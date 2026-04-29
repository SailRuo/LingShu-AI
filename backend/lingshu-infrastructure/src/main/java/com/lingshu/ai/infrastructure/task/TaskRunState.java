package com.lingshu.ai.infrastructure.task;

public enum TaskRunState {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    PAUSED,
    COMPLETED,
    FAILED,
    STOPPED
}
