package com.lingshu.ai.core.service;

import reactor.core.publisher.Flux;

public interface ChatService {
    /**
     * 同步聊天接口。
     */
    String chat(String message);
    
    /**
     * 流式聊天接口（默认配置）。
     */
    Flux<String> streamChat(String message);
    
    /**
     * 流式聊天接口，支持指定模型和配置。
     */
    Flux<String> streamChat(String message, String model, String apiKey, String baseUrl);
    
    /**
     * 动态生成并流式输出欢迎语。
     */
    Flux<String> streamWelcome();
    
    /**
     * 获取指定渠道的模型列表。
     */
    java.util.List<String> getModels(String source, String baseUrl, String apiKey);
}
