package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final com.lingshu.ai.core.service.MemoryService memoryService;
    private final com.lingshu.ai.core.service.AgentConfigService agentConfigService;
    private final com.lingshu.ai.infrastructure.repository.ChatSessionRepository sessionRepository;
    private final com.lingshu.ai.infrastructure.repository.ChatMessageRepository messageRepository;
    private final com.lingshu.ai.core.config.AiConfig.Assistant assistant;
    private final com.lingshu.ai.core.config.AiConfig.StreamingAssistant streamingAssistant;
    private final dev.langchain4j.model.chat.StreamingChatLanguageModel streamingChatLanguageModel;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final com.lingshu.ai.core.service.SettingService settingService;
    private final com.lingshu.ai.core.service.SystemLogService systemLogService;
    private final com.lingshu.ai.core.service.AffinityService affinityService;
    private final com.lingshu.ai.core.service.EmotionAnalyzer emotionAnalyzer;
    private final com.lingshu.ai.core.service.ProactiveService proactiveService;
    private final com.lingshu.ai.core.service.PromptBuilderService promptBuilderService;

    @org.springframework.beans.factory.annotation.Value("${lingshu.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    public ChatServiceImpl(com.lingshu.ai.core.service.MemoryService memoryService,
                           com.lingshu.ai.core.service.AgentConfigService agentConfigService,
                           com.lingshu.ai.infrastructure.repository.ChatSessionRepository sessionRepository,
                           com.lingshu.ai.infrastructure.repository.ChatMessageRepository messageRepository,
                           com.lingshu.ai.core.config.AiConfig.Assistant assistant,
                           com.lingshu.ai.core.config.AiConfig.StreamingAssistant streamingAssistant,
                           dev.langchain4j.model.chat.StreamingChatLanguageModel streamingChatLanguageModel,
                           org.springframework.web.client.RestTemplate restTemplate,
                           com.lingshu.ai.core.service.SettingService settingService,
                           com.lingshu.ai.core.service.SystemLogService systemLogService,
                           com.lingshu.ai.core.service.AffinityService affinityService,
                           com.lingshu.ai.core.service.EmotionAnalyzer emotionAnalyzer,
                           com.lingshu.ai.core.service.ProactiveService proactiveService,
                           com.lingshu.ai.core.service.PromptBuilderService promptBuilderService) {
        this.memoryService = memoryService;
        this.agentConfigService = agentConfigService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.assistant = assistant;
        this.streamingAssistant = streamingAssistant;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.restTemplate = restTemplate;
        this.settingService = settingService;
        this.systemLogService = systemLogService;
        this.affinityService = affinityService;
        this.emotionAnalyzer = emotionAnalyzer;
        this.proactiveService = proactiveService;
        this.promptBuilderService = promptBuilderService;
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

    private AgentConfig getAgent(Long agentId) {
        if (agentId != null) {
            return agentConfigService.getAgentById(agentId).orElse(null);
        }
        return agentConfigService.getDefaultAgent().orElse(null);
    }

    @Override
    public String chat(String message) {
        return chat(message, null, "User");
    }

    @Override
    public String chat(String message, Long agentId) {
        return chat(message, agentId, "User");
    }

    @Override
    public String chat(String message, Long agentId, String userId) {
        com.lingshu.ai.infrastructure.entity.ChatSession session = getOrCreateSession();
        AgentConfig agent = getAgent(agentId);
        String agentName = agent != null ? agent.getDisplayName() : "灵枢";
        
        messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                .session(session)
                .role("user")
                .content(message)
                .createdAt(java.time.LocalDateTime.now())
                .build());
        
        systemLogService.info("\n收到用户消息: " + (message.length() > 20 ? message.substring(0, 20) + "..." : message), "CHAT");

        analyzeAndUpdateUserState(userId, message);

        String longTermContext = memoryService.retrieveContext(userId, message);
        if (longTermContext != null && !longTermContext.isBlank()) {
            systemLogService.info("长期记忆检索完成，获取到相关事实。", "MEMORY");
        }
        
        java.util.List<com.lingshu.ai.infrastructure.entity.ChatMessage> recentLogs = 
                messageRepository.findTop5BySessionOrderByCreatedAtDesc(session);
        java.util.Collections.reverse(recentLogs);
        
        StringBuilder shortTermContext = new StringBuilder();
        if (!recentLogs.isEmpty()) {
            shortTermContext.append("【近期对话流水】\n");
            recentLogs.forEach(m -> shortTermContext.append(m.getRole()).append(": ").append(m.getContent()).append("\n"));
        }

        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);

        String systemPrompt = promptBuilderService.buildSystemPrompt(agent);
        String userPrompt = promptBuilderService.buildUserPrompt(relationshipPrompt, longTermContext, shortTermContext.toString(), message);
        
        log.debug("System Prompt generated for chat (first 100 chars): {}...", systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
        log.debug("User Prompt generated for chat (first 100 chars): {}...", userPrompt.substring(0, Math.min(100, userPrompt.length())));
        
        systemLogService.llmStart("default-model", "ollama", "LLM");
        String response = assistant.chat(systemPrompt + "\n\n" + userPrompt);
        systemLogService.llmEnd(response != null ? response.length() / 4 : 0, "LLM");

        messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                .session(session)
                .role("assistant")
                .content(response)
                .createdAt(java.time.LocalDateTime.now())
                .build());
        
        memoryService.extractFacts(userId, message);
        
        return response;
    }

    private void analyzeAndUpdateUserState(String userId, String message) {
        try {
            EmotionAnalysis emotion = emotionAnalyzer.analyze(message);
            if (emotion != null) {
                affinityService.updateEmotion(userId, emotion.getEmotion(), emotion.getIntensity());
                systemLogService.info(String.format("情绪分析: %s (强度: %.2f)", 
                        emotion.getEmotion(), emotion.getIntensity()), "EMOTION");
                
                if (emotion.isPositive()) {
                    affinityService.increaseAffinity(userId, 1);
                } else if (emotion.isNegative() && emotion.getIntensity() > 0.5) {
                    affinityService.decreaseAffinity(userId, 1);
                }
                
                if (emotion.needsAttention()) {
                    systemLogService.info("检测到用户需要关注", "EMOTION");
                }
            }
            
            affinityService.recordInteraction(userId);
        } catch (Exception e) {
            log.warn("情绪分析失败: {}", e.getMessage());
        }
    }

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(message, null, "User", null, null);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId) {
        return streamChat(message, agentId, "User", null, null);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId) {
        return streamChat(message, agentId, userId, null, null);
    }

    @Override
    public Flux<String> streamChat(String message, String model, String apiKey, String baseUrl) {
        return streamChat(message, null, "User", model, apiKey, baseUrl);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String model, String apiKey, String baseUrl) {
        return streamChat(message, agentId, "User", model, apiKey, baseUrl);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl) {
        com.lingshu.ai.infrastructure.entity.ChatSession session = getOrCreateSession();
        AgentConfig agent = getAgent(agentId);
        String agentName = agent != null ? agent.getDisplayName() : "灵枢";
        
        messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                .session(session)
                .role("user")
                .content(message)
                .createdAt(java.time.LocalDateTime.now())
                .build());

        systemLogService.info("收到用户消息 (流式): " + (message.length() > 20 ? message.substring(0, 20) + "..." : message), "CHAT");

        analyzeAndUpdateUserState(userId, message);

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder assistantResponseStore = new StringBuilder();

        String longTermContext = memoryService.retrieveContext(userId, message);
        
        java.util.List<com.lingshu.ai.infrastructure.entity.ChatMessage> recentLogs = 
                messageRepository.findTop5BySessionOrderByCreatedAtDesc(session);
        java.util.Collections.reverse(recentLogs);
        
        StringBuilder shortTermContext = new StringBuilder();
        if (!recentLogs.isEmpty()) {
            shortTermContext.append("【近期对话流水】\n");
            recentLogs.forEach(m -> shortTermContext.append(m.getRole()).append(": ").append(m.getContent()).append("\n"));
        }

        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);

        String systemPrompt = promptBuilderService.buildSystemPrompt(agent);
        String userPrompt = promptBuilderService.buildUserPrompt(relationshipPrompt, longTermContext, shortTermContext.toString(), message);

        log.debug("System Prompt generated for streamChat (first 100 chars): {}...", systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
        log.debug("User Prompt generated for streamChat (first 100 chars): {}...", userPrompt.substring(0, Math.min(100, userPrompt.length())));

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

        systemLogService.info(String.format("准备LLM调用 | 智能体: %s | 模型: %s | 来源: %s | 端点: %s", 
            agentName, effectiveModel, effectiveSource, effectiveBaseUrl), "LLM");
        systemLogService.startTimer("llm_LLM");

        com.lingshu.ai.core.config.AiConfig.RawStreamingAssistant rawAssistant;

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
            
            rawAssistant = dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.config.AiConfig.RawStreamingAssistant.class)
                    .streamingChatLanguageModel(dynamicModel)
                    .build();
        } else {
            rawAssistant = dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.config.AiConfig.RawStreamingAssistant.class)
                    .streamingChatLanguageModel(streamingChatLanguageModel)
                    .build();
        }

        rawAssistant.chat(systemPrompt, userPrompt)
                .onNext(token -> {
                    if (assistantResponseStore.length() == 0) {
                        systemLogService.info("流式输出已开启，接收首个 token...", "LLM");
                    }
                    assistantResponseStore.append(token);
                    sink.tryEmitNext(token);
                })
                .onComplete(response -> {
                    int tokenCount = assistantResponseStore.length() / 4;
                    systemLogService.llmEnd(tokenCount, "LLM");
                    
                    messageRepository.save(com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                            .session(session)
                            .role("assistant")
                            .content(assistantResponseStore.toString())
                            .createdAt(java.time.LocalDateTime.now())
                            .build());

                    systemLogService.success("对话完成，回复长度: " + assistantResponseStore.length() + " 字符", "CHAT");
                    sink.tryEmitComplete();
                    memoryService.extractFacts(userId, message);
                })
                .onError(error -> {
                    systemLogService.llmError(error.getMessage(), "LLM");
                    sink.tryEmitError(error);
                })
                .start();

        return sink.asFlux();
    }

    @Override
    public Flux<String> streamWelcome() {
        return streamWelcome("User");
    }

    @Override
    public Flux<String> streamWelcome(String userId) {
        systemLogService.info("生成欢迎消息...", "CHAT");
        
        java.util.List<com.lingshu.ai.infrastructure.entity.ChatMessage> lastMessages = messageRepository.findTop5ByOrderByCreatedAtDesc();
        
        if (lastMessages.isEmpty()) {
            systemLogService.info("无历史对话，使用默认欢迎语", "CHAT");
            return Flux.just("欢迎回来。我是灵枢 (LingShu-AI)，你的感官与记忆中枢。今天有什么我可以帮你的吗？");
        }

        StringBuilder historyContext = new StringBuilder("最近的对话记录：\n");
        for (int i = lastMessages.size() - 1; i >= 0; i--) {
            historyContext.append(lastMessages.get(i).getRole()).append(": ")
                          .append(lastMessages.get(i).getContent()).append("\n");
        }

        AgentConfig defaultAgent = agentConfigService.getDefaultAgent().orElse(null);
        String agentName = defaultAgent != null ? defaultAgent.getDisplayName() : "灵枢";
        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);
        
        String systemPrompt = promptBuilderService.buildSystemPrompt(defaultAgent);
        String userPrompt = promptBuilderService.buildWelcomeUserPrompt(relationshipPrompt, historyContext.toString(), agentName);

        com.lingshu.ai.infrastructure.entity.SystemSetting setting = settingService.getSetting();

        String effectiveModel = setting.getChatModel();
        String effectiveSource = setting.getSource();

        systemLogService.info(String.format("欢迎消息LLM调用 | 模型: %s | 来源: %s | 端点: %s",
            effectiveModel, effectiveSource, setting.getBaseUrl()), "LLM");
        systemLogService.startTimer("llm_welcome");

        com.lingshu.ai.core.config.AiConfig.RawStreamingAssistant rawAssistant;

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
            rawAssistant = dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.config.AiConfig.RawStreamingAssistant.class)
                    .streamingChatLanguageModel(dynamicModel)
                    .build();
        } else {
            rawAssistant = dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.config.AiConfig.RawStreamingAssistant.class)
                    .streamingChatLanguageModel(streamingChatLanguageModel)
                    .build();
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder welcomeBuilder = new StringBuilder();

        rawAssistant.chat(systemPrompt, userPrompt)
                .onNext(token -> {
                    welcomeBuilder.append(token);
                    sink.tryEmitNext(token);
                })
                .onComplete(response -> {
                    systemLogService.endTimer("llm_welcome", "欢迎消息生成完成", "LLM");
                    systemLogService.success("欢迎消息长度: " + welcomeBuilder.length() + " 字符", "CHAT");
                    sink.tryEmitComplete();
                })
                .onError(err -> {
                    systemLogService.endTimer("llm_welcome", "欢迎消息生成失败", "LLM");
                    systemLogService.error(err.getMessage(), "LLM");
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

        systemLogService.info(String.format("获取模型列表 | 来源: %s | 端点: %s", source, effectiveUrl), "SYSTEM");
        systemLogService.startTimer("get_models");

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
                
                systemLogService.debug("请求OpenAI模型列表: " + url, "SYSTEM");
                
                Object responseObj = null;
                try {
                    responseObj = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                } catch (Exception e) {
                    if (!base.contains("/v1")) {
                        String fallbackUrl = base + "/models";
                        systemLogService.debug("重试备用端点: " + fallbackUrl, "SYSTEM");
                        try {
                            responseObj = restTemplate.exchange(fallbackUrl, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                        } catch (Exception ex) {
                            throw ex;
                        }
                    } else {
                        throw e;
                    }
                }
                
                if (responseObj instanceof java.util.Map<?, ?> response) {
                    if (response.containsKey("data") && response.get("data") instanceof java.util.List<?> list) {
                        java.util.List<String> models = list.stream()
                                .map(m -> (String) ((java.util.Map<?, ?>) m).get("id"))
                                .collect(java.util.stream.Collectors.toList());
                        systemLogService.endTimer("get_models", "模型列表获取成功", "SYSTEM");
                        systemLogService.info("获取到 " + models.size() + " 个OpenAI兼容模型", "SYSTEM");
                        return models;
                    }
                } else if (responseObj instanceof java.util.List<?> list) {
                    java.util.List<String> models = list.stream()
                            .map(m -> (String) ((java.util.Map<?, ?>) m).get("id"))
                            .collect(java.util.stream.Collectors.toList());
                    systemLogService.endTimer("get_models", "模型列表获取成功", "SYSTEM");
                    systemLogService.info("获取到 " + models.size() + " 个OpenAI兼容模型", "SYSTEM");
                    return models;
                }
                
                systemLogService.warn("模型列表解析失败，使用默认模型", "SYSTEM");
                return java.util.List.of("gpt-3.5-turbo");
            } else {
                String url = effectiveUrl + "/api/tags";
                systemLogService.debug("请求Ollama模型列表: " + url, "SYSTEM");
                
                java.util.Map<?, ?> response = restTemplate.getForObject(url, java.util.Map.class);
                if (response == null || !response.containsKey("models")) {
                    systemLogService.warn("Ollama返回空响应，使用默认模型", "SYSTEM");
                    return java.util.List.of("qwen3.5:4b");
                }
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<?, ?>> models = (java.util.List<java.util.Map<?, ?>>) response.get("models");
                java.util.List<String> modelNames = models.stream()
                        .map(m -> (String) m.get("name"))
                        .collect(java.util.stream.Collectors.toList());
                systemLogService.endTimer("get_models", "模型列表获取成功", "SYSTEM");
                systemLogService.info("获取到 " + modelNames.size() + " 个Ollama模型", "SYSTEM");
                return modelNames;
            }
        } catch (Exception e) {
            systemLogService.error("获取模型列表失败: " + e.getMessage(), "SYSTEM");
            return "openai".equalsIgnoreCase(source) ? java.util.List.of("gpt-3.5-turbo") : java.util.List.of("qwen3.5:4b");
        }
    }
}
