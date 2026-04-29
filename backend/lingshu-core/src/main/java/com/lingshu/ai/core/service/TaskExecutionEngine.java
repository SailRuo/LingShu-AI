package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.TaskRun;

public interface TaskExecutionEngine {

    void schedule(TaskRun run);

    void pause(TaskRun run);

    void resume(TaskRun run);

    void stop(TaskRun run);

    void restorePendingTasks();
}
