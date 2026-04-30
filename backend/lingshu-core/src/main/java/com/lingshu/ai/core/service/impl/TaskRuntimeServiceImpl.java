package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.task.TaskApprovalDecisionRequest;
import com.lingshu.ai.core.dto.task.TaskRunView;
import com.lingshu.ai.core.dto.task.TaskStartRequest;
import com.lingshu.ai.core.service.TaskEventStreamService;
import com.lingshu.ai.core.service.TaskExecutionEngine;
import com.lingshu.ai.core.service.TaskPermissionService;
import com.lingshu.ai.core.service.TaskRuntimeService;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TaskRuntimeServiceImpl implements TaskRuntimeService {
    private static final Pattern GIT_CMD = Pattern.compile("(?i).*(?:^|\\s)(git)(?:\\s|$).*");
    private static final Pattern JS_CMD = Pattern.compile("(?i).*(?:^|\\s)(npm|pnpm|yarn|node|npx)(?:\\s|$).*");
    private static final Pattern JAVA_CMD = Pattern.compile("(?i).*(?:^|\\s)(mvn|gradle|java|javac|./gradlew)(?:\\s|$).*");
    private static final Pattern PYTHON_CMD = Pattern.compile("(?i).*(?:^|\\s)(python|pip|pytest|uv)(?:\\s|$).*");
    private static final Pattern SHELL_CMD = Pattern.compile("(?i).*(?:^|\\s)(powershell|pwsh|cmd|bash|sh)(?:\\s|$).*");


    private final TaskRunRepository taskRunRepository;
    private final TaskEventStreamService taskEventStreamService;
    private final TaskPermissionService taskPermissionService;
    private final TaskExecutionEngine taskExecutionEngine;
    private final TaskSessionRouterService taskSessionRouterService;

    public TaskRuntimeServiceImpl(TaskRunRepository taskRunRepository,
                                  TaskEventStreamService taskEventStreamService,
                                  TaskPermissionService taskPermissionService,
                                  TaskExecutionEngine taskExecutionEngine) {
        this(taskRunRepository, taskEventStreamService, taskPermissionService, taskExecutionEngine, new TaskSessionRouterService());
    }

    @Autowired
    public TaskRuntimeServiceImpl(TaskRunRepository taskRunRepository,
                                  TaskEventStreamService taskEventStreamService,
                                  TaskPermissionService taskPermissionService,
                                  TaskExecutionEngine taskExecutionEngine,
                                  TaskSessionRouterService taskSessionRouterService) {
        this.taskRunRepository = taskRunRepository;
        this.taskEventStreamService = taskEventStreamService;
        this.taskPermissionService = taskPermissionService;
        this.taskExecutionEngine = taskExecutionEngine;
        this.taskSessionRouterService = taskSessionRouterService;
    }

    @Override
    @Transactional("transactionManager")
    public TaskRunView start(TaskStartRequest request) {
        String requestText = requireText(request.requestText(), "requestText");
        TaskSessionRouterService.TaskRouteDecision routeDecision = taskSessionRouterService.decide(requestText);
        if (!routeDecision.taskRequest()) {
            throw new IllegalArgumentException("requestText is not a task request: " + routeDecision.reason());
        }
        String commandCategory = resolveCommandCategory(request.commandCategory(), requestText);

        TaskPermissionService.TaskPermissionDecision decision = taskPermissionService.evaluate(
                request.userId(),
                request.workspacePath(),
                commandCategory
        );
        LocalDateTime now = LocalDateTime.now();
        boolean requiresApproval = decision.requiresWorkspaceApproval() || decision.requiresCommandApproval();
        TaskRunState state = requiresApproval ? TaskRunState.WAITING_APPROVAL : TaskRunState.RUNNING;

        TaskRun run = TaskRun.builder()
                .userId(requireText(request.userId(), "userId"))
                .chatSessionId(request.chatSessionId())
                .title(buildTitle(requestText))
                .workspacePath(requireText(request.workspacePath(), "workspacePath"))
                .commandCategory(commandCategory)
                .requestText(requestText)
                .state(state)
                .createdAt(now)
                .updatedAt(now)
                .build();

        TaskRun saved = taskRunRepository.save(run);
        taskEventStreamService.appendEvent(saved, TaskEventType.TASK_CREATED, Map.of(
                "requestText", saved.getRequestText(),
                "workspacePath", saved.getWorkspacePath(),
                "commandCategory", saved.getCommandCategory()
        ));

        if (requiresApproval) {
            taskEventStreamService.appendEvent(saved, TaskEventType.APPROVAL_REQUIRED, Map.of(
                    "workspacePath", saved.getWorkspacePath(),
                    "commandCategory", saved.getCommandCategory(),
                    "requiresWorkspaceApproval", decision.requiresWorkspaceApproval(),
                    "requiresCommandApproval", decision.requiresCommandApproval()
            ));
        } else {
            runAfterCommitOrNow(() -> taskExecutionEngine.schedule(saved));
        }

        return toView(saved);
    }

    @Override
    public TaskRunView get(Long taskRunId, String userId) {
        TaskRun run = taskRunRepository.findByIdAndUserId(taskRunId, requireText(userId, "userId"))
                .orElseThrow(() -> new IllegalArgumentException("Task run not found: " + taskRunId));
        return toView(run);
    }

    @Override
    public List<TaskRunView> listBySession(Long chatSessionId, String userId) {
        if (chatSessionId == null) {
            throw new IllegalArgumentException("chatSessionId must not be null");
        }
        String normalizedUserId = requireText(userId, "userId");
        return taskRunRepository.findByUserIdAndChatSessionIdOrderByCreatedAtAscIdAsc(normalizedUserId, chatSessionId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional("transactionManager")
    public TaskRunView approve(Long taskRunId, String userId, TaskApprovalDecisionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        TaskRun run = getOwnedRun(taskRunId, userId);
        ensureState(run, TaskRunState.WAITING_APPROVAL, "approve");

        TaskPermissionService.TaskPermissionDecision decision = taskPermissionService.evaluate(
                run.getUserId(),
                run.getWorkspacePath(),
                run.getCommandCategory()
        );
        boolean workspaceRequired = decision.requiresWorkspaceApproval() && !Boolean.TRUE.equals(request.grantWorkspace());
        boolean commandRequired = decision.requiresCommandApproval() && !Boolean.TRUE.equals(request.grantCommandCategory());

        if (decision.requiresWorkspaceApproval() && Boolean.TRUE.equals(request.grantWorkspace())) {
            taskPermissionService.grantWorkspace(run.getUserId(), run.getWorkspacePath());
        }
        if (decision.requiresCommandApproval() && Boolean.TRUE.equals(request.grantCommandCategory())) {
            taskPermissionService.grantCommandCategory(run.getUserId(), run.getCommandCategory());
        }

        LocalDateTime now = LocalDateTime.now();
        if (workspaceRequired || commandRequired) {
            run.setState(TaskRunState.STOPPED);
            run.setUpdatedAt(now);
            taskRunRepository.save(run);
            taskEventStreamService.appendEvent(run, TaskEventType.APPROVAL_REJECTED, Map.of(
                    "grantWorkspace", Boolean.TRUE.equals(request.grantWorkspace()),
                    "grantCommandCategory", Boolean.TRUE.equals(request.grantCommandCategory())
            ));
            return toView(run);
        }

        run.setState(TaskRunState.RUNNING);
        run.setUpdatedAt(now);
        taskRunRepository.save(run);
        Map<String, Object> approvalGrantedPayload = new LinkedHashMap<>();
        approvalGrantedPayload.put("grantWorkspace", request.grantWorkspace());
        approvalGrantedPayload.put("grantCommandCategory", request.grantCommandCategory());
        taskEventStreamService.appendEvent(run, TaskEventType.APPROVAL_GRANTED, approvalGrantedPayload);
        taskEventStreamService.appendEvent(run, TaskEventType.TASK_RESUMED, Map.of(
                "reason", "approval_granted"
        ));
        runAfterCommitOrNow(() -> taskExecutionEngine.schedule(run));
        return toView(run);
    }

    @Override
    @Transactional("transactionManager")
    public TaskRunView pause(Long taskRunId, String userId) {
        TaskRun run = getOwnedRun(taskRunId, userId);
        ensureState(run, TaskRunState.RUNNING, "pause");
        run.setState(TaskRunState.PAUSED);
        run.setUpdatedAt(LocalDateTime.now());
        taskRunRepository.save(run);
        taskEventStreamService.appendEvent(run, TaskEventType.TASK_PAUSED, Map.of());
        runAfterCommitOrNow(() -> taskExecutionEngine.pause(run));
        return toView(run);
    }

    @Override
    @Transactional("transactionManager")
    public TaskRunView resume(Long taskRunId, String userId) {
        TaskRun run = getOwnedRun(taskRunId, userId);
        ensureState(run, TaskRunState.PAUSED, "resume");
        run.setState(TaskRunState.RUNNING);
        run.setUpdatedAt(LocalDateTime.now());
        taskRunRepository.save(run);
        taskEventStreamService.appendEvent(run, TaskEventType.TASK_RESUMED, Map.of(
                "reason", "manual_resume"
        ));
        runAfterCommitOrNow(() -> taskExecutionEngine.resume(run));
        return toView(run);
    }

    @Override
    @Transactional("transactionManager")
    public TaskRunView stop(Long taskRunId, String userId) {
        TaskRun run = getOwnedRun(taskRunId, userId);
        if (run.getState() != TaskRunState.PENDING
                && run.getState() != TaskRunState.RUNNING
                && run.getState() != TaskRunState.WAITING_APPROVAL
                && run.getState() != TaskRunState.PAUSED) {
            throw new IllegalStateException("Cannot stop task run in state " + run.getState());
        }
        run.setState(TaskRunState.STOPPED);
        run.setUpdatedAt(LocalDateTime.now());
        taskRunRepository.save(run);
        taskEventStreamService.appendEvent(run, TaskEventType.TASK_STOPPED, Map.of());
        runAfterCommitOrNow(() -> taskExecutionEngine.stop(run));
        return toView(run);
    }

    private TaskRunView toView(TaskRun run) {
        return new TaskRunView(
                run.getId(),
                run.getUserId(),
                run.getChatSessionId(),
                run.getTitle(),
                run.getWorkspacePath(),
                run.getCommandCategory(),
                run.getState().name(),
                run.getRuntimeSnapshotJson(),
                taskEventStreamService.getEvents(run.getId())
        );
    }

    private String buildTitle(String requestText) {
        String normalized = requireText(requestText, "requestText");
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
    }

    private TaskRun getOwnedRun(Long taskRunId, String userId) {
        return taskRunRepository.findByIdAndUserId(taskRunId, requireText(userId, "userId"))
                .orElseThrow(() -> new IllegalArgumentException("Task run not found: " + taskRunId));
    }

    private void ensureState(TaskRun run, TaskRunState expectedState, String operation) {
        if (run.getState() != expectedState) {
            throw new IllegalStateException("Cannot " + operation + " task run in state " + run.getState());
        }
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String resolveCommandCategory(String requestedCategory, String requestText) {
        String normalizedRequested = requestedCategory == null ? "" : requestedCategory.trim().toLowerCase(Locale.ROOT);
        if (!normalizedRequested.isBlank() && !"auto".equals(normalizedRequested)) {
            return normalizedRequested;
        }
        String normalizedText = requestText == null ? "" : requestText.toLowerCase(Locale.ROOT);
        if (GIT_CMD.matcher(normalizedText).matches()) return "git";
        if (JS_CMD.matcher(normalizedText).matches()) return "node";
        if (JAVA_CMD.matcher(normalizedText).matches()) return "java";
        if (PYTHON_CMD.matcher(normalizedText).matches()) return "python";
        if (SHELL_CMD.matcher(normalizedText).matches()) return "shell";
        return "general";
    }
}
