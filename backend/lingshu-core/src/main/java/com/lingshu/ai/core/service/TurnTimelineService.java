package com.lingshu.ai.core.service;

import java.util.List;

public interface TurnTimelineService {

    record ArtifactPayload(String artifactType, String mimeType, String url, String base64Data) {}

    record ToolStepView(
            String toolCallId,
            String toolName,
            String arguments,
            String result,
            boolean isError,
            List<ArtifactPayload> artifacts
    ) {}

    record SegmentView(
            String type,
            String toolCallId,
            String toolName,
            String arguments,
            String result,
            boolean isError,
            String content,
            List<ArtifactPayload> artifacts
    ) {}

    record TurnView(
            Long id,
            long timestamp,
            String status,
            String userMessage,
            List<String> userImages,
            String assistantMessage,
            String errorMessage,
            List<ToolStepView> toolSteps,
            List<SegmentView> segments
    ) {}

    Long startTurn(Long sessionId, String userMessage, List<String> userImages);

    void recordToolStart(Long turnId, String toolCallId, String toolName, String arguments);

    void recordToolEnd(Long turnId, String toolCallId, String toolName, String arguments, String result, boolean isError,
                       List<ArtifactPayload> artifacts);

    void recordAssistantText(Long turnId, String content);

    void completeTurn(Long turnId, String assistantMessage);

    void failTurn(Long turnId, String errorMessage);

    List<TurnView> getTurnHistory(Long sessionId, Long beforeId, int size);

    void clearTurnHistory(Long sessionId);
}
