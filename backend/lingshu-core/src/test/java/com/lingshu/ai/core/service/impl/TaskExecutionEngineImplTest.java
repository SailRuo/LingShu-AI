package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.TaskEventStreamService;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
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
    void schedule_shouldAppendAnalyzeWorkspaceStepAndLog() {
        TaskEventStreamService taskEventStreamService = mock(TaskEventStreamService.class);
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        Executor directExecutor = Runnable::run;
        TaskExecutionEngineImpl engine = new TaskExecutionEngineImpl(
                directExecutor,
                taskEventStreamService,
                taskRunRepository
        );

        TaskRun run = baseRun(301L, TaskRunState.RUNNING);

        engine.schedule(run);

        verify(taskEventStreamService).appendEvent(eq(run), eq(TaskEventType.STEP_STARTED), any());
        verify(taskEventStreamService).appendEvent(eq(run), eq(TaskEventType.LOG), any());
    }

    @Test
    void restorePendingTasks_shouldOnlyScheduleRunningRuns() {
        TaskEventStreamService taskEventStreamService = mock(TaskEventStreamService.class);
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        Executor directExecutor = Runnable::run;
        TaskExecutionEngineImpl engine = new TaskExecutionEngineImpl(
                directExecutor,
                taskEventStreamService,
                taskRunRepository
        );

        TaskRun runningOne = baseRun(302L, TaskRunState.RUNNING);
        TaskRun runningTwo = baseRun(303L, TaskRunState.RUNNING);
        when(taskRunRepository.findByState(TaskRunState.RUNNING)).thenReturn(List.of(runningOne, runningTwo));

        engine.restorePendingTasks();

        verify(taskRunRepository).findByState(TaskRunState.RUNNING);
        verify(taskEventStreamService, times(2)).appendEvent(any(TaskRun.class), eq(TaskEventType.STEP_STARTED), any());
        verify(taskEventStreamService, times(2)).appendEvent(any(TaskRun.class), eq(TaskEventType.LOG), any());
        verify(taskEventStreamService, never()).appendEvent(any(TaskRun.class), eq(TaskEventType.TASK_STOPPED), any());
    }

    @Test
    void pauseAndStop_shouldCancelQueuedExecutionBeforeEventsAreAppended() {
        TaskEventStreamService taskEventStreamService = mock(TaskEventStreamService.class);
        TaskRunRepository taskRunRepository = mock(TaskRunRepository.class);
        DeferredExecutor deferredExecutor = new DeferredExecutor();
        TaskExecutionEngineImpl engine = new TaskExecutionEngineImpl(
                deferredExecutor,
                taskEventStreamService,
                taskRunRepository
        );

        TaskRun pausedRun = baseRun(304L, TaskRunState.RUNNING);
        engine.schedule(pausedRun);
        engine.pause(pausedRun);
        deferredExecutor.runAll();

        verify(taskEventStreamService, never()).appendEvent(eq(pausedRun), any(TaskEventType.class), any());

        TaskRun stoppedRun = baseRun(305L, TaskRunState.RUNNING);
        engine.schedule(stoppedRun);
        engine.stop(stoppedRun);
        deferredExecutor.runAll();

        verify(taskEventStreamService, never()).appendEvent(eq(stoppedRun), any(TaskEventType.class), any());
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
