package com.lingshu.ai.core.config;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiConfigStreamingAssistantSystemMessageTest {

    @Test
    void streamingAssistant_shouldInjectAnnotatedSystemMessageIntoRequest() {
        RecordingStreamingChatModel model = new RecordingStreamingChatModel();
        var memoryProvider = new AiConfig().chatMemoryProvider(new NoopChatMemoryStore());

        AiConfig.StreamingAssistant assistant = AiServices.builder(AiConfig.StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(memoryProvider)
                .build();

        assistant.chat(1L, "你好", "system prompt")
                .onError(Throwable::printStackTrace)
                .start();

        List<dev.langchain4j.data.message.ChatMessage> messages = model.messages();
        assertEquals(2, messages.size());
        assertEquals(ChatMessageType.SYSTEM, messages.get(0).type());
        assertEquals(ChatMessageType.USER, messages.get(1).type());
    }

    private static final class RecordingStreamingChatModel implements StreamingChatModel {
        private final List<dev.langchain4j.data.message.ChatMessage> messages = new CopyOnWriteArrayList<>();

        @Override
        public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            messages.clear();
            messages.addAll(chatRequest.messages());
            handler.onPartialResponse("ok");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from("ok"))
                    .build());
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().build();
        }

        List<dev.langchain4j.data.message.ChatMessage> messages() {
            return messages;
        }
    }

    private static final class NoopChatMemoryStore implements dev.langchain4j.store.memory.chat.ChatMemoryStore {
        @Override
        public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
            return List.of();
        }

        @Override
        public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        }

        @Override
        public void deleteMessages(Object memoryId) {
        }
    }
}
