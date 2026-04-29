package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.dto.task.TaskApprovalDecisionRequest;
import com.lingshu.ai.core.dto.task.TaskRunView;
import com.lingshu.ai.core.dto.task.TaskStartRequest;
import com.lingshu.ai.core.service.TaskExecutionEngine;
import com.lingshu.ai.core.service.TaskPermissionService;
import com.lingshu.ai.infrastructure.entity.PermissionGrant;
import com.lingshu.ai.infrastructure.entity.TaskEvent;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskEventRepository;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskRuntimeServiceImplTest {

    @Test
    void start_shouldReturnWaitingApprovalAndAppendCreatedAndApprovalRequiredEvents() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskEventStreamServiceImpl taskEventStreamService = new TaskEventStreamServiceImpl(
                taskEventRepository,
                taskRunRepository,
                new ObjectMapper()
        );
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                taskEventStreamService,
                taskPermissionService,
                taskExecutionEngine
        );

        TaskStartRequest request = new TaskStartRequest(
                "web:test-user",
                12L,
                "Please fix the demo tests under D:\\work\\demo",
                "D:\\work\\demo",
                "npm"
        );

        when(taskPermissionService.evaluate("web:test-user", "D:\\work\\demo", "npm"))
                .thenReturn(new TaskPermissionService.TaskPermissionDecision(true, true));
        when(taskRunRepository.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId(101L);
            return run;
        });
        when(taskRunRepository.findByIdForUpdate(101L)).thenAnswer(invocation ->
                Optional.of(TaskRun.builder().id(101L).build())
        );

        List<TaskEvent> persistedEvents = new ArrayList<>();
        AtomicLong eventIds = new AtomicLong(200L);
        when(taskEventRepository.findByTaskRunIdOrderBySequenceNoAsc(101L)).thenAnswer(invocation -> List.copyOf(persistedEvents));
        when(taskEventRepository.findTopByTaskRunIdOrderBySequenceNoDesc(101L)).thenAnswer(invocation ->
                persistedEvents.isEmpty() ? Optional.empty() : Optional.of(persistedEvents.getLast())
        );
        when(taskEventRepository.save(any(TaskEvent.class))).thenAnswer(invocation -> {
            TaskEvent event = invocation.getArgument(0);
            event.setId(eventIds.incrementAndGet());
            persistedEvents.add(event);
            return event;
        });

        TaskRunView view = service.start(request);

        assertEquals(TaskRunState.WAITING_APPROVAL.name(), view.state());
        assertEquals("D:\\work\\demo", view.workspacePath());
        assertEquals(2, view.events().size());
        assertEquals("TASK_CREATED", view.events().get(0).eventType());
        assertEquals("APPROVAL_REQUIRED", view.events().get(1).eventType());
        assertEquals(1, view.events().get(0).sequenceNo());
        assertEquals(2, view.events().get(1).sequenceNo());
        assertTrue(view.events().get(0).payloadJson().contains("\"commandCategory\":\"npm\""));
        assertTrue(view.events().get(1).payloadJson().contains("\"requiresWorkspaceApproval\":true"));
        assertTrue(view.events().get(1).payloadJson().contains("\"requiresCommandApproval\":true"));
    }

    @Test
    void start_shouldReturnRunningAndScheduleWhenGrantsAlreadyExist() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskEventStreamServiceImpl taskEventStreamService = new TaskEventStreamServiceImpl(
                taskEventRepository,
                taskRunRepository,
                new ObjectMapper()
        );
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                taskEventStreamService,
                taskPermissionService,
                taskExecutionEngine
        );

        TaskStartRequest request = new TaskStartRequest(
                "web:test-user",
                22L,
                "Fix the test failures",
                "D:\\work\\demo",
                "npm"
        );

        when(taskPermissionService.evaluate("web:test-user", "D:\\work\\demo", "npm"))
                .thenReturn(new TaskPermissionService.TaskPermissionDecision(false, false));
        when(taskRunRepository.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId(102L);
            return run;
        });
        when(taskRunRepository.findByIdForUpdate(102L)).thenAnswer(invocation ->
                Optional.of(TaskRun.builder().id(102L).build())
        );

        List<TaskEvent> persistedEvents = new ArrayList<>();
        AtomicLong eventIds = new AtomicLong(300L);
        when(taskEventRepository.findByTaskRunIdOrderBySequenceNoAsc(102L)).thenAnswer(invocation -> List.copyOf(persistedEvents));
        when(taskEventRepository.findTopByTaskRunIdOrderBySequenceNoDesc(102L)).thenAnswer(invocation ->
                persistedEvents.isEmpty() ? Optional.empty() : Optional.of(persistedEvents.getLast())
        );
        when(taskEventRepository.save(any(TaskEvent.class))).thenAnswer(invocation -> {
            TaskEvent event = invocation.getArgument(0);
            event.setId(eventIds.incrementAndGet());
            persistedEvents.add(event);
            return event;
        });

        TaskRunView view = service.start(request);

        assertEquals(TaskRunState.RUNNING.name(), view.state());
        assertEquals(1, view.events().size());
        assertEquals("TASK_CREATED", view.events().getFirst().eventType());
        assertFalse(view.events().stream().anyMatch(event -> "APPROVAL_REQUIRED".equals(event.eventType())));
        verify(taskExecutionEngine).schedule(org.mockito.ArgumentMatchers.argThat(run ->
                Long.valueOf(102L).equals(run.getId()) && run.getState() == TaskRunState.RUNNING
        ));
    }

    @Test
    void approve_shouldTransitionToRunningAndGrantRequiredPermissions() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        TaskRun run = baseRun(201L, TaskRunState.WAITING_APPROVAL);
        List<TaskEvent> persistedEvents = new ArrayList<>();
        stubExistingRun(taskRunRepository, taskEventRepository, run, persistedEvents);
        when(taskPermissionService.evaluate("web:test-user", "D:\\work\\demo", "npm"))
                .thenReturn(new TaskPermissionService.TaskPermissionDecision(true, true));
        when(taskPermissionService.grantWorkspace("web:test-user", "D:\\work\\demo"))
                .thenReturn(PermissionGrant.builder().id(1L).build());
        when(taskPermissionService.grantCommandCategory("web:test-user", "npm"))
                .thenReturn(PermissionGrant.builder().id(2L).build());

        TaskRunView view = service.approve(
                201L,
                "web:test-user",
                new TaskApprovalDecisionRequest(true, true)
        );

        assertEquals(TaskRunState.RUNNING.name(), view.state());
        assertEquals(TaskRunState.RUNNING, run.getState());
        assertEquals(2, view.events().size());
        assertEquals("APPROVAL_GRANTED", view.events().get(0).eventType());
        assertEquals("TASK_RESUMED", view.events().get(1).eventType());
        verify(taskPermissionService).grantWorkspace("web:test-user", "D:\\work\\demo");
        verify(taskPermissionService).grantCommandCategory("web:test-user", "npm");
        verify(taskExecutionEngine).schedule(run);
    }

    @Test
    void approve_shouldStopRunAndAppendRejectedEventWhenRequiredApprovalMissing() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        TaskRun run = baseRun(202L, TaskRunState.WAITING_APPROVAL);
        List<TaskEvent> persistedEvents = new ArrayList<>();
        stubExistingRun(taskRunRepository, taskEventRepository, run, persistedEvents);
        when(taskPermissionService.evaluate("web:test-user", "D:\\work\\demo", "npm"))
                .thenReturn(new TaskPermissionService.TaskPermissionDecision(true, true));

        TaskRunView view = service.approve(
                202L,
                "web:test-user",
                new TaskApprovalDecisionRequest(true, null)
        );

        assertEquals(TaskRunState.STOPPED.name(), view.state());
        assertEquals(TaskRunState.STOPPED, run.getState());
        assertEquals(1, view.events().size());
        assertEquals("APPROVAL_REJECTED", view.events().getFirst().eventType());
        assertTrue(view.events().getFirst().payloadJson().contains("\"grantCommandCategory\":false"));
        verify(taskPermissionService).grantWorkspace("web:test-user", "D:\\work\\demo");
        verify(taskPermissionService, never()).grantCommandCategory(any(), any());
        verify(taskExecutionEngine, never()).schedule(any());
    }

    @Test
    void approve_shouldAllowNullOptionalApprovalFlagsWhenPermissionNotRequired() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        TaskRun run = baseRun(203L, TaskRunState.WAITING_APPROVAL);
        run.setCommandCategory("git");
        List<TaskEvent> persistedEvents = new ArrayList<>();
        stubExistingRun(taskRunRepository, taskEventRepository, run, persistedEvents);
        when(taskPermissionService.evaluate("web:test-user", "D:\\work\\demo", "git"))
                .thenReturn(new TaskPermissionService.TaskPermissionDecision(true, false));
        when(taskPermissionService.grantWorkspace("web:test-user", "D:\\work\\demo"))
                .thenReturn(PermissionGrant.builder().id(3L).build());

        TaskRunView view = service.approve(
                203L,
                "web:test-user",
                new TaskApprovalDecisionRequest(true, null)
        );

        assertEquals(TaskRunState.RUNNING.name(), view.state());
        verify(taskPermissionService).grantWorkspace("web:test-user", "D:\\work\\demo");
        verify(taskPermissionService, never()).grantCommandCategory(any(), any());
        verify(taskExecutionEngine).schedule(run);
    }

    @Test
    void lifecycleOperations_shouldPauseResumeAndStopSupportedStates() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        TaskRun runningRun = baseRun(204L, TaskRunState.RUNNING);
        List<TaskEvent> runningEvents = new ArrayList<>();
        stubExistingRun(taskRunRepository, taskEventRepository, runningRun, runningEvents);

        TaskRunView paused = service.pause(204L, "web:test-user");
        assertEquals(TaskRunState.PAUSED.name(), paused.state());
        assertEquals("TASK_PAUSED", paused.events().getFirst().eventType());
        verify(taskExecutionEngine).pause(runningRun);

        TaskRunView resumed = service.resume(204L, "web:test-user");
        assertEquals(TaskRunState.RUNNING.name(), resumed.state());
        assertEquals("TASK_RESUMED", resumed.events().getLast().eventType());
        verify(taskExecutionEngine).resume(runningRun);

        TaskRunView stoppedFromRunning = service.stop(204L, "web:test-user");
        assertEquals(TaskRunState.STOPPED.name(), stoppedFromRunning.state());
        assertEquals("TASK_STOPPED", stoppedFromRunning.events().getLast().eventType());
        verify(taskExecutionEngine).stop(runningRun);

        TaskRun pendingRun = baseRun(205L, TaskRunState.PENDING);
        List<TaskEvent> pendingEvents = new ArrayList<>();
        stubExistingRun(taskRunRepository, taskEventRepository, pendingRun, pendingEvents);
        TaskRunView stoppedFromPending = service.stop(205L, "web:test-user");
        assertEquals(TaskRunState.STOPPED.name(), stoppedFromPending.state());

        TaskRun waitingRun = baseRun(206L, TaskRunState.WAITING_APPROVAL);
        List<TaskEvent> waitingEvents = new ArrayList<>();
        stubExistingRun(taskRunRepository, taskEventRepository, waitingRun, waitingEvents);
        TaskRunView stoppedFromWaiting = service.stop(206L, "web:test-user");
        assertEquals(TaskRunState.STOPPED.name(), stoppedFromWaiting.state());
    }

    @Test
    void lifecycleOperations_shouldRejectIllegalStateTransitions() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        TaskRun pendingRun = baseRun(207L, TaskRunState.PENDING);
        stubExistingRun(taskRunRepository, taskEventRepository, pendingRun, new ArrayList<>());
        IllegalStateException pauseError = assertThrows(IllegalStateException.class, () -> service.pause(207L, "web:test-user"));
        assertTrue(pauseError.getMessage().contains("Cannot pause"));

        TaskRun runningRun = baseRun(208L, TaskRunState.RUNNING);
        stubExistingRun(taskRunRepository, taskEventRepository, runningRun, new ArrayList<>());
        IllegalStateException resumeError = assertThrows(IllegalStateException.class, () -> service.resume(208L, "web:test-user"));
        assertTrue(resumeError.getMessage().contains("Cannot resume"));

        TaskRun stoppedRun = baseRun(209L, TaskRunState.STOPPED);
        stubExistingRun(taskRunRepository, taskEventRepository, stoppedRun, new ArrayList<>());
        IllegalStateException stopError = assertThrows(IllegalStateException.class, () -> service.stop(209L, "web:test-user"));
        assertTrue(stopError.getMessage().contains("Cannot stop"));

        TaskRun wrongApprovalStateRun = baseRun(210L, TaskRunState.PENDING);
        stubExistingRun(taskRunRepository, taskEventRepository, wrongApprovalStateRun, new ArrayList<>());
        IllegalStateException approveError = assertThrows(IllegalStateException.class, () -> service.approve(
                210L,
                "web:test-user",
                new TaskApprovalDecisionRequest(true, true)
        ));
        assertTrue(approveError.getMessage().contains("Cannot approve"));

        IllegalArgumentException nullRequestError = assertThrows(IllegalArgumentException.class, () -> service.approve(
                210L,
                "web:test-user",
                null
        ));
        assertEquals("request must not be null", nullRequestError.getMessage());
    }

    @Test
    void get_shouldMapOwnedRunWithOrderedEvents() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        TaskRun run = TaskRun.builder()
                .id(103L)
                .userId("web:test-user")
                .chatSessionId(33L)
                .title("Fix the tests")
                .workspacePath("D:\\work\\demo")
                .commandCategory("npm")
                .requestText("Fix the tests")
                .runtimeSnapshotJson("{\"step\":\"created\"}")
                .state(TaskRunState.PENDING)
                .createdAt(LocalDateTime.of(2026, 4, 29, 22, 40))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 22, 41))
                .build();
        List<TaskEvent> events = List.of(
                TaskEvent.builder()
                        .id(401L)
                        .taskRun(run)
                        .sequenceNo(1)
                        .eventType(TaskEventType.TASK_CREATED)
                        .payloadJson("{\"requestText\":\"Fix the tests\"}")
                        .createdAt(LocalDateTime.of(2026, 4, 29, 22, 40, 30))
                        .build()
        );

        when(taskRunRepository.findByIdAndUserId(103L, "web:test-user")).thenReturn(java.util.Optional.of(run));
        when(taskEventRepository.findByTaskRunIdOrderBySequenceNoAsc(103L)).thenReturn(events);

        TaskRunView view = service.get(103L, "web:test-user");

        assertEquals(103L, view.id());
        assertEquals(TaskRunState.PENDING.name(), view.state());
        assertEquals(1, view.events().size());
        assertEquals("TASK_CREATED", view.events().getFirst().eventType());
    }

    @Test
    void start_shouldTrimInputsAndTruncateLongTitle() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        String longRequest = "  Please fix the demo tests and stabilize the project build pipeline before release tonight.  ";
        when(taskPermissionService.evaluate("  web:test-user  ", "  D:\\work\\demo  ", "  npm  "))
                .thenReturn(new TaskPermissionService.TaskPermissionDecision(false, false));
        when(taskRunRepository.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId(104L);
            return run;
        });
        when(taskRunRepository.findByIdForUpdate(104L)).thenReturn(Optional.of(TaskRun.builder().id(104L).build()));
        when(taskEventRepository.findTopByTaskRunIdOrderBySequenceNoDesc(104L)).thenReturn(Optional.empty());
        when(taskEventRepository.findByTaskRunIdOrderBySequenceNoAsc(104L)).thenReturn(List.of(
                TaskEvent.builder()
                        .id(501L)
                        .taskRun(TaskRun.builder().id(104L).build())
                        .sequenceNo(1)
                        .eventType(TaskEventType.TASK_CREATED)
                        .payloadJson("{\"requestText\":\"trimmed\"}")
                        .createdAt(LocalDateTime.of(2026, 4, 29, 22, 44))
                        .build()
        ));
        when(taskEventRepository.save(any(TaskEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskRunView view = service.start(new TaskStartRequest(
                "  web:test-user  ",
                44L,
                longRequest,
                "  D:\\work\\demo  ",
                "  npm  "
        ));

        assertEquals("web:test-user", view.userId());
        assertEquals("D:\\work\\demo", view.workspacePath());
        assertEquals("npm", view.commandCategory());
        assertEquals(48, view.title().length());
    }

    @Test
    void start_shouldRejectBlankRequestText() {
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);
        TaskPermissionService taskPermissionService = mock(TaskPermissionService.class);
        TaskExecutionEngine taskExecutionEngine = mock(TaskExecutionEngine.class);
        TaskRuntimeServiceImpl service = new TaskRuntimeServiceImpl(
                taskRunRepository,
                new TaskEventStreamServiceImpl(taskEventRepository, taskRunRepository, new ObjectMapper()),
                taskPermissionService,
                taskExecutionEngine
        );

        when(taskPermissionService.evaluate("web:test-user", "D:\\work\\demo", "npm"))
                .thenReturn(new TaskPermissionService.TaskPermissionDecision(false, false));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.start(
                new TaskStartRequest("web:test-user", 12L, "   ", "D:\\work\\demo", "npm")
        ));

        assertEquals("requestText must not be blank", error.getMessage());
    }

    private TaskRun baseRun(Long id, TaskRunState state) {
        return TaskRun.builder()
                .id(id)
                .userId("web:test-user")
                .chatSessionId(12L)
                .title("Fix the tests")
                .workspacePath("D:\\work\\demo")
                .commandCategory("npm")
                .requestText("Fix the tests")
                .state(state)
                .createdAt(LocalDateTime.of(2026, 4, 29, 22, 40))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 22, 41))
                .build();
    }

    private void stubExistingRun(TaskRunRepository taskRunRepository,
                                 TaskEventRepository taskEventRepository,
                                 TaskRun run,
                                 List<TaskEvent> persistedEvents) {
        when(taskRunRepository.findByIdAndUserId(run.getId(), run.getUserId())).thenReturn(Optional.of(run));
        when(taskRunRepository.findByIdForUpdate(run.getId())).thenAnswer(invocation -> Optional.of(run));
        when(taskRunRepository.save(run)).thenReturn(run);
        when(taskEventRepository.findByTaskRunIdOrderBySequenceNoAsc(run.getId()))
                .thenAnswer(invocation -> List.copyOf(persistedEvents));
        when(taskEventRepository.findTopByTaskRunIdOrderBySequenceNoDesc(run.getId()))
                .thenAnswer(invocation -> persistedEvents.isEmpty() ? Optional.empty() : Optional.of(persistedEvents.getLast()));
        when(taskEventRepository.save(any(TaskEvent.class))).thenAnswer(invocation -> {
            TaskEvent event = invocation.getArgument(0);
            if (event.getId() == null) {
                event.setId((long) (persistedEvents.size() + 1));
            }
            persistedEvents.add(event);
            return event;
        });
    }
}
