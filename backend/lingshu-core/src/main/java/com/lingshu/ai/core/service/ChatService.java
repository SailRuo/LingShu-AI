package com.lingshu.ai.core.service;

import reactor.core.publisher.Flux;

public interface ChatService {

    interface ToolEventListener {
        default void onToolStart(String toolCallId, String toolName, String arguments) {
        }

        default void onToolEnd(String toolCallId, String toolName, String arguments, String result, boolean isError) {
        }
    }

    String chat(String message);
    
    String chat(String message, Long agentId);
    
    String chat(String message, Long agentId, String userId);
    
    Flux<String> streamChat(String message);
    
    Flux<String> streamChat(String message, Long agentId);
    
    Flux<String> streamChat(String message, Long agentId, String userId);
    
    Flux<String> streamChat(String message, String model, String apiKey, String baseUrl);
    
    Flux<String> streamChat(String message, Long agentId, String model, String apiKey, String baseUrl);

    Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl);

    Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl,
                            ToolEventListener toolEventListener);
    
    Flux<String> streamWelcome();
    
    Flux<String> streamWelcome(String userId);
    
    java.util.List<String> getModels(String source, String baseUrl, String apiKey);
}
