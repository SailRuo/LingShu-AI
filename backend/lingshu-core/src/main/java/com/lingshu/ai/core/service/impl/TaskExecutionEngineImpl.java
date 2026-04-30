package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.core.service.TaskEventStreamService;
import com.lingshu.ai.core.service.TaskExecutionEngine;
import com.lingshu.ai.core.service.TaskPlanner;
import com.lingshu.ai.core.service.McpService;
import com.lingshu.ai.core.tool.BuiltinWorkspaceToolProvider;
import com.lingshu.ai.core.tool.SafeMcpToolProvider;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class TaskExecutionEngineImpl implements TaskExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionEngineImpl.class);

    private final Executor taskExecutor;
    private final TaskEventStreamService taskEventStreamService;
    private final TaskRunRepository taskRunRepository;
    private final TaskPlanner taskPlanner;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;
    private final McpService mcpService;
    private final ConcurrentHashMap<Long, TaskHandle> activeRuns = new ConcurrentHashMap<>();

    public TaskExecutionEngineImpl(@Qualifier("taskExecutor") Executor taskExecutor,
                                   TaskEventStreamService taskEventStreamService,
                                   TaskRunRepository taskRunRepository,
                                   TaskPlanner taskPlanner,
                                   @Qualifier("chatLanguageModel") ChatModel chatModel,
                                   McpService mcpService) {
        this.taskExecutor = taskExecutor;
        this.taskEventStreamService = taskEventStreamService;
        this.taskRunRepository = taskRunRepository;
        this.taskPlanner = taskPlanner;
        this.chatModel = chatModel;
        this.mcpService = mcpService;
        this.objectMapper = new ObjectMapper();
    }

    // ── public contract ────────────────────────────────────────────────

    @Override
    public void schedule(TaskRun run) {
        TaskHandle handle = new TaskHandle();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                executeAgenticWorkflow(run, handle);
            } catch (Exception e) {
                log.error("Task execution engine failed for run {}: {}", run.getId(), e.getMessage(), e);
                emitLog(run, "step", "execute_workflow", "message", "Execution engine error: " + e.getMessage());
                transitionState(run, TaskRunState.FAILED, "Execution engine error: " + e.getMessage());
            }
        }, taskExecutor);
        handle.setFuture(future);
        activeRuns.put(run.getId(), handle);
        future.whenComplete((unused, throwable) -> activeRuns.remove(run.getId(), handle));
    }

    @Override
    public void pause(TaskRun run) {
        cancel(run.getId());
    }

    @Override
    public void resume(TaskRun run) {
        schedule(run);
    }

    @Override
    public void stop(TaskRun run) {
        cancel(run.getId());
    }

    @Override
    public void restorePendingTasks() {
        taskRunRepository.findByState(TaskRunState.RUNNING).forEach(this::schedule);
    }

    // ── agentic workflow ────────────────────────────────────────────

    interface TaskAgent {
        @dev.langchain4j.service.SystemMessage("""
            You are an expert developer agent executing a task in a local workspace.
            You have access to tools to read files, write files, and execute commands.
            
            Follow these rules:
            1. ALWAYS start by exploring the workspace to understand the context.
            2. If you need to modify code, read the existing code first.
            3. After making changes, run tests or build commands to verify your changes if applicable.
            4. If a command fails, analyze the error and try to fix it.
            5. When you are completely done with the task, summarize what you did.
            """)
        String executeTask(String userRequest);
    }

    private void executeAgenticWorkflow(TaskRun run, TaskHandle handle) {
        Path workspaceRoot = Paths.get(run.getWorkspacePath()).toAbsolutePath().normalize();
        
        // 1. Generate initial plan (for context and logging)
        TaskPlan plan = taskPlanner.plan(run);
        storePlanInSnapshot(run, plan);
        emitLog(run, "step", "execution_plan", "message", "Starting agentic execution for: " + plan.summary());

        // 2. Setup ToolProviders
        List<ToolProvider> toolProviders = new ArrayList<>();
        
        // Add Builtin Workspace Tools (with all permissions enabled for task mode)
        Set<String> enabledBuiltinTools = Set.of("execute_command", "read_file", "write_file");
        toolProviders.add(new BuiltinWorkspaceToolProvider(workspaceRoot, enabledBuiltinTools));
        
        // Add MCP Tools
        var mcpClients = mcpService.getActiveClients();
        if (!mcpClients.isEmpty()) {
            toolProviders.add(new SafeMcpToolProvider(mcpClients, null, () -> run.getRequestText()));
        }

        // 3. Setup ChatMemory
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(50);
        
        // Restore memory from snapshot if resuming
        if (run.getRuntimeSnapshotJson() != null && run.getRuntimeSnapshotJson().contains("\"messages\"")) {
            try {
                restoreAgentState(run, chatMemory);
                log.info("Resuming task {} from snapshot", run.getId());
                emitLog(run, "step", "agent_resume", "message", "Resuming agent execution from previous state");
            } catch (Exception e) {
                log.warn("Failed to restore chat memory for run {}", run.getId(), e);
            }
        }

        // 4. Build Agent
        TaskAgent agent = AiServices.builder(TaskAgent.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .toolProviders(toolProviders)
                .build();

        // 5. Execute
        emitStepStart(run, "agent_execution");
        try {
            // Periodically save state (simulated here by saving after execution)
            // In a fully reactive setup, you'd hook into the TokenStream or ToolExecution events
            String finalResult = agent.executeTask(run.getRequestText());
            
            if (handle.isCancelled()) {
                saveAgentState(run, chatMemory);
                return;
            }
            
            emitLog(run, "step", "agent_execution", "message", "Agent finished: " + finalResult);
            emitStepCompleted(run, "agent_execution", "result", finalResult);
            
            transitionState(run, TaskRunState.COMPLETED, finalResult);
        } catch (Exception e) {
            if (handle.isCancelled()) {
                saveAgentState(run, chatMemory);
                return;
            }
            log.error("Agent execution failed", e);
            emitLog(run, "step", "agent_execution", "message", "Agent failed: " + e.getMessage());
            transitionState(run, TaskRunState.FAILED, e.getMessage());
        }
    }

    private void saveAgentState(TaskRun run, ChatMemory chatMemory) {
        try {
            List<ChatMessage> messages = chatMemory.messages();
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("messages", messages.stream().map(this::serializeMessage).toList());
            
            String stateJson = objectMapper.writeValueAsString(state);
            run.setRuntimeSnapshotJson(stateJson);
            taskRunRepository.save(run);
            log.info("Saved agent state for run {}", run.getId());
        } catch (Exception e) {
            log.error("Failed to save agent state for run {}", run.getId(), e);
        }
    }

    private void restoreAgentState(TaskRun run, ChatMemory chatMemory) throws JsonProcessingException {
        Map<String, Object> state = objectMapper.readValue(run.getRuntimeSnapshotJson(), new TypeReference<>() {
        });
        Object messagesNode = state.get("messages");
        if (!(messagesNode instanceof List<?> rawMessages) || rawMessages.isEmpty()) {
            return;
        }

        int restored = 0;
        int skipped = 0;
        for (Object rawMessage : rawMessages) {
            try {
                ChatMessage message = deserializeMessage(rawMessage);
                if (message == null) {
                    skipped++;
                    continue;
                }
                chatMemory.add(message);
                restored++;
            } catch (Exception e) {
                skipped++;
                log.debug("Skip invalid snapshot message for run {}: {}", run.getId(), e.getMessage());
            }
        }
        log.info("Restored {} messages (skipped {}) for run {}", restored, skipped, run.getId());
    }

    private Map<String, Object> serializeMessage(ChatMessage message) {
        Map<String, Object> data = new HashMap<>();
        if (message instanceof SystemMessage systemMessage) {
            data.put("type", "SYSTEM");
            data.put("text", systemMessage.text());
            return data;
        }
        if (message instanceof UserMessage userMessage) {
            data.put("type", "USER");
            data.put("text", extractText(userMessage.contents()));
            return data;
        }
        if (message instanceof AiMessage aiMessage) {
            data.put("type", "AI");
            data.put("text", aiMessage.text());
            if (aiMessage.hasToolExecutionRequests()) {
                data.put("toolRequests", aiMessage.toolExecutionRequests().stream()
                        .map(req -> Map.of(
                                "id", req.id() == null ? "" : req.id(),
                                "name", req.name() == null ? "" : req.name(),
                                "arguments", req.arguments() == null ? "" : req.arguments()
                        ))
                        .toList());
            }
            return data;
        }
        if (message instanceof ToolExecutionResultMessage toolResult) {
            data.put("type", "TOOL_EXECUTION_RESULT");
            data.put("id", toolResult.id());
            data.put("toolName", toolResult.toolName());
            data.put("text", toolResult.text());
            data.put("isError", toolResult.isError());
            return data;
        }
        data.put("type", "UNKNOWN");
        data.put("text", message.toString());
        return data;
    }

    private ChatMessage deserializeMessage(Object rawMessage) {
        // Backward compatibility: older snapshots stored only message.toString().
        if (rawMessage instanceof String legacyText) {
            if (legacyText.isBlank()) {
                return null;
            }
            return UserMessage.from(legacyText);
        }
        if (!(rawMessage instanceof Map<?, ?> rawMap)) {
            return null;
        }

        String type = safeString(rawMap.get("type"));
        return switch (type) {
            case "SYSTEM" -> SystemMessage.from(safeString(rawMap.get("text")));
            case "USER" -> UserMessage.from(safeString(rawMap.get("text")));
            case "AI" -> deserializeAiMessage(rawMap);
            case "TOOL_EXECUTION_RESULT" -> ToolExecutionResultMessage.builder()
                    .id(safeString(rawMap.get("id")))
                    .toolName(safeString(rawMap.get("toolName")))
                    .text(safeString(rawMap.get("text")))
                    .isError(Boolean.TRUE.equals(rawMap.get("isError")))
                    .build();
            default -> null;
        };
    }

    private ChatMessage deserializeAiMessage(Map<?, ?> rawMap) {
        Object toolRequestsNode = rawMap.get("toolRequests");
        if (toolRequestsNode instanceof List<?> rawRequests && !rawRequests.isEmpty()) {
            List<ToolExecutionRequest> requests = new ArrayList<>();
            for (Object requestNode : rawRequests) {
                if (!(requestNode instanceof Map<?, ?> requestMap)) {
                    continue;
                }
                requests.add(ToolExecutionRequest.builder()
                        .id(safeString(requestMap.get("id")))
                        .name(safeString(requestMap.get("name")))
                        .arguments(safeString(requestMap.get("arguments")))
                        .build());
            }
            if (!requests.isEmpty()) {
                return AiMessage.from(requests);
            }
        }
        return AiMessage.from(safeString(rawMap.get("text")));
    }

    private String extractText(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        return contents.stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ── plan persistence ──────────────────────────────────────────────

    private void storePlanInSnapshot(TaskRun run, TaskPlan plan) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("intentLabel", plan.intentLabel());
            snapshot.put("summary", plan.summary());
            snapshot.put("steps", plan.steps().stream()
                    .map(s -> Map.of(
                            "type", s.type().name(),
                            "description", s.description(),
                            "params", s.params()))
                    .toList());
            run.setRuntimeSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize plan snapshot for run {}", run.getId(), e);
        }
    }

    // ── state transition ───────────────────────────────────────────────

    private void transitionState(TaskRun run, TaskRunState targetState, String summary) {
        TaskRun managed = taskRunRepository.findById(run.getId()).orElse(null);
        if (managed == null) {
            log.warn("Cannot transition state for run {}: not found in database", run.getId());
            return;
        }

        managed.setState(targetState);
        managed.setUpdatedAt(LocalDateTime.now());
        if (targetState == TaskRunState.COMPLETED || targetState == TaskRunState.FAILED
                || targetState == TaskRunState.STOPPED) {
            managed.setCompletedAt(LocalDateTime.now());
        }
        taskRunRepository.save(managed);

        TaskEventType eventType = targetState == TaskRunState.COMPLETED
                ? TaskEventType.TASK_COMPLETED
                : TaskEventType.TASK_FAILED;
        taskEventStreamService.appendEvent(run, eventType, Map.of(
                "summary", summary != null ? summary : ""
        ));
    }

    // ── event helpers ──────────────────────────────────────────────────

    private void emitStepStart(TaskRun run, String step) {
        taskEventStreamService.appendEvent(run, TaskEventType.STEP_STARTED,
                Map.of("step", step));
    }

    private void emitStepCompleted(TaskRun run, String step, String... kvPairs) {
        Map<String, Object> payload = buildPayload("step", step, kvPairs);
        taskEventStreamService.appendEvent(run, TaskEventType.STEP_COMPLETED, payload);
    }

    private void emitLog(TaskRun run, String... kvPairs) {
        Map<String, Object> payload = buildPayload(null, null, kvPairs);
        taskEventStreamService.appendEvent(run, TaskEventType.LOG, payload);
    }

    private Map<String, Object> buildPayload(String extraKey, String extraValue, String... kvPairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (extraKey != null) {
            payload.put(extraKey, extraValue);
        }
        if (kvPairs != null) {
            for (int i = 0; i + 1 < kvPairs.length; i += 2) {
                payload.put(kvPairs[i], kvPairs[i + 1]);
            }
        }
        return payload;
    }

    // ── lifecycle ──────────────────────────────────────────────────────

    private void cancel(Long taskRunId) {
        TaskHandle handle = activeRuns.remove(taskRunId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private static final class TaskHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private CompletableFuture<Void> future;

        void setFuture(CompletableFuture<Void> future) {
            this.future = future;
        }

        boolean isCancelled() {
            return cancelled.get() || Thread.currentThread().isInterrupted();
        }

        void cancel() {
            cancelled.set(true);
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
