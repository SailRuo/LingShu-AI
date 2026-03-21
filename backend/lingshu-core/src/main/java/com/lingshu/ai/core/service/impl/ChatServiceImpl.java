package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.ChatService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final com.lingshu.ai.core.service.MemoryService memoryService;
    private final com.lingshu.ai.infrastructure.repository.ChatSessionRepository sessionRepository;
    private final com.lingshu.ai.infrastructure.repository.ChatMessageRepository messageRepository;
    private final com.lingshu.ai.core.tool.LocalTools localTools;
    private final com.lingshu.ai.core.config.AiConfig.Assistant assistant;
    private final com.lingshu.ai.core.config.AiConfig.StreamingAssistant streamingAssistant;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final com.lingshu.ai.core.service.SettingService settingService;

    @org.springframework.beans.factory.annotation.Value("${lingshu.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    public ChatServiceImpl(com.lingshu.ai.core.service.MemoryService memoryService,
                           com.lingshu.ai.infrastructure.repository.ChatSessionRepository sessionRepository,
                           com.lingshu.ai.infrastructure.repository.ChatMessageRepository messageRepository,
                           com.lingshu.ai.core.tool.LocalTools localTools,
                           com.lingshu.ai.core.config.AiConfig.Assistant assistant,
                           com.lingshu.ai.core.config.AiConfig.StreamingAssistant streamingAssistant,
                           org.springframework.web.client.RestTemplate restTemplate,
                           com.lingshu.ai.core.service.SettingService settingService) {
        this.memoryService = memoryService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.localTools = localTools;
        this.assistant = assistant;
        this.streamingAssistant = streamingAssistant;
        this.restTemplate = restTemplate;
        this.settingService = settingService;
    }

    private com.lingshu.ai.infrastructure.entity.ChatSession getOrCreateSession() {
        return sessionRepository.findAll().stream().findFirst().orElseGet(() -> {
            com.lingshu.ai.infrastructure.entity.ChatSession session = com.lingshu.ai.infrastructure.entity.ChatSession.builder()
                    .title("Default Conversation")
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            return sessionRepository.save(session);
        });
    }

    @Override
    public String chat(String message) {
        com.lingshu.ai.infrastructure.entity.ChatSession session = getOrCreateSession();
        
        messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                .session(session)
                .role("user")
                .content(message)
                .createdAt(java.time.LocalDateTime.now())
                .build());

        // 1. 获取长期记忆相关上下文 (RAG)
        String longTermContext = memoryService.retrieveContext("User", message);
        
        // 2. 获取短期记忆 (最近 5 条对话记录)
        java.util.List<com.lingshu.ai.infrastructure.entity.ChatMessage> recentLogs = 
                messageRepository.findTop5BySessionOrderByCreatedAtDesc(session);
        java.util.Collections.reverse(recentLogs);
        
        StringBuilder shortTermContext = new StringBuilder();
        if (!recentLogs.isEmpty()) {
            shortTermContext.append("【近期对话流水】\n");
            recentLogs.forEach(m -> shortTermContext.append(m.getRole()).append(": ").append(m.getContent()).append("\n"));
        }

        String augmentedPrompt = String.format("""
                【感官记忆 - 长期 facts】
                %s
                
                %s
                
                【当前指令】
                %s
                
                指令回复准则：
                - 只有当用户显式要求“回忆”时，才引用以上记忆。
                - 如果事实或近期流水不足以支撑回忆，直接回答“之前的记忆有些模糊，能提醒我一下吗？”而非虚构（如：严禁虚构 Java 报错或深夜工作背景）。
                """, longTermContext, shortTermContext.toString(), message);
        
        log.debug("Augmented Prompt generated for chat (first 100 chars): {}...", augmentedPrompt.substring(0, Math.min(100, augmentedPrompt.length())));
        
        String response = assistant.chat(augmentedPrompt);

        messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                .session(session)
                .role("assistant")
                .content(response)
                .createdAt(java.time.LocalDateTime.now())
                .build());
        
        memoryService.extractFacts("User", message);
        
        return response;
    }

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(message, null, null, null);
    }

    @Override
    public Flux<String> streamChat(String message, String model, String apiKey, String baseUrl) {
        com.lingshu.ai.infrastructure.entity.ChatSession session = getOrCreateSession();
        
        messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                .session(session)
                .role("user")
                .content(message)
                .createdAt(java.time.LocalDateTime.now())
                .build());

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder assistantResponseStore = new StringBuilder();

        // 1. 获取长期记忆相关上下文 (RAG)
        String longTermContext = memoryService.retrieveContext("User", message);
        
        // 2. 获取短期记忆 (最近 5 条对话记录)
        java.util.List<com.lingshu.ai.infrastructure.entity.ChatMessage> recentLogs = 
                messageRepository.findTop5BySessionOrderByCreatedAtDesc(session);
        java.util.Collections.reverse(recentLogs);
        
        StringBuilder shortTermContext = new StringBuilder();
        if (!recentLogs.isEmpty()) {
            shortTermContext.append("【近期对话流水】\n");
            recentLogs.forEach(m -> shortTermContext.append(m.getRole()).append(": ").append(m.getContent()).append("\n"));
        }

        String augmentedPrompt = String.format("""
                【感官记忆 - 长期 facts】
                %s
                
                %s
                
                【当前指令】
                %s
                
                指令回复准则：
                - 只有当用户显式要求“回忆”时，才引用以上记忆。
                - 如果事实或近期流水不足以支撑回忆，直接回答“之前的记忆有些模糊，能提醒我一下吗？”而非虚构（如：严禁虚构 Java 报错或深夜工作背景）。
                """, longTermContext, shortTermContext.toString(), message);

        log.debug("Augmented Prompt generated for streamChat (first 100 chars): {}...", augmentedPrompt.substring(0, Math.min(100, augmentedPrompt.length())));
        if (log.isTraceEnabled()) log.trace("Full Augmented Prompt:\n{}", augmentedPrompt);

        com.lingshu.ai.core.config.AiConfig.StreamingAssistant currentAssistant = streamingAssistant;

        // Use database settings if provided ones are null
        String effectiveModel = model;
        String effectiveBaseUrl = baseUrl;
        String effectiveApiKey = apiKey;
        String effectiveSource = null;

        if (effectiveModel == null || effectiveBaseUrl == null) {
            com.lingshu.ai.infrastructure.entity.SystemSetting setting = settingService.getSetting();
            if (effectiveModel == null) effectiveModel = setting.getChatModel();
            if (effectiveBaseUrl == null) effectiveBaseUrl = setting.getBaseUrl();
            if (effectiveApiKey == null) effectiveApiKey = setting.getApiKey();
            effectiveSource = setting.getSource();
        }

        if (effectiveModel != null && effectiveBaseUrl != null) {
            dev.langchain4j.model.chat.StreamingChatLanguageModel dynamicModel;
            
            if ("ollama".equalsIgnoreCase(effectiveSource) || (effectiveSource == null && effectiveBaseUrl.contains("11434"))) {
                dynamicModel = dev.langchain4j.model.ollama.OllamaStreamingChatModel.builder()
                        .baseUrl(effectiveBaseUrl)
                        .modelName(effectiveModel)
                        .timeout(java.time.Duration.ofMinutes(2))
                        .build();
            } else {
                dynamicModel = dev.langchain4j.model.openai.OpenAiStreamingChatModel.builder()
                        .baseUrl(effectiveBaseUrl.endsWith("/v1") || effectiveBaseUrl.endsWith("/v1/") ? effectiveBaseUrl : effectiveBaseUrl + (effectiveBaseUrl.endsWith("/") ? "v1" : "/v1"))
                        .apiKey(effectiveApiKey != null && !effectiveApiKey.isBlank() ? effectiveApiKey : "no-key")
                        .modelName(effectiveModel)
                        .tokenizer(new dev.langchain4j.model.openai.OpenAiTokenizer("gpt-3.5-turbo"))
                        .timeout(java.time.Duration.ofMinutes(2))
                        .build();
            }
            
            currentAssistant = dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.config.AiConfig.StreamingAssistant.class)
                    .streamingChatLanguageModel(dynamicModel)
                    .build();
        }

        currentAssistant.chat(augmentedPrompt)
                .onNext(token -> {
                    assistantResponseStore.append(token);
                    sink.tryEmitNext(token);
                })
                .onComplete(response -> {
                    messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                            .session(session)
                            .role("assistant")
                            .content(assistantResponseStore.toString())
                            .createdAt(java.time.LocalDateTime.now())
                            .build());

                    sink.tryEmitComplete();
                    memoryService.extractFacts("User", message);
                })
                .onError(error -> {
                    sink.tryEmitError(error);
                })
                .start();

        return sink.asFlux();
    }

    @Override
    public Flux<String> streamWelcome() {
        java.util.List<com.lingshu.ai.infrastructure.entity.ChatMessage> lastMessages = messageRepository.findTop5ByOrderByCreatedAtDesc();
        
        if (lastMessages.isEmpty()) {
            return Flux.just("欢迎回来。我是灵枢 (LingShu-AI)，你的感官与记忆中枢。今天有什么我可以帮你的吗？");
        }

        StringBuilder historyContext = new StringBuilder("最近的对话记录：\n");
        for (int i = lastMessages.size() - 1; i >= 0; i--) {
            historyContext.append(lastMessages.get(i).getRole()).append(": ")
                          .append(lastMessages.get(i).getContent()).append("\n");
        }

        String greetingPrompt = "【历史对话记录】\n" + historyContext.toString() + "\n\n" +
                "请作为『灵枢 (LingShu-AI)』，基于以上对话动态生成一句情感共识强烈的中文欢迎语。如果对话还没开启，请作为新伙伴询问用户的身份。";

        com.lingshu.ai.core.config.AiConfig.StreamingAssistant currentAssistant = streamingAssistant;
        com.lingshu.ai.infrastructure.entity.SystemSetting setting = settingService.getSetting();

        if (setting.getChatModel() != null && setting.getBaseUrl() != null) {
            dev.langchain4j.model.chat.StreamingChatLanguageModel dynamicModel;
            if ("ollama".equalsIgnoreCase(setting.getSource()) || (setting.getSource() == null && setting.getBaseUrl().contains("11434"))) {
                dynamicModel = dev.langchain4j.model.ollama.OllamaStreamingChatModel.builder()
                        .baseUrl(setting.getBaseUrl())
                        .modelName(setting.getChatModel())
                        .timeout(java.time.Duration.ofMinutes(2))
                        .build();
            } else {
                String u = setting.getBaseUrl();
                dynamicModel = dev.langchain4j.model.openai.OpenAiStreamingChatModel.builder()
                        .baseUrl(u.endsWith("/v1") || u.endsWith("/v1/") ? u : u + (u.endsWith("/") ? "v1" : "/v1"))
                        .apiKey(setting.getApiKey() != null && !setting.getApiKey().isBlank() ? setting.getApiKey() : "no-key")
                        .modelName(setting.getChatModel())
                        .tokenizer(new dev.langchain4j.model.openai.OpenAiTokenizer("gpt-3.5-turbo"))
                        .timeout(java.time.Duration.ofMinutes(2))
                        .build();
            }
            currentAssistant = dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.config.AiConfig.StreamingAssistant.class)
                    .streamingChatLanguageModel(dynamicModel)
                    .build();
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        currentAssistant.chat(greetingPrompt)
                .onNext(sink::tryEmitNext)
                .onComplete(response -> sink.tryEmitComplete())
                .onError(err -> {
                    sink.tryEmitNext("欢迎回来。系统已就绪，随时准备与你共同探索。");
                    sink.tryEmitComplete();
                })
                .start();

        return sink.asFlux();
    }

    @Override
    public java.util.List<String> getModels(String source, String baseUrl, String apiKey) {
        String effectiveUrl = (baseUrl == null || baseUrl.isBlank()) ? this.baseUrl : baseUrl;
        if (effectiveUrl.endsWith("/")) effectiveUrl = effectiveUrl.substring(0, effectiveUrl.length() - 1);

        try {
            if ("openai".equalsIgnoreCase(source)) {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.set("Authorization", "Bearer " + apiKey);
                }
                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                String base = effectiveUrl;
                if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                
                String url;
                if (base.contains("/v1") || base.endsWith("/v1")) {
                    url = base + "/models";
                } else {
                    url = base + "/v1/models";
                }
                
                System.out.println("Fetching models from OpenAI-compatible endpoint: " + url);
                
                Object responseObj = null;
                try {
                    responseObj = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                } catch (Exception e) {
                    // Try fallback if /v1/models failed and base URL doesn't have v1
                    if (!base.contains("/v1")) {
                        String fallbackUrl = base + "/models";
                        System.out.println("Retrying fallback endpoint: " + fallbackUrl);
                        try {
                            responseObj = restTemplate.exchange(fallbackUrl, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                        } catch (Exception ex) {
                            throw ex; // still failed, catch at outer block
                        }
                    } else {
                        throw e;
                    }
                }
                
                if (responseObj instanceof java.util.Map response) {
                    if (response.containsKey("data") && response.get("data") instanceof java.util.List list) {
                        return (java.util.List<String>) list.stream()
                                .map(m -> (String) ((java.util.Map) m).get("id"))
                                .collect(java.util.stream.Collectors.toList());
                    }
                } else if (responseObj instanceof java.util.List list) {
                    return (java.util.List<String>) list.stream()
                            .map(m -> (String) ((java.util.Map) m).get("id"))
                            .collect(java.util.stream.Collectors.toList());
                }
                
                return java.util.List.of("gpt-3.5-turbo");
            } else {
                // Ollama 模式
                String url = effectiveUrl + "/api/tags";
                System.out.println("Fetching Ollama models from: " + url);
                
                java.util.Map response = restTemplate.getForObject(url, java.util.Map.class);
                if (response == null || !response.containsKey("models")) return java.util.List.of("qwen3.5:4b");
                java.util.List<java.util.Map> models = (java.util.List<java.util.Map>) response.get("models");
                return models.stream()
                        .map(m -> (String) m.get("name"))
                        .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch models: " + e.getMessage());
            return "openai".equalsIgnoreCase(source) ? java.util.List.of("gpt-3.5-turbo") : java.util.List.of("qwen3.5:4b");
        }
    }
}
