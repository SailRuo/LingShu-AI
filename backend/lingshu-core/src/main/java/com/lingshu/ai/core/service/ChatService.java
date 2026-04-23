package com.lingshu.ai.core.service;

import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {

    interface ToolEventListener {
        default void onToolStart(String toolCallId, String toolName, String arguments) {
        }

        default void onToolEnd(String toolCallId, String toolName, String arguments, String result, boolean isError) {
            onToolEnd(toolCallId, toolName, arguments, result, isError, List.of());
        }

        default void onToolEnd(String toolCallId, String toolName, String arguments, String result, boolean isError,
                               List<TurnTimelineService.ArtifactPayload> artifacts) {
        }
    }

    record ChatStreamRequest(
            String message,
            List<String> images,
            Long sessionId,
            Long agentId,
            String userId,
            String model,
            String apiKey,
            String baseUrl,
            Boolean enableThinking,
            ToolEventListener toolEventListener
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public String effectiveUserId() {
            return userId == null || userId.isBlank() ? "User" : userId;
        }

        public boolean thinkingEnabled() {
            return Boolean.TRUE.equals(enableThinking);
        }

        public static final class Builder {
            private String message;
            private List<String> images;
            private Long sessionId;
            private Long agentId;
            private String userId;
            private String model;
            private String apiKey;
            private String baseUrl;
            private Boolean enableThinking;
            private ToolEventListener toolEventListener;

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder images(List<String> images) {
                this.images = images;
                return this;
            }

            public Builder sessionId(Long sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public Builder agentId(Long agentId) {
                this.agentId = agentId;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public Builder baseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
                return this;
            }

            public Builder enableThinking(Boolean enableThinking) {
                this.enableThinking = enableThinking;
                return this;
            }

            public Builder toolEventListener(ToolEventListener toolEventListener) {
                this.toolEventListener = toolEventListener;
                return this;
            }

            public ChatStreamRequest build() {
                return new ChatStreamRequest(
                        message,
                        images,
                        sessionId,
                        agentId,
                        userId,
                        model,
                        apiKey,
                        baseUrl,
                        enableThinking,
                        toolEventListener
                );
            }
        }
    }

    Flux<String> streamChat(String message);

    Flux<String> streamChat(String message, Long agentId);

    Flux<String> streamChat(String message, Long agentId, String userId);

    Flux<String> streamChat(String message, String model, String apiKey, String baseUrl);

    Flux<String> streamChat(String message, Long agentId, String model, String apiKey, String baseUrl);

    Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl);

    Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl,
                            ToolEventListener toolEventListener);

    Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl, Boolean enableThinking, ToolEventListener toolEventListener);

    Flux<String> streamChat(String message, List<String> images, Long agentId, String userId, String model, String apiKey, String baseUrl, Boolean enableThinking, ToolEventListener toolEventListener);

    Flux<String> streamChat(String message, List<String> images, Long sessionId, Long agentId, String userId, String model, String apiKey, String baseUrl, Boolean enableThinking, ToolEventListener toolEventListener);

    Flux<String> streamChat(ChatStreamRequest request);

    Flux<String> streamWelcome();

    Flux<String> streamWelcome(String userId);

    void clearHistory(Long sessionId);

    java.util.List<String> getModels(String source, String baseUrl, String apiKey);
}
