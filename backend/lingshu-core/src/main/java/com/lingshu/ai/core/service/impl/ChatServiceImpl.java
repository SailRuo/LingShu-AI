package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.config.AiConfig;
import com.lingshu.ai.core.service.*;
import com.lingshu.ai.core.tool.LocalTools;
import com.lingshu.ai.core.tool.SummarizingMcpToolProvider;
import com.lingshu.ai.core.dto.EmotionContextResult;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import com.lingshu.ai.infrastructure.entity.ChatMessage;
import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@Service
public class ChatServiceImpl implements ChatService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatServiceImpl.class);

    private final MemoryService memoryService;
    private final AgentConfigService agentConfigService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final StreamingChatModel streamingChatLanguageModel;
    private final RestTemplate restTemplate;
    private final SettingService settingService;
    private final SystemLogService systemLogService;
    private final AffinityService affinityService;
    private final PromptBuilderService promptBuilderService;
    private final ChatMemoryProvider chatMemoryProvider;
    private final LocalTools localTools;
    private final McpService mcpService;
    private final List<ChatModelListener> listeners;
    private final TurnPostProcessingServiceImpl turnPostProcessingService;
    private final ToolResultSummarizer toolResultSummarizer;
    private final EmotionPreAnalysisService emotionPreAnalysisService;

    @Value("${lingshu.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    public ChatServiceImpl(MemoryService memoryService,
                           AgentConfigService agentConfigService,
                           ChatSessionRepository sessionRepository,
                           ChatMessageRepository messageRepository,
                           StreamingChatModel streamingChatLanguageModel,
                           RestTemplate restTemplate,
                           SettingService settingService,
                           SystemLogService systemLogService,
                           AffinityService affinityService,
                           PromptBuilderService promptBuilderService,
                           ChatMemoryProvider chatMemoryProvider,
                           LocalTools localTools,
                           McpService mcpService,
                           List<ChatModelListener> listeners,
                           TurnPostProcessingServiceImpl turnPostProcessingService,
                           ToolResultSummarizer toolResultSummarizer,
                           EmotionPreAnalysisService emotionPreAnalysisService) {
        this.memoryService = memoryService;
        this.agentConfigService = agentConfigService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.restTemplate = restTemplate;
        this.settingService = settingService;
        this.systemLogService = systemLogService;
        this.affinityService = affinityService;
        this.promptBuilderService = promptBuilderService;
        this.chatMemoryProvider = chatMemoryProvider;
        this.localTools = localTools;
        this.mcpService = mcpService;
        this.listeners = listeners;
        this.turnPostProcessingService = turnPostProcessingService;
        this.toolResultSummarizer = toolResultSummarizer;
        this.emotionPreAnalysisService = emotionPreAnalysisService;
    }

    private ChatSession getOrCreateSession() {
        return sessionRepository.findAll().stream().findFirst().orElseGet(() -> {
            ChatSession session = ChatSession.builder()
                    .title("Default Conversation")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
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

    private void postProcessAfterResponse(String userId, String userMessage, String assistantResponse,
                                          com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion) {
        try {
            turnPostProcessingService.processCompletedTurn(
                    userId,
                    userMessage,
                    assistantResponse != null ? assistantResponse : "",
                    preAnalyzedEmotion
            );
        } catch (Exception e) {
            log.warn("提交回合后处理任务失败: {}", e.getMessage(), e);
            try {
                affinityService.recordInteraction(userId);
            } catch (Exception ex) {
                log.warn("记录互动失败: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(message, null, "User", null, null, null);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId) {
        return streamChat(message, agentId, "User", null, null, null);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId) {
        return streamChat(message, agentId, userId, null, null, null);
    }

    @Override
    public Flux<String> streamChat(String message, String model, String apiKey, String baseUrl) {
        return streamChat(message, null, "User", model, apiKey, baseUrl, null);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String model, String apiKey, String baseUrl) {
        return streamChat(message, agentId, "User", model, apiKey, baseUrl, null);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl) {
        return streamChat(message, agentId, userId, model, apiKey, baseUrl, null);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl,
                                   ToolEventListener toolEventListener) {
        return streamChat(message, agentId, userId, model, apiKey, baseUrl, false, toolEventListener);
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl,
                                   Boolean enableThinking, ToolEventListener toolEventListener) {
        return streamChat(message, null, agentId, userId, model, apiKey, baseUrl, enableThinking, toolEventListener);
    }

    @Override
    public Flux<String> streamChat(String message, List<String> images, Long agentId, String userId, String model, String apiKey, String baseUrl,
                                   Boolean enableThinking, ToolEventListener toolEventListener) {
        ChatSession session = getOrCreateSession();
        AgentConfig agent = getAgent(agentId);
        
        if (agent != null) {
            log.info("处理聊天消息: agentId={}, 使用智能体: id={}, name={}, systemPrompt长度={}", 
                    agentId, agent.getId(), agent.getName(), 
                    agent.getSystemPrompt() != null ? agent.getSystemPrompt().length() : 0);
        } else {
            log.warn("处理聊天消息: agentId={}, 未找到智能体", agentId);
        }
        
        String safeMessage = message != null ? message : "";

        systemLogService.info("收到用户消息 (流式): " + (safeMessage.length() > 20 ? safeMessage.substring(0, 20) + "..." : safeMessage) + ", enableThinking=" + enableThinking + ", images=" + (images != null ? images.size() : 0), "CHAT");

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder assistantResponseStore = new StringBuilder();

        com.lingshu.ai.core.dto.EmotionContextResult emotionResult = emotionPreAnalysisService.analyzeBeforeResponse(userId, safeMessage, session.getId());
        com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion = emotionResult != null ? emotionResult.toEmotionAnalysis() : null;
        String emotionPrompt = "";
        if (emotionResult != null) {
            emotionPrompt = emotionPreAnalysisService.getEmotionPromptInjection(userId);
            String abbreviated = emotionPrompt.length() > 100 ? emotionPrompt.substring(0, 100) + "..." : emotionPrompt;
            systemLogService.info("情感前置分析完成，已注入情感状态到 Prompt: " + abbreviated, "EMOTION");
        }

        String longTermContext = memoryService.retrieveContext(userId, safeMessage);
        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);
        String systemPrompt = promptBuilderService.buildMergedSystemPrompt(agent, relationshipPrompt, longTermContext);
        
        if (!emotionPrompt.isEmpty()) {
            systemPrompt = systemPrompt + emotionPrompt;
        }

        log.debug("Merged System Prompt generated for streamChat (first 100 chars): {}...", systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
        systemLogService.debug("已加载 Agent Prompt (长度: " + systemPrompt.length() + ")", "CHAT");

        com.lingshu.ai.infrastructure.entity.SystemSetting sysSetting = settingService.getSetting();
        String actualModel = sysSetting.getChatModel() != null ? sysSetting.getChatModel() : "default-model";
        String actualSource = sysSetting.getSource() != null ? sysSetting.getSource() : "ollama";
        systemLogService.llmStart(actualModel, actualSource, "LLM");

        StreamingChatModel streamingModelToUse = streamingChatLanguageModel;

        if (model != null && baseUrl != null) {
            JdkHttpClientBuilder httpClientBuilder =
                    dev.langchain4j.http.client.jdk.JdkHttpClient.builder()
                            .httpClientBuilder(java.net.http.HttpClient.newBuilder()
                                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                                    .connectTimeout(Duration.ofSeconds(30))
                                    .executor(Executors.newCachedThreadPool()));
            
            if ("ollama".equalsIgnoreCase(baseUrl) || baseUrl.contains("11434")) {
                log.info("使用 Ollama 模式, baseUrl={}, model={}", baseUrl, model);
                streamingModelToUse = dev.langchain4j.model.ollama.OllamaStreamingChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(model)
                        .timeout(java.time.Duration.ofMinutes(5))
                        .listeners(listeners)
                        .build();
            } else {
                String effectiveUrl = baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")
                        ? baseUrl
                        : baseUrl + (baseUrl.endsWith("/") ? "v1" : "/v1");
                log.info("使用 OpenAI 兼容模式, effectiveUrl={}, model={}, apiKey={}, enableThinking={}", effectiveUrl, model, apiKey != null ? "***" : "null", enableThinking);
                var openAiBuilder = OpenAiStreamingChatModel.builder()
                        .baseUrl(effectiveUrl)
                        .apiKey(apiKey != null ? apiKey : "no-key")
                        .modelName(model)
                        .timeout(java.time.Duration.ofMinutes(5))
                        .listeners(listeners)
                        .httpClientBuilder(httpClientBuilder);
                
                boolean isGemini = model != null && model.toLowerCase().contains("gemini");
                if (Boolean.TRUE.equals(enableThinking)) {
                    if (isGemini) {
                        log.warn("检测到 Gemini 模型 [{}]，切换到普通模式（由于当前版本暂不支持推理模式下的工具调用）。", model);
                    } else {
                        openAiBuilder.returnThinking(true);
                        log.info("启用 Thinking/Reasoning 模式: [{}]", model);
                    }
                }
                
                streamingModelToUse = openAiBuilder.build();
            }
        }

        systemLogService.debug("准备发送流式对话请求，SystemPrompt 长度: " + systemPrompt.length(), "CHAT");

        // 构建多模态 UserMessage
        List<Content> contents = new ArrayList<>();
        if (!safeMessage.isBlank()) {
            contents.add(TextContent.from(safeMessage));
        }
        if (images != null && !images.isEmpty()) {
            for (String base64Image : images) {
                String compressed = com.lingshu.ai.core.util.ImageCompressor.compressToBase64(base64Image);
                String base64Data = extractBase64Data(compressed);
                String mimeType = extractMimeType(compressed);
                contents.add(ImageContent.from(base64Data, mimeType));
            }
        }
        
        // 如果既没有文本也没有图片，添加一个默认文本避免报错
        if (contents.isEmpty()) {
            contents.add(TextContent.from(" "));
        }

        UserMessage userMessageObj = UserMessage.from(contents);

        if (images != null && !images.isEmpty()) {
            streamMultimodalRequest(streamingModelToUse, session.getId(), systemPrompt, userMessageObj,
                    userId, safeMessage, assistantResponseStore, sink, preAnalyzedEmotion);
            return sink.asFlux();
        }

        AiServices<AiConfig.StreamingAssistant> builder = AiServices.builder(AiConfig.StreamingAssistant.class)
                .streamingChatModel(streamingModelToUse)
                .chatMemoryProvider(chatMemoryProvider)
                .toolArgumentsErrorHandler(this::handleToolArgumentsError)
                .maxSequentialToolsInvocations(15);

        List<Object> enabledLocalTools = localTools.getEnabledTools(settingService.getLocalToolsSetting());
        if (!enabledLocalTools.isEmpty()) {
            builder.tools(enabledLocalTools.toArray());
        }

        List<McpClient> mcpClients = mcpService.getActiveClients();
        if (!mcpClients.isEmpty()) {
            String userIntent = safeMessage;
            builder.toolProvider(new SummarizingMcpToolProvider(
                    mcpClients, toolResultSummarizer, () -> userIntent));
        }

        builder.build()
                .chat(session.getId(), safeMessage.isBlank() ? " " : safeMessage, systemPrompt)
            .onPartialThinking(thinking -> {
                //systemLogService.thinking(thinking.text(), "LLM");
                sink.tryEmitNext("\u0001REASONING\u0001" + thinking.text() + "\u0001/REASONING\u0001");
            })
            .beforeToolExecution(beforeToolExecution -> {
                if (toolEventListener == null) {
                    return;
                }
                var request = beforeToolExecution.request();
                toolEventListener.onToolStart(request.id(), request.name(), request.arguments());
            })
            .onPartialResponse(token -> {
                if (assistantResponseStore.isEmpty()) {
                    systemLogService.info("流式输出已开启，接收首个 token...", "LLM");
                }
                assistantResponseStore.append(token);
                sink.tryEmitNext(token);
            })
            .onToolExecuted(toolExecution -> {
                if (toolEventListener == null) {
                    return;
                }
                var request = toolExecution.request();
                toolEventListener.onToolEnd(
                        request.id(),
                        request.name(),
                        request.arguments(),
                        toolExecution.result(),
                        toolExecution.hasFailed()
                );
            })
            .onCompleteResponse(response -> {
                int tokenCount = assistantResponseStore.length() / 4;
                systemLogService.llmEnd(tokenCount, "LLM");
                systemLogService.success("对话完成，回复长度: " + assistantResponseStore.length() + " 字符", "CHAT");
                sink.tryEmitComplete();
                postProcessAfterResponse(userId, safeMessage, assistantResponseStore.toString(), preAnalyzedEmotion);
            })
            .onError(error -> {
                log.error("流式对话发生错误: {}", error.getMessage(), error);
                systemLogService.error("LLM 调用失败: " + error.getMessage(), "LLM");

                String errorMsg = error.getMessage() != null ? error.getMessage() : "";

                if (errorMsg.contains("context length") || errorMsg.contains("n_ctx") || errorMsg.contains("n_keep")) {
                    sink.tryEmitError(new RuntimeException("输入内容过长，超出模型上下文限制。请尝试：\n1. 减少图片数量或使用更小的图片\n2. 清除对话历史后重试\n3. 切换到支持更长上下文的模型"));
                } else if (errorMsg.contains("image") || errorMsg.contains("vision") || errorMsg.contains("multimodal")) {
                    sink.tryEmitError(new RuntimeException("当前模型不支持图像识别，请切换到支持视觉的模型（如 Qwen-VL 等）或移除图片后重试。"));
                } else {
                    sink.tryEmitError(error);
                }
            })
            .start();

        return sink.asFlux();
    }

    private void streamMultimodalRequest(StreamingChatModel streamingModel,
                                         Long sessionId,
                                         String systemPrompt,
                                         UserMessage userMessage,
                                         String userId,
                                         String safeMessage,
                                         StringBuilder assistantResponseStore,
                                         Sinks.Many<String> sink,
                                         com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion) {
        ChatMemory chatMemory = chatMemoryProvider.get(sessionId);
        
        chatMemory.add(userMessage);

        List<dev.langchain4j.data.message.ChatMessage> messagesToSend = new ArrayList<>();
        messagesToSend.add(SystemMessage.from(systemPrompt));
        messagesToSend.addAll(chatMemory.messages());

        ChatRequest request = ChatRequest.builder()
                .messages(messagesToSend)
                .build();

        streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handleStreamingToken(partialResponse, assistantResponseStore, sink);
            }

            @Override
            public void onPartialThinking(dev.langchain4j.model.chat.response.PartialThinking partialThinking) {
                systemLogService.thinking(partialThinking.text(), "LLM");
                sink.tryEmitNext("\u0001REASONING\u0001" + partialThinking.text() + "\u0001/REASONING\u0001");
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                if (response != null && response.aiMessage() != null) {
                    chatMemory.add(response.aiMessage());
                }
                completeStreamingResponse(userId, safeMessage, assistantResponseStore, sink, preAnalyzedEmotion);
            }

            @Override
            public void onError(Throwable error) {
                handleStreamingError(error, sink);
            }
        });
    }

    private void handleStreamingToken(String token,
                                      StringBuilder assistantResponseStore,
                                      Sinks.Many<String> sink) {
        if (assistantResponseStore.isEmpty()) {
            systemLogService.info("流式输出已开启，接收首个 token...", "LLM");
        }
        assistantResponseStore.append(token);
        sink.tryEmitNext(token);
    }

    private void completeStreamingResponse(String userId,
                                           String safeMessage,
                                           StringBuilder assistantResponseStore,
                                           Sinks.Many<String> sink,
                                           com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion) {
        int tokenCount = assistantResponseStore.length() / 4;
        systemLogService.llmEnd(tokenCount, "LLM");
        systemLogService.success("对话完成，回复长度: " + assistantResponseStore.length() + " 字符", "CHAT");
        sink.tryEmitComplete();
        postProcessAfterResponse(userId, safeMessage, assistantResponseStore.toString(), preAnalyzedEmotion);
    }

    private void handleStreamingError(Throwable error, Sinks.Many<String> sink) {
        log.error("流式对话发生错误: {}", error.getMessage(), error);
        systemLogService.error("LLM 调用失败: " + error.getMessage(), "LLM");

        String errorMsg = error.getMessage() != null ? error.getMessage() : "";
        if (errorMsg.contains("context length") || errorMsg.contains("n_ctx") || errorMsg.contains("n_keep")) {
            sink.tryEmitError(new RuntimeException("输入内容过长，超出模型上下文限制。请尝试：\n1. 减少图片数量或使用更小的图片\n2. 清除对话历史后重试\n3. 切换到支持更长上下文的模型"));
        } else if (errorMsg.contains("image") || errorMsg.contains("vision") || errorMsg.contains("multimodal")) {
            sink.tryEmitError(new RuntimeException("当前模型不支持图像识别，请切换到支持视觉的模型（如 Qwen-VL 等）或移除图片后重试。"));
        } else {
            sink.tryEmitError(error);
        }
    }

    private String extractBase64Data(String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return "";
        }
        return base64Image.contains(",") ? base64Image.split(",", 2)[1] : base64Image;
    }

    private String extractMimeType(String base64Image) {
        if (base64Image != null && base64Image.startsWith("data:") && base64Image.contains(";")) {
            return base64Image.substring(5, base64Image.indexOf(';'));
        }
        return "image/jpeg";
    }

    @Override
    public Flux<String> streamWelcome() {
        return streamWelcome("User");
    }

    @Override
    public Flux<String> streamWelcome(String userId) {
        ChatSession session = getOrCreateSession();
        systemLogService.info("生成欢迎消息...", "CHAT");

        AgentConfig defaultAgent = agentConfigService.getDefaultAgent().orElse(null);
        String memoryContext = memoryService.retrieveContext(userId, "用户身份与基本事实");
        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);
        String systemPrompt = promptBuilderService.buildMergedSystemPrompt(defaultAgent, relationshipPrompt, memoryContext);

        systemLogService.startTimer("llm_welcome");

        AiConfig.PlainStreamingAssistant rawAssistant =
                AiServices.builder(AiConfig.PlainStreamingAssistant.class)
                        .streamingChatModel(streamingChatLanguageModel)
                        .chatMemoryProvider(chatMemoryProvider)
                        .systemMessageProvider(memoryId -> systemPrompt)
                        .build();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder welcomeBuilder = new StringBuilder();

        rawAssistant.chat(session.getId(), "请开始我们的对话")
                .onPartialResponse(token -> {
                    welcomeBuilder.append(token);
                    sink.tryEmitNext(token);
                })
                .onCompleteResponse(response -> {
                    log.info("欢迎语生成完成，正在手动保存到数据库...");
                    ChatMessage welcomeMsg =
                            ChatMessage.builder()
                                    .session(session)
                                    .role("assistant")
                                    .content(welcomeBuilder.toString())
                                    .createdAt(LocalDateTime.now())
                                    .build();
                    messageRepository.save(welcomeMsg);
                    sink.tryEmitComplete();
                })
                .onError(err -> {
                    sink.tryEmitNext("欢迎回来。系统已就绪，随时准备与你共同探索。");
                    sink.tryEmitComplete();
                })
                .start();

        return sink.asFlux();
    }

    @Override
    public List<String> getModels(String source, String baseUrl, String apiKey) {
        String effectiveUrl = (baseUrl == null || baseUrl.isBlank()) ? this.baseUrl : baseUrl;
        if (effectiveUrl.endsWith("/")) {
            effectiveUrl = effectiveUrl.substring(0, effectiveUrl.length() - 1);
        }

        systemLogService.startTimer("get_models");

        try {
            if ("openai".equalsIgnoreCase(source)) {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.set("Authorization", "Bearer " + apiKey);
                }
                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                String base = effectiveUrl;
                if (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }

                String url = (base.contains("/v1") || base.endsWith("/v1")) ? base + "/models" : base + "/v1/models";

                systemLogService.debug("请求OpenAI模型列表: " + url, "SYSTEM");

                Object responseObj;
                try {
                    responseObj = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                } catch (Exception e) {
                    if (!base.contains("/v1")) {
                        String fallbackUrl = base + "/models";
                        systemLogService.debug("重试备用端点: " + fallbackUrl, "SYSTEM");
                        responseObj = restTemplate.exchange(fallbackUrl, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                    } else {
                        throw e;
                    }
                }

                if (responseObj instanceof java.util.Map<?, ?> response) {
                    if (response.containsKey("data") && response.get("data") instanceof java.util.List<?> list) {
                        List<String> models = list.stream()
                                .map(m -> (String) ((java.util.Map<?, ?>) m).get("id"))
                                .collect(java.util.stream.Collectors.toList());
                        //log.debug("获取到 " + models.size() + " 个OpenAI兼容模型");
                        return models;
                    }
                } else if (responseObj instanceof java.util.List<?> list) {
                    List<String> models = list.stream()
                            .map(m -> (String) ((java.util.Map<?, ?>) m).get("id"))
                            .collect(java.util.stream.Collectors.toList());
                    //log.debug("获取到 " + models.size() + " 个OpenAI兼容模型");
                    return models;
                }

                systemLogService.warn("模型列表解析失败，使用默认模型", "SYSTEM");
                return List.of("gpt-3.5-turbo");
            } else {
                String url = effectiveUrl + "/api/tags";
                systemLogService.debug("请求Ollama模型列表: " + url, "SYSTEM");

                java.util.Map<?, ?> response = restTemplate.getForObject(url, java.util.Map.class);
                if (response == null || !response.containsKey("models")) {
                    systemLogService.warn("Ollama返回空响应，使用默认模型", "SYSTEM");
                    return java.util.List.of("qwen3.5:4b");
                }

                @SuppressWarnings("unchecked")
                List<java.util.Map<?, ?>> models = (List<java.util.Map<?, ?>>) response.get("models");
                List<String> modelNames = models.stream()
                        .map(m -> (String) m.get("name"))
                        .collect(java.util.stream.Collectors.toList());
                systemLogService.info("获取到 " + modelNames.size() + " 个Ollama模型", "SYSTEM");
                return modelNames;
            }
        } catch (Exception e) {
            systemLogService.error("获取模型列表失败: " + e.getMessage(), "SYSTEM");
            return "openai".equalsIgnoreCase(source) ? List.of("gpt-3.5-turbo") : List.of("qwen3.5:4b");
        }
    }

    @Override
    public void clearHistory(Long sessionId) {
        Long idToClear = sessionId;
        if (idToClear == null) {
            ChatSession session = getOrCreateSession();
            idToClear = session.getId();
        }
        
        // 1. 从数据库物理删除记录
        messageRepository.deleteBySessionId(idToClear);
        
        // 2. 清除 LangChain4j 的 ChatMemory 缓存（这会触发 store.deleteMessages）
        chatMemoryProvider.get(idToClear).clear();
        
        systemLogService.info("已清空会话历史记录: sessionId=" + idToClear, "CHAT");
    }

    private ToolErrorHandlerResult handleToolArgumentsError(Throwable error, ToolErrorContext errorContext) {

        String toolName = errorContext != null && errorContext.toolExecutionRequest() != null
                ? errorContext.toolExecutionRequest().name()
                : "unknown";
        String rawArguments = errorContext != null && errorContext.toolExecutionRequest() != null
                ? errorContext.toolExecutionRequest().arguments()
                : "";

        String message = buildToolArgumentsRepairMessage(toolName, rawArguments, error);
        systemLogService.warn(
                "工具参数解析失败: tool=" + toolName
                        + " | raw=" + previewArguments(rawArguments)
                        + " | error=" + (error != null ? error.getMessage() : "unknown"),
                "TOOL"
        );
        return ToolErrorHandlerResult.text(message);
    }

    private String buildToolArgumentsRepairMessage(String toolName, String rawArguments, Throwable error) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tool arguments are invalid JSON and could not be parsed.")
                .append(" Retry the same tool call with valid JSON only. ");

        if ("executeCommand".equals(toolName)) {
            builder.append("\n\nCRITICAL: Your JSON has UNESCAPED DOUBLE QUOTES inside the command string!\n")
                    .append("WRONG: {\"command\": \"powershell -Command \"Get-Date\"\"}  <- inner quotes NOT escaped!\n")
                    .append("RIGHT: {\"command\": \"powershell -Command \\\"Get-Date\\\"\"}  <- inner quotes escaped as \\\"\n\n")
                    .append("Rules:\n")
                    .append("1. The command value must be a single JSON string.\n")
                    .append("2. Every double quote INSIDE the command must be escaped as backslash-backslash-quote (\\\\\\\").\n")
                    .append("3. Prefer single quotes in PowerShell commands to avoid escaping: 'Get-Date' instead of \\\"Get-Date\\\".\n")
                    .append("4. Example: {\"command\":\"powershell -NoProfile -Command \\\"Get-Date -Format 'yyyy-MM-dd'\\\"\"}\n");
        }

        if (rawArguments != null && !rawArguments.isBlank()) {
            builder.append("\nYour previous (invalid) arguments: ").append(previewArguments(rawArguments));
        }

        if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
            builder.append("\nParser error: ").append(error.getMessage());
        }

        return builder.toString().trim();
    }

    private String previewArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return "";
        }
        String normalized = rawArguments.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }
}
