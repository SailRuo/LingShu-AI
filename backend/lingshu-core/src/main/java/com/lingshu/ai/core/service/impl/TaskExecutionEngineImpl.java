package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.TaskEventStreamService;
import com.lingshu.ai.core.service.TaskExecutionEngine;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TaskExecutionEngineImpl implements TaskExecutionEngine {

    private static final String ANALYZE_WORKSPACE_STEP = "analyze_workspace";

    private final Executor taskExecutor;
    private final TaskEventStreamService taskEventStreamService;
    private final TaskRunRepository taskRunRepository;
    private final ConcurrentHashMap<Long, TaskHandle> activeRuns = new ConcurrentHashMap<>();

    public TaskExecutionEngineImpl(@Qualifier("taskExecutor") Executor taskExecutor,
                                   TaskEventStreamService taskEventStreamService,
                                   TaskRunRepository taskRunRepository) {
        this.taskExecutor = taskExecutor;
        this.taskEventStreamService = taskEventStreamService;
        this.taskRunRepository = taskRunRepository;
    }

    @Override
    public void schedule(TaskRun run) {
        TaskHandle handle = new TaskHandle();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            if (handle.isCancelled()) {
                return;
            }
            taskEventStreamService.appendEvent(run, TaskEventType.STEP_STARTED, Map.of(
                    "step", ANALYZE_WORKSPACE_STEP
            ));
            if (handle.isCancelled()) {
                return;
            }
            taskEventStreamService.appendEvent(run, TaskEventType.LOG, Map.of(
                    "message", "Starting workspace analysis",
                    "step", ANALYZE_WORKSPACE_STEP
            ));
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
