package com.lingshu.ai.core.dto.task;

import java.util.List;

public record TaskRunView(
        Long id,
        String userId,
        Long chatSessionId,
        String title,
        String workspacePath,
        String commandCategory,
        String state,
        String runtimeSnapshotJson,
        List<TaskEventView> events
) {
}
