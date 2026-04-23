package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.config.AiConfig;
import com.lingshu.ai.core.service.*;
import com.lingshu.ai.core.tool.*;
import com.lingshu.ai.core.util.SkillNameResolver;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import dev.langchain4j.data.message.*;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skills;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map; // 添加此行
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors; // 添加此行
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatServiceImpl.class);

    private final MemoryService memoryService;
    private final AgentConfigService agentConfigService;
    private final ChatSessionService chatSessionService;
    private final ChatSessionRepository sessionRepository;
    private final StreamingChatModel streamingChatLanguageModel;
    private final RestTemplate restTemplate;
    private final SettingService settingService;
    private final SystemLogService systemLogService;
    private final AffinityService affinityService;
    private final PromptBuilderService promptBuilderService;
    private final ChatMemoryProvider chatMemoryProvider;
    private final McpService mcpService;
    private final List<ChatModelListener> listeners;
    private final TurnPostProcessingServiceImpl turnPostProcessingService;
    private final ToolResultSummarizer toolResultSummarizer;
    private final TurnTimelineService turnTimelineService;
    private final McpToolArtifactRegistry mcpToolArtifactRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lingshu.ollama.base-url:http://localhost:11434}")
    private String baseUrl;
    private final ExecutorService customModelHttpExecutor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors())
    );



    private ChatSession getOrCreateSession(String userId, Long sessionId) {
        Long resolvedSessionId = chatSessionService.resolveSessionId(userId, sessionId);
        return sessionRepository.findById(resolvedSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + resolvedSessionId));
    }

    private AgentConfig getAgent(Long agentId) {
        if (agentId != null) {
            return agentConfigService.getAgentById(agentId).orElse(null);
        }
        return agentConfigService.getDefaultAgent().orElse(null);
    }

    private void postProcessAfterResponse(String userId, Long sessionId, String userMessage, String assistantResponse,
                                          com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion) {
        try {
            turnPostProcessingService.processCompletedTurn(
                    userId,
                    sessionId,
                    userMessage,
                    assistantResponse != null ? assistantResponse : "",
                    preAnalyzedEmotion
            );
        } catch (Exception e) {
            log.warn("提交回合后处理任务失败 {}", e.getMessage(), e);
            try {
                affinityService.recordInteraction(userId);
            } catch (Exception ex) {
                log.warn("记录互动失败 {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .userId("User")
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .agentId(agentId)
                .userId("User")
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .agentId(agentId)
                .userId(userId)
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, String model, String apiKey, String baseUrl) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .userId("User")
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String model, String apiKey, String baseUrl) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .agentId(agentId)
                .userId("User")
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .agentId(agentId)
                .userId(userId)
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl,
                                   ToolEventListener toolEventListener) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .agentId(agentId)
                .userId(userId)
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .enableThinking(false)
                .toolEventListener(toolEventListener)
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, Long agentId, String userId, String model, String apiKey, String baseUrl,
                                   Boolean enableThinking, ToolEventListener toolEventListener) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .agentId(agentId)
                .userId(userId)
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .enableThinking(enableThinking)
                .toolEventListener(toolEventListener)
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, List<String> images, Long agentId, String userId, String model, String apiKey, String baseUrl,
                                   Boolean enableThinking, ToolEventListener toolEventListener) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .images(images)
                .agentId(agentId)
                .userId(userId)
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .enableThinking(enableThinking)
                .toolEventListener(toolEventListener)
                .build());
    }

    @Override
    public Flux<String> streamChat(String message, List<String> images, Long sessionId, Long agentId, String userId, String model, String apiKey, String baseUrl,
                                   Boolean enableThinking, ToolEventListener toolEventListener) {
        return streamChat(ChatService.ChatStreamRequest.builder()
                .message(message)
                .images(images)
                .sessionId(sessionId)
                .agentId(agentId)
                .userId(userId)
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .enableThinking(enableThinking)
                .toolEventListener(toolEventListener)
                .build());
    }

    @Override
    public Flux<String> streamChat(ChatService.ChatStreamRequest chatRequest) {
        String userId = chatRequest != null ? chatRequest.effectiveUserId() : "User";
        List<String> images = chatRequest != null ? chatRequest.images() : null;
        Long sessionId = chatRequest != null ? chatRequest.sessionId() : null;
        Long agentId = chatRequest != null ? chatRequest.agentId() : null;
        String model = chatRequest != null ? chatRequest.model() : null;
        String apiKey = chatRequest != null ? chatRequest.apiKey() : null;
        String baseUrl = chatRequest != null ? chatRequest.baseUrl() : null;
        Boolean enableThinking = chatRequest != null ? chatRequest.enableThinking() : null;
        ToolEventListener toolEventListener = chatRequest != null ? chatRequest.toolEventListener() : null;
        String message = chatRequest != null ? chatRequest.message() : null;

        ChatSession session = getOrCreateSession(userId, sessionId);
        chatSessionService.touchSession(session.getId());
        AgentConfig agent = getAgent(agentId);

        if (agent != null) {
            log.info("处理聊天消息: 智能体name={}, systemPrompt长度={}",
                    agent.getName(),
                    agent.getSystemPrompt() != null ? agent.getSystemPrompt().length() : 0);
        } else {
            log.warn("处理聊天消息: agentId={}, 未找到智能体", agentId);
        }

        String safeMessage = message != null ? message : "";

        systemLogService.info("收到用户消息 (流式): " + (safeMessage.length() > 20 ? safeMessage.substring(0, 20) + "..." : safeMessage) + ", enableThinking=" + enableThinking + ", images=" + (images != null ? images.size() : 0), "CHAT");

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder assistantResponseStore = new StringBuilder();
        StringBuilder pendingAssistantText = new StringBuilder();
        Long turnId = turnTimelineService.startTurn(session.getId(), safeMessage, images == null ? List.of() : images);

        // 情感前置分析已禁用，使用情感微调后的 LLM
        // com.lingshu.ai.core.dto.EmotionContextResult emotionResult = emotionPreAnalysisService.analyzeBeforeResponse(userId, safeMessage, session.getId());
        // com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion = emotionResult != null ? emotionResult.toEmotionAnalysis() : null;
        com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion = null;
        String emotionPrompt = "";
        String longTermContext = memoryService.retrieveContext(userId, safeMessage);
        String relationshipPrompt = affinityService.getRelationshipPrompt(userId);
        
        String systemPrompt = promptBuilderService.buildMergedSystemPrompt(agent, relationshipPrompt, longTermContext);

        if (!emotionPrompt.isEmpty()) {
            systemPrompt = systemPrompt + emotionPrompt;
        }

        log.debug("合并后的系统提示: \n{}...", systemPrompt);
        systemLogService.debug("已添加Agent Prompt (长度: " + systemPrompt.length() + ")", "CHAT");

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
                                    .executor(customModelHttpExecutor));

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
                        log.warn("检测到 Gemini 模型 [{}]，切换到普通模式（因为当前版本不支持流式输出中的工具调用）", model);
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

        // 如果既没有文字也没有图片，添加一个默认文字避免报错
        if (contents.isEmpty()) {
            contents.add(TextContent.from(" "));
        }

        UserMessage userMessageObj = UserMessage.from(contents);

        if (images != null && !images.isEmpty()) {
            streamMultimodalRequest(streamingModelToUse, session.getId(), systemPrompt, userMessageObj,
                    userId, safeMessage, assistantResponseStore, sink, preAnalyzedEmotion, turnId);
            return sink.asFlux();
        }

        AiServices<AiConfig.StreamingAssistant> builder = AiServices.builder(AiConfig.StreamingAssistant.class)
                .streamingChatModel(streamingModelToUse)
                .chatMemoryProvider(chatMemoryProvider)
                .hallucinatedToolNameStrategy(this::handleHallucinatedToolName)
                .toolArgumentsErrorHandler(this::handleToolArgumentsError)
                .maxSequentialToolsInvocations(30);

        List<RawMcpClient> mcpClients = mcpService.getActiveClients();
        String userIntent = safeMessage;

        // 加载 Skills
        Path skillsPath = resolveSkillsPath();
        Skills loadedSkills = null;
        Set<String> requiredBuiltinTools = new LinkedHashSet<>();
        try {
            if (java.nio.file.Files.exists(skillsPath)) {
                List<FileSystemSkill> fsSkills = FileSystemSkillLoader.loadSkills(skillsPath);
                if (!fsSkills.isEmpty()) {
                    // 根据 skill name 去重，后加载的覆盖先加载的
                    Map<String, FileSystemSkill> uniqueSkills = fsSkills.stream()
                            .collect(Collectors.toMap(
                                    FileSystemSkill::name,
                                    skill -> skill,
                                    (existing, replacement) -> replacement // 保留后加载的
                            ));

                    loadedSkills = Skills.from(new ArrayList<>(uniqueSkills.values()));
                    log.info("已加载 Skills: count={}, unique_count={}, path={}",
                            fsSkills.size(), uniqueSkills.size(), skillsPath);

                    for (FileSystemSkill skill : uniqueSkills.values()) {
                        requiredBuiltinTools.addAll(SkillToolManifest.parseRequiredTools(skill.basePath()));
                    }
                } else {
                    log.warn("Skills 目录存在但未加载到任何技能: {}", skillsPath);
                }
            } else {
                log.warn("未找到 Skills 目录: {}", skillsPath);
            }
        } catch (Exception e) {
            log.error("加载 Skills 失败: {}", e.getMessage());
        }

        List<ToolProvider> toolProviders = new ArrayList<>();
        if (!mcpClients.isEmpty()) {
            toolProviders.add(new SafeMcpToolProvider(
                    mcpClients, toolResultSummarizer, () -> userIntent, chatMemoryProvider, mcpToolArtifactRegistry));
        }
        if (loadedSkills != null) {
            toolProviders.add(loadedSkills.toolProvider());
        }

        Set<String> enabledBuiltinTools = new LinkedHashSet<>(requiredBuiltinTools);
        enabledBuiltinTools.add("execute_command");
        toolProviders.add(new BuiltinWorkspaceToolProvider(
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize(),
                enabledBuiltinTools));

        // 官方推荐：把多个动态 ToolProvider 直接交给 AiServices 统一合并
        builder.toolProviders(toolProviders);

        ToolEventListener timelineToolListener = new ToolEventListener() {
            @Override
            public void onToolStart(String toolCallId, String toolName, String arguments) {
                String skillName = SkillNameResolver.resolve(toolName, arguments, objectMapper);
                turnTimelineService.recordToolStart(turnId, toolCallId, toolName, skillName, arguments);
                if (toolEventListener != null) {
                    toolEventListener.onToolStart(toolCallId, toolName, arguments);
                }
            }

            @Override
            public void onToolEnd(String toolCallId, String toolName, String arguments, String result, boolean isError) {
                String skillName = SkillNameResolver.resolve(toolName, arguments, objectMapper);
                List<TurnTimelineService.ArtifactPayload> artifacts = mcpToolArtifactRegistry.pop(toolCallId).stream()
                        .map(artifact -> new TurnTimelineService.ArtifactPayload(
                                artifact.artifactType(),
                                artifact.mimeType(),
                                artifact.url(),
                                artifact.base64Data()
                        ))
                        .toList();
                turnTimelineService.recordToolEnd(turnId, toolCallId, toolName, skillName, arguments, result, isError, artifacts);
                if (toolEventListener != null) {
                    toolEventListener.onToolEnd(toolCallId, toolName, arguments, result, isError, artifacts);
                }
            }
        };

        builder.build()
                .chat(session.getId(), safeMessage.isBlank() ? " " : safeMessage, systemPrompt)
                .onPartialThinking(thinking -> {
                    //systemLogService.thinking(thinking.text(), "LLM");
                    sink.tryEmitNext("\u0001REASONING\u0001" + thinking.text() + "\u0001/REASONING\u0001");
                })
                .beforeToolExecution(beforeToolExecution -> {
                    var request = beforeToolExecution.request();
                    flushPendingAssistantText(turnId, pendingAssistantText);
                    timelineToolListener.onToolStart(request.id(), request.name(), request.arguments());
                })
                .onPartialResponse(token -> {
                    if (assistantResponseStore.isEmpty()) {
                        systemLogService.info("流式输出已开启，接收首个 token...", "LLM");
                    }
                    assistantResponseStore.append(token);
                    pendingAssistantText.append(token);
                    sink.tryEmitNext(token);
                })
                .onToolExecuted(toolExecution -> {
                    var request = toolExecution.request();
                    timelineToolListener.onToolEnd(
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
                    flushPendingAssistantText(turnId, pendingAssistantText);
                    turnTimelineService.completeTurn(turnId, assistantResponseStore.toString());
                    sink.tryEmitComplete();
                    postProcessAfterResponse(userId, session.getId(), safeMessage, assistantResponseStore.toString(), preAnalyzedEmotion);
                })
                .onError(error -> {
                    log.error("流式对话发生错误: {}", error.getMessage(), error);
                    systemLogService.error("LLM 调用失败: " + error.getMessage(), "LLM");
                    flushPendingAssistantText(turnId, pendingAssistantText);
                    turnTimelineService.failTurn(turnId, error.getMessage());

                    String errorMsg = error.getMessage() != null ? error.getMessage() : "";

                    if (errorMsg.contains("context length") || errorMsg.contains("n_ctx") || errorMsg.contains("n_keep")) {
                        sink.tryEmitError(new RuntimeException("输入内容过长，超出模型上下文限制。请尝试：1. 减少图片数量或使用更小的图片 2. 清除对话历史后重试 3. 切换到支持更长上下文的模型"));
                    } else if (errorMsg.contains("image") || errorMsg.contains("vision") || errorMsg.contains("multimodal")) {
                        sink.tryEmitError(new RuntimeException("当前模型不支持图像识别，请切换到支持视觉的模型（如Qwen-VL等）或移除图片后重试"));
                    } else {
                        sink.tryEmitError(error);
                    }
                })
                .start();

        return sink.asFlux();
    }

    private Path resolveSkillsPath() {
        // 1. 优先扫描用户主目录下的配置 (生产环境推荐)
        Path homeSkills = Paths.get(System.getProperty("user.home"), ".lingshu", "skills");
        if (java.nio.file.Files.isDirectory(homeSkills)) {
            return homeSkills;
        }

        // 2. Fallback: 递归向上查找项目目录下的 .lingshu/skills (开发环境便利)
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++) {
            Path candidate = current.resolve(".lingshu").resolve("skills");
            if (java.nio.file.Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Paths.get(System.getProperty("user.dir"), ".lingshu", "skills");
    }

    private void streamMultimodalRequest(StreamingChatModel streamingModel,
                                         Long sessionId,
                                         String systemPrompt,
                                         UserMessage userMessage,
                                         String userId,
                                         String safeMessage,
                                         StringBuilder assistantResponseStore,
                                         Sinks.Many<String> sink,
                                         com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion,
                                         Long turnId) {
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
                completeStreamingResponse(userId, sessionId, safeMessage, assistantResponseStore, sink, preAnalyzedEmotion, turnId);
            }

            @Override
            public void onError(Throwable error) {
                handleStreamingError(error, sink, turnId);
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

    private void flushPendingAssistantText(Long turnId, StringBuilder pendingAssistantText) {
        if (pendingAssistantText == null || pendingAssistantText.isEmpty()) {
            return;
        }
        turnTimelineService.recordAssistantText(turnId, pendingAssistantText.toString());
        pendingAssistantText.setLength(0);
    }

    private void completeStreamingResponse(String userId,
                                           Long sessionId,
                                           String safeMessage,
                                           StringBuilder assistantResponseStore,
                                           Sinks.Many<String> sink,
                                           com.lingshu.ai.core.dto.EmotionAnalysis preAnalyzedEmotion,
                                           Long turnId) {
        int tokenCount = assistantResponseStore.length() / 4;
        systemLogService.llmEnd(tokenCount, "LLM");
        systemLogService.success("对话完成，回复长度: " + assistantResponseStore.length() + " 字符", "CHAT");
        turnTimelineService.recordAssistantText(turnId, assistantResponseStore.toString());
        turnTimelineService.completeTurn(turnId, assistantResponseStore.toString());
        sink.tryEmitComplete();
        postProcessAfterResponse(userId, sessionId, safeMessage, assistantResponseStore.toString(), preAnalyzedEmotion);
    }

    private void handleStreamingError(Throwable error, Sinks.Many<String> sink, Long turnId) {
        log.error("流式对话发生错误: {}", error.getMessage(), error);
        systemLogService.error("LLM 调用失败: " + error.getMessage(), "LLM");
        turnTimelineService.failTurn(turnId, error.getMessage());

        String errorMsg = error.getMessage() != null ? error.getMessage() : "";
        if (errorMsg.contains("context length") || errorMsg.contains("n_ctx") || errorMsg.contains("n_keep")) {
            sink.tryEmitError(new RuntimeException("输入内容过长，超出模型上下文限制。请尝试：\n1. 减少图片数量或使用更小的图片\n2. 清除对话历史后重试\n3. 切换到支持更长上下文的模型"));
        } else if (errorMsg.contains("image") || errorMsg.contains("vision") || errorMsg.contains("multimodal")) {
            sink.tryEmitError(new RuntimeException("当前模型不支持图像识别，请切换到支持视觉的模型（如Qwen-VL等）或移除图片后重试"));
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
        ChatSession session = getOrCreateSession(userId, null);
        systemLogService.info("生成欢迎消息...", "CHAT");

        AgentConfig defaultAgent = agentConfigService.getDefaultAgent().orElse(null);
        String memoryContext = memoryService.retrieveContext(userId, "用户身份与基本信息");
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
                    Long turnId = turnTimelineService.startTurn(session.getId(), "", List.of());
                    turnTimelineService.recordAssistantText(turnId, welcomeBuilder.toString());
                    turnTimelineService.completeTurn(turnId, welcomeBuilder.toString());
                    sink.tryEmitComplete();
                })
                .onError(err -> {
                    sink.tryEmitNext("欢迎回来。系统已话化，随时准备与您共赴时光。");
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

                Object responseObj;
                try {
                    responseObj = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                } catch (Exception e) {
                    if (!base.contains("/v1")) {
                        String fallbackUrl = base + "/models";
                        systemLogService.debug("重试备用 endpoints: " + fallbackUrl, "SYSTEM");
                        responseObj = restTemplate.exchange(fallbackUrl, org.springframework.http.HttpMethod.GET, entity, Object.class).getBody();
                    } else {
                        throw e;
                    }
                }

                if (responseObj instanceof java.util.Map<?, ?> response) {
                    if (response.containsKey("data") && response.get("data") instanceof java.util.List<?> list) {
                        //log.debug("获取? + models.size() + " 个OpenAI兼容模型");
                        return list.stream()
                                .map(m -> (String) ((java.util.Map<?, ?>) m).get("id"))
                                .collect(java.util.stream.Collectors.toList());
                    }
                } else if (responseObj instanceof java.util.List<?> list) {
                    List<String> models = list.stream()
                            .map(m -> (String) ((java.util.Map<?, ?>) m).get("id"))
                            .collect(java.util.stream.Collectors.toList());
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
                systemLogService.info("获取" + modelNames.size() + " 个Ollama模型", "SYSTEM");
                return modelNames;
            }
        } catch (Exception e) {
            systemLogService.error("获取模型列表失败: " + e.getMessage(), "SYSTEM");
            return "openai".equalsIgnoreCase(source) ? List.of("gpt-3.5-turbo") : List.of("qwen3.5:4b");
        }
    }

    @Override
    public void clearHistory(Long sessionId) {
        if (sessionId == null) {
            systemLogService.warn("clearHistory 被拒绝：sessionId 为空", "CHAT");
            throw new IllegalArgumentException("sessionId must not be null");
        }

        // 1. 从数据库物理删除记录
        turnTimelineService.clearTurnHistory(sessionId);

        // 2. 清除 LangChain4j 的 ChatMemory 缓存
        chatMemoryProvider.get(sessionId).clear();

        systemLogService.info("已清空会话记录 sessionId=" + sessionId, "CHAT");
    }

    @PreDestroy
    public void shutdownResources() {
        customModelHttpExecutor.shutdown();
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

    private ToolExecutionResultMessage handleHallucinatedToolName(dev.langchain4j.agent.tool.ToolExecutionRequest request) {
        String toolName = request != null && request.name() != null ? request.name() : "unknown";
        String message = "Tool not available: " + toolName + ". "
                + "Do not invent tool names. Use only tools provided in this turn, or continue without tool calls.";
        systemLogService.warn("检测到幻觉工具调用: " + toolName + "，已返回可恢复错误结果", "TOOL");
        return ToolExecutionResultMessage.builder()
                .id(request != null ? request.id() : null)
                .toolName(toolName)
                .text(message)
                .isError(true)
                .build();
    }

    private String buildToolArgumentsRepairMessage(String toolName, String rawArguments, Throwable error) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tool arguments are invalid JSON and could not be parsed.")
                .append(" Retry the same tool call with valid JSON only. ");

        if ("executeCommand".equals(toolName) || "execute_command".equals(toolName)) {
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
