package com.lingshu.ai.core;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

public class SystemProviderTest {
    public interface MyAssistant {
        String chat(@MemoryId int id, @UserMessage String text);
    }

    public static void main(String[] args) {
        ChatModel mockModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                System.out.println("REQUEST MESSAGES:");
                req.messages().forEach(m -> System.out.println("Role: " + m.type() + " Text: " + m.toString()));
                return ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("OK")).build();
            }
        };

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MyAssistant ai = AiServices.builder(MyAssistant.class)
            .chatModel(mockModel)
            .chatMemoryProvider(id -> memory)
            .systemMessageProvider(id -> "I am a system message")
            .build();

        ai.chat(1, "Hello user message");
    }
}
