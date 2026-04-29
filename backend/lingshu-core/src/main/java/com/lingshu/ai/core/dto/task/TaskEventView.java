package com.lingshu.ai.core.dto.task;

public record TaskEventView(
        Long id,
        Integer sequenceNo,
        String eventType,
        String payloadJson,
        long timestamp
) {
}
