package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.config.AiConfig;
import com.lingshu.ai.core.service.*;
import com.lingshu.ai.core.tool.LocalTools;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import com.lingshu.ai.infrastructure.entity.ChatMessage;
import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
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
    private final ChatModel chatLanguageModel;
    private final List<ChatModelListener> listeners;
    private final TurnPostProcessingServiceImpl turnPostProcessingService;

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
                           ChatModel chatLanguageModel,
                           List<ChatModelListener> listeners,
                           TurnPostProcessingServiceImpl turnPostProcessingService) {
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
        this.chatLanguageModel = chatLanguageModel;
        this.listeners = listeners;
        this.turnPostProcessingService = turnPostProcessingService;
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
        ChatSession session = getOrCreateSession();
        AgentConfig agent = getAgent(agentId);

        systemLogService.info("\n收到用户消息: " + (message.length() > 20 ? message.substring(0, 20) + "..." : message), "CHAT");

        String longTermContext = memoryService.retrieveContext(userId, message);
        if (longTermContext != null && !longTermContext.isBlank()) {
            systemLogService.info("长期记忆检索完成，获取到相关事实。", "MEMORY");
        }

        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);

        String systemPrompt = promptBuilderService.buildMergedSystemPrompt(agent, relationshipPrompt, longTermContext);

        log.debug("Merged System Prompt generated for chat (first 100 chars): {}...", systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
        systemLogService.debug("已加载 Agent Prompt (长度: " + systemPrompt.length() + ")", "CHAT");

        com.lingshu.ai.infrastructure.entity.SystemSetting sysSetting = settingService.getSetting();
        String actualModel = sysSetting.getChatModel() != null ? sysSetting.getChatModel() : "default-model";
        String actualSource = sysSetting.getSource() != null ? sysSetting.getSource() : "ollama";
        systemLogService.llmStart(actualModel, actualSource, "LLM");

        AiServices<AiConfig.Assistant> builder =
                AiServices.builder(AiConfig.Assistant.class)
                        .chatModel(chatLanguageModel)
                        .chatMemoryProvider(chatMemoryProvider)
                        .toolArgumentsErrorHandler(this::handleToolArgumentsError)
                        .maxSequentialToolsInvocations(15);

        java.util.List<Object> enabledLocalTools = localTools.getEnabledTools(settingService.getLocalToolsSetting());
        if (!enabledLocalTools.isEmpty()) {
            builder.tools(enabledLocalTools.toArray());
        }

        List<McpClient> mcpClients = mcpService.getActiveClients();
        if (!mcpClients.isEmpty()) {
            builder.toolProvider(McpToolProvider.builder().mcpClients(mcpClients).build());
        }

        AiConfig.Assistant dynamicAssistant = builder.build();

        systemLogService.debug("准备发送对话请求，SystemPrompt 长度: " + systemPrompt.length(), "CHAT");

        String response = dynamicAssistant.chat(session.getId(), message, systemPrompt);
        systemLogService.llmEnd(response != null ? response.length() / 4 : 0, "LLM");

        postProcessAfterResponse(userId, message, response);

        return response;
    }

    private void postProcessAfterResponse(String userId, String userMessage, String assistantResponse) {
        try {
            turnPostProcessingService.processCompletedTurn(
                    userId,
                    userMessage,
                    assistantResponse != null ? assistantResponse : ""
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
        ChatSession session = getOrCreateSession();
        AgentConfig agent = getAgent(agentId);

        systemLogService.info("收到用户消息 (流式): " + (message.length() > 20 ? message.substring(0, 20) + "..." : message) + ", enableThinking=" + enableThinking, "CHAT");

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder assistantResponseStore = new StringBuilder();

        String longTermContext = memoryService.retrieveContext(userId, message);
        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);
        String systemPrompt = promptBuilderService.buildMergedSystemPrompt(agent, relationshipPrompt, longTermContext);

        log.debug("Merged System Prompt generated for streamChat (first 100 chars): {}...", systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
        systemLogService.debug("已加载 Agent Prompt (长度: " + systemPrompt.length() + ")", "CHAT");

        systemLogService.startTimer("llm_LLM");

        AiConfig.RawStreamingAssistant assistantToUse;
        AiServices<AiConfig.RawStreamingAssistant> builder;

        if (model != null && baseUrl != null) {
            StreamingChatModel tempModel;
            
            JdkHttpClientBuilder httpClientBuilder =
                    dev.langchain4j.http.client.jdk.JdkHttpClient.builder()
                            .httpClientBuilder(java.net.http.HttpClient.newBuilder()
                                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                                    .connectTimeout(Duration.ofSeconds(30))
                                    .executor(Executors.newCachedThreadPool()));
            
            if ("ollama".equalsIgnoreCase(baseUrl) || baseUrl.contains("11434")) {
                log.info("使用 Ollama 模式, baseUrl={}, model={}", baseUrl, model);
                tempModel = dev.langchain4j.model.ollama.OllamaStreamingChatModel.builder()
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
                var openAiBuilder = dev.langchain4j.model.openai.OpenAiStreamingChatModel.builder()
                        .baseUrl(effectiveUrl)
                        .apiKey(apiKey != null ? apiKey : "no-key")
                        .modelName(model)
                        .timeout(java.time.Duration.ofMinutes(5))
                        .listeners(listeners)
                        .httpClientBuilder(httpClientBuilder);
                
                if (Boolean.TRUE.equals(enableThinking)) {
                    openAiBuilder.returnThinking(true);
                    log.info("启用 Thinking/Reasoning 模式");
                }
                
                tempModel = openAiBuilder.build();
            }
            builder = AiServices.builder(AiConfig.RawStreamingAssistant.class)
                    .streamingChatModel(tempModel)
                    .chatMemoryProvider(chatMemoryProvider)
                    .toolArgumentsErrorHandler(this::handleToolArgumentsError)
                    .maxSequentialToolsInvocations(15);
        } else {
            builder = AiServices.builder(AiConfig.RawStreamingAssistant.class)
                    .streamingChatModel(streamingChatLanguageModel)
                    .chatMemoryProvider(chatMemoryProvider)
                    .toolArgumentsErrorHandler(this::handleToolArgumentsError)
                    .maxSequentialToolsInvocations(15);
        }

        java.util.List<Object> enabledLocalTools = localTools.getEnabledTools(settingService.getLocalToolsSetting());
        if (!enabledLocalTools.isEmpty()) {
            builder.tools(enabledLocalTools.toArray());
        }

        List<McpClient> mcpClients = mcpService.getActiveClients();
        if (!mcpClients.isEmpty()) {
            builder.toolProvider(McpToolProvider.builder().mcpClients(mcpClients).build());
        }
        assistantToUse = builder.build();

        systemLogService.debug("准备发送流式对话请求，SystemPrompt 长度: " + systemPrompt.length(), "CHAT");

        assistantToUse.chat(session.getId(), message, systemPrompt)
                .onPartialThinking(thinking -> {
                    systemLogService.thinking(thinking.text(), "LLM");
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
                    if (assistantResponseStore.length() == 0) {
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
                    postProcessAfterResponse(userId, message, assistantResponseStore.toString());
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
                        systemLogService.endTimer("get_models", "模型列表获取成功", "SYSTEM");
                        systemLogService.info("获取到 " + models.size() + " 个OpenAI兼容模型", "SYSTEM");
                        return models;
                    }
                } else if (responseObj instanceof java.util.List<?> list) {
                    List<String> models = list.stream()
                            .map(m -> (String) ((java.util.Map<?, ?>) m).get("id"))
                            .collect(java.util.stream.Collectors.toList());
                    systemLogService.endTimer("get_models", "模型列表获取成功", "SYSTEM");
                    systemLogService.info("获取到 " + models.size() + " 个OpenAI兼容模型", "SYSTEM");
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
                systemLogService.endTimer("get_models", "模型列表获取成功", "SYSTEM");
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
