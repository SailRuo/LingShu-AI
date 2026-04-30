package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.task.PlanStep;
import com.lingshu.ai.core.model.task.PlanStepType;
import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.core.service.TaskEventStreamService;
import com.lingshu.ai.core.service.TaskPlanner;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskExecutionEngineImplTest {

    @Test
    void schedule_shouldFailWhenWorkspaceDoesNotExist() {
        TaskEventStreamService taskEventStreamService = mock(TaskEventStreamService.class);
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskPlanner taskPlanner = mock(TaskPlanner.class);
        Executor directExecutor = Runnable::run;
        TaskExecutionEngineImpl engine = new TaskExecutionEngineImpl(
                directExecutor,
                taskEventStreamService,
                taskRunRepository,
                taskPlanner
        );

        TaskRun run = baseRun(301L, TaskRunState.RUNNING);
        when(taskRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(taskPlanner.plan(run)).thenReturn(defaultPlanFor(run));

        engine.schedule(run);

        // it should start scanning the workspace
        verify(taskEventStreamService, times(1)).appendEvent(eq(run), eq(TaskEventType.STEP_STARTED), any());
        // it should log the scan attempt
        verify(taskEventStreamService).appendEvent(eq(run), eq(TaskEventType.LOG), any());
        // it should transition to FAILED because the workspace doesn't exist
        verify(taskEventStreamService, times(1)).appendEvent(eq(run), eq(TaskEventType.TASK_FAILED), any());
    }

    @Test
    void restorePendingTasks_shouldOnlyScheduleRunningRuns() {
        TaskEventStreamService taskEventStreamService = mock(TaskEventStreamService.class);
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskPlanner taskPlanner = mock(TaskPlanner.class);
        Executor directExecutor = Runnable::run;
        TaskExecutionEngineImpl engine = new TaskExecutionEngineImpl(
                directExecutor,
                taskEventStreamService,
                taskRunRepository,
                taskPlanner
        );

        TaskRun runningOne = baseRun(302L, TaskRunState.RUNNING);
        TaskRun runningTwo = baseRun(303L, TaskRunState.RUNNING);
        when(taskRunRepository.findByState(TaskRunState.RUNNING)).thenReturn(List.of(runningOne, runningTwo));
        when(taskRunRepository.findById(runningOne.getId())).thenReturn(Optional.of(runningOne));
        when(taskRunRepository.findById(runningTwo.getId())).thenReturn(Optional.of(runningTwo));
        when(taskPlanner.plan(any(TaskRun.class))).thenAnswer(inv ->
                defaultPlanFor(inv.getArgument(0)));

        engine.restorePendingTasks();

        verify(taskRunRepository).findByState(TaskRunState.RUNNING);

        // each restore should start scanning and fail (workspace doesn't exist)
        verify(taskEventStreamService, times(2)).appendEvent(any(TaskRun.class), eq(TaskEventType.STEP_STARTED), any());
        verify(taskEventStreamService, times(2)).appendEvent(any(TaskRun.class), eq(TaskEventType.TASK_FAILED), any());
        // never emit TASK_STOPPED for restore
        verify(taskEventStreamService, never()).appendEvent(any(TaskRun.class), eq(TaskEventType.TASK_STOPPED), any());
    }

    @Test
    void pauseAndStop_shouldCancelQueuedExecutionBeforeEventsAreAppended() {
        TaskEventStreamService taskEventStreamService = mock(TaskEventStreamService.class);
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        TaskPlanner taskPlanner = mock(TaskPlanner.class);
        DeferredExecutor deferredExecutor = new DeferredExecutor();
        TaskExecutionEngineImpl engine = new TaskExecutionEngineImpl(
                deferredExecutor,
                taskEventStreamService,
                taskRunRepository,
                taskPlanner
        );

        TaskRun pausedRun = baseRun(304L, TaskRunState.RUNNING);
        engine.schedule(pausedRun);
        engine.pause(pausedRun);
        deferredExecutor.runAll();

        // verify no events were appended because execution was cancelled
        verify(taskEventStreamService, never()).appendEvent(eq(pausedRun), any(TaskEventType.class), any());
        verify(taskPlanner, never()).plan(any());

        TaskRun stoppedRun = baseRun(305L, TaskRunState.RUNNING);
        engine.schedule(stoppedRun);
        engine.stop(stoppedRun);
        deferredExecutor.runAll();

        verify(taskEventStreamService, never()).appendEvent(eq(stoppedRun), any(TaskEventType.class), any());
        verify(taskPlanner, never()).plan(any());
    }

    private TaskPlan defaultPlanFor(TaskRun run) {
        return new TaskPlan(
                List.of(
                        PlanStep.of(PlanStepType.SCAN_WORKSPACE, "Scan workspace"),
                        PlanStep.of(PlanStepType.READ_KEY_FILES, "Read key files"),
                        PlanStep.of(PlanStepType.EXECUTE_COMMAND, "Run command")
                ),
                "Default analysis plan",
                "general",
                LocalDateTime.now()
        );
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
                .createdAt(LocalDateTime.of(2026, 4, 29, 23, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 23, 1))
                .build();
    }

    private static final class DeferredExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }
}
