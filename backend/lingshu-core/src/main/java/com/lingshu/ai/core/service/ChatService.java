package com.lingshu.ai.core.service;

import reactor.core.publisher.Flux;

public interface ChatService {
    String chat(String message);
    Flux<String> streamChat(String message);
    Flux<String> streamChat(String message, String model, String apiKey, String baseUrl);
    Flux<String> streamWelcome();
    java.util.List<String> getModels(String source, String baseUrl, String apiKey);
}
