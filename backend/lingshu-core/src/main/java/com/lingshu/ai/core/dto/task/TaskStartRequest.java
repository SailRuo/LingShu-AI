package com.lingshu.ai.core.dto.task;

public record TaskStartRequest(
        String userId,
        Long chatSessionId,
        String requestText,
        String workspacePath,
        String commandCategory
) {
}
