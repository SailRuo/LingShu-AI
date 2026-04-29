package com.lingshu.ai.infrastructure.task;

public enum TaskEventType {
    TASK_CREATED,
    STEP_STARTED,
    STEP_COMPLETED,
    APPROVAL_REQUIRED,
    APPROVAL_GRANTED,
    APPROVAL_REJECTED,
    TASK_PAUSED,
    TASK_RESUMED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_STOPPED,
    LOG
}
