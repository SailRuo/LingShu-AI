package com.lingshu.ai.core.service;

import reactor.core.publisher.Flux;

public interface ChatService {
    String chat(String message);
    Flux<String> streamChat(String message);
}
