package com.lingshu.ai.core;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public class SystemProviderStreamingTest {
    public interface MyStreamingAssistant {
        TokenStream chat(@MemoryId Long id, @UserMessage String text);
    }

    public static void main(String[] args) {
        StreamingChatModel mockModel = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest req, StreamingChatResponseHandler handler) {
                System.out.println("STREAMING REQUEST MESSAGES:");
                req.messages().forEach(m -> System.out.println("Role: " + m.type() + " Text: " + m.toString()));
                handler.onPartialResponse("OK");
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("OK")).build());
            }
        };

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MyStreamingAssistant ai = AiServices.builder(MyStreamingAssistant.class)
            .streamingChatModel(mockModel)
            .chatMemoryProvider(id -> memory)
            .systemMessageProvider(id -> "I am a STREAMING system message")
            .toolProvider(dev.langchain4j.mcp.McpToolProvider.builder().mcpClients(java.util.Collections.emptyList()).build())
            .build();

        ai.chat(1L, "Hello user message").onCompleteResponse(r -> System.out.println("Done")).onError(Throwable::printStackTrace).start();
    }
}
