package com.lingshu.ai.infrastructure.memory;

import com.lingshu.ai.infrastructure.entity.ChatTurn;
import com.lingshu.ai.infrastructure.entity.ChatTurnArtifact;
import com.lingshu.ai.infrastructure.entity.ChatTurnEvent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles model-safe chat context from turn/event persistence.
 * This class is intentionally stateless and deterministic.
 */
public class ChatContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ChatContextAssembler.class);

    public record AssemblyResult(
            List<ChatMessage> messages,
            Map<String, Integer> diagnostics
    ) {
    }

    public AssemblyResult assemble(List<ChatTurn> turns,
                                   Map<Long, List<ChatTurnEvent>> eventsByTurnId) {
        return assemble(turns, eventsByTurnId, Map.of());
    }

    public AssemblyResult assemble(List<ChatTurn> turns,
                                   Map<Long, List<ChatTurnEvent>> eventsByTurnId,
                                   Map<Long, List<ChatTurnArtifact>> artifactsByEventId) {
        List<ChatMessage> messages = new ArrayList<>();
        Map<String, Integer> diagnostics = new LinkedHashMap<>();

        if (turns == null || turns.isEmpty()) {
            return new AssemblyResult(messages, diagnostics);
        }

        for (ChatTurn turn : turns) {
            List<ChatTurnEvent> turnEvents = eventsByTurnId.getOrDefault(turn.getId(), List.of());
            UserMessage userMessage = toUserMessage(turn, turnEvents, diagnostics);
            if (userMessage == null) {
                increment(diagnostics, "skip_turn_without_user");
                continue;
            }

            messages.add(userMessage);

            Map<String, ChatTurnEvent> pendingToolStarts = new LinkedHashMap<>();
            boolean hasAssistantTextEvent = false;
            int anonymousToolCounter = 0;

            for (ChatTurnEvent event : turnEvents) {
                String eventType = safe(event.getEventType());

                if ("tool_start".equals(eventType)) {
                    String callId = safe(event.getToolCallId());
                    if (callId.isBlank()) {
                        callId = "anonymous-" + (++anonymousToolCounter);
                    }
                    pendingToolStarts.put(callId, event);
                    continue;
                }

                if ("tool_end".equals(eventType)) {
                    String callId = safe(event.getToolCallId());
                    if (callId.isBlank()) {
                        increment(diagnostics, "drop_tool_end_without_call_id");
                        continue;
                    }

                    ChatTurnEvent start = pendingToolStarts.remove(callId);
                    if (start == null) {
                        increment(diagnostics, "drop_orphan_tool_end");
                        continue;
                    }

                    ToolExecutionRequest req = ToolExecutionRequest.builder()
                            .id(safe(start.getToolCallId()))
                            .name(safe(start.getToolName()))
                            .arguments(safe(start.getArguments()))
                            .build();
                    messages.add(AiMessage.from(List.of(req)));
                    messages.add(ToolExecutionResultMessage.from(
                            safe(event.getToolCallId()),
                            safe(event.getToolName()),
                            safe(event.getContent())
                    ));
                    UserMessage artifactMessage = toToolArtifactUserMessage(event, artifactsByEventId, diagnostics);
                    if (artifactMessage != null) {
                        messages.add(artifactMessage);
                    }
                    continue;
                }

                if ("assistant_text".equals(eventType)) {
                    String text = safe(event.getContent());
                    if (!text.isBlank()) {
                        messages.add(AiMessage.from(text));
                        hasAssistantTextEvent = true;
                    } else {
                        increment(diagnostics, "drop_empty_assistant_text_event");
                    }
                }
            }

            if (!pendingToolStarts.isEmpty()) {
                incrementBy(diagnostics, "drop_incomplete_tool_start",
                        pendingToolStarts.size());
            }

            if (!hasAssistantTextEvent) {
                String assistant = safe(turn.getAssistantMessage());
                if (!assistant.isBlank()) {
                    messages.add(AiMessage.from(assistant));
                }
            }
        }

        return new AssemblyResult(messages, diagnostics);
    }

    private UserMessage toUserMessage(ChatTurn turn,
                                      List<ChatTurnEvent> turnEvents,
                                      Map<String, Integer> diagnostics) {
        String text = safe(turn.getUserMessage());
        List<String> images = parseImages(turn.getUserImagesJson());

        if ((text == null || text.isBlank()) && images.isEmpty()) {
            if (hasReplayableTurnSignals(turn, turnEvents)) {
                // Gemini/OpenAI continuation calls require at least one user content item.
                // Keep the turn replayable even when historic data has empty user_message.
                increment(diagnostics, "inject_placeholder_user_message");
                return UserMessage.from("请继续基于上文与工具结果回答用户问题。");
            }
            return null;
        }

        List<Content> contents = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            contents.add(TextContent.from(text));
        }
        for (String image : images) {
            if (!addImageContent(contents, image)) {
                increment(diagnostics, "drop_invalid_user_image_payload");
            }
        }

        if (contents.isEmpty()) {
            return null;
        }
        return UserMessage.from(contents);
    }

    private boolean hasReplayableTurnSignals(ChatTurn turn, List<ChatTurnEvent> turnEvents) {
        if (turn == null) {
            return false;
        }
        if (!safe(turn.getAssistantMessage()).isBlank()) {
            return true;
        }
        if (turnEvents == null || turnEvents.isEmpty()) {
            return false;
        }
        for (ChatTurnEvent event : turnEvents) {
            String type = safe(event.getEventType());
            if ("assistant_text".equals(type) || "tool_start".equals(type) || "tool_end".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseImages(String userImagesJson) {
        if (userImagesJson == null || userImagesJson.isBlank()) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(userImagesJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    });
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean addImageContent(List<Content> contents, String image) {
        if (image == null || image.isBlank()) {
            return false;
        }
        String trimmed = image.trim();
        try {
            if (trimmed.startsWith("data:") && trimmed.contains(",")) {
                int sep = trimmed.indexOf(',');
                String header = trimmed.substring(5, sep);
                String mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : "image/jpeg";
                String base64 = trimmed.substring(sep + 1);
                contents.add(ImageContent.from(base64, mime));
                return true;
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                contents.add(ImageContent.from(trimmed));
                return true;
            }
            contents.add(ImageContent.from(trimmed, "image/jpeg"));
            return true;
        } catch (Exception e) {
            log.debug("Skip invalid image payload in turn context: {}", e.getMessage());
            return false;
        }
    }

    private UserMessage toToolArtifactUserMessage(ChatTurnEvent event,
                                                  Map<Long, List<ChatTurnArtifact>> artifactsByEventId,
                                                  Map<String, Integer> diagnostics) {
        if (event == null || event.getId() == null || event.getTurn() == null) {
            return null;
        }

        List<ChatTurnArtifactView> images = artifactsByEventId.getOrDefault(event.getId(), List.of()).stream()
                .filter(artifact -> "image".equalsIgnoreCase(safe(artifact.getArtifactType())))
                .map(artifact -> new ChatTurnArtifactView(
                        safe(artifact.getMimeType()),
                        safe(artifact.getUrl()),
                        safe(artifact.getBase64Data())
                ))
                .toList();
        if (images.isEmpty()) {
            return null;
        }

        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from("[工具 " + safe(event.getToolName()) + " 返回了 "
                + images.size() + " 张图片，请基于下面图片继续回答用户。]"));
        for (ChatTurnArtifactView image : images) {
            if (image.base64Data() != null && !image.base64Data().isBlank()) {
                contents.add(ImageContent.from(image.base64Data(), image.mimeType().isBlank() ? "image/png" : image.mimeType()));
            } else if (image.url() != null && !image.url().isBlank()) {
                contents.add(ImageContent.from(image.url()));
            } else {
                increment(diagnostics, "drop_invalid_tool_image_artifact");
            }
        }

        if (contents.size() <= 1) {
            return null;
        }
        return UserMessage.from(contents);
    }

    private record ChatTurnArtifactView(String mimeType, String url, String base64Data) {
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void increment(Map<String, Integer> diagnostics, String key) {
        diagnostics.merge(key, 1, Integer::sum);
    }

    private void incrementBy(Map<String, Integer> diagnostics, String key, int delta) {
        diagnostics.merge(key, delta, Integer::sum);
    }
}
