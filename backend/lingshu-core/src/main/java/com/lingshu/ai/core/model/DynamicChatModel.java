package com.lingshu.ai.core.model;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 动态对话模型，根据系统设置实时切换底层的 LLM 实例 (Ollama 或 OpenAI)。
 * 实现了 ChatModel 和 StreamingChatModel 接口。
 */
public class DynamicChatModel implements ChatModel, StreamingChatModel {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicChatModel.class);

    private final SettingService settingService;
    private final List<ChatModelListener> listeners;
    private volatile String lastConfigId;
    private ChatModel chatDelegate;
    private StreamingChatModel streamingDelegate;

    public DynamicChatModel(SettingService settingService, List<ChatModelListener> listeners) {
        this.settingService = settingService;
        this.listeners = listeners;
    }

    private void ensureDelegate() {
        SystemSetting setting = settingService.getSetting();
        // 简单的配置指纹，用于检测变更
        String currentConfigId = String.format("%s|%s|%s|%s|%s",
                setting.getSource(), setting.getChatModel(), setting.getBaseUrl(), setting.getApiKey(),
                setting.getEnableThinking());

        if (chatDelegate == null || streamingDelegate == null || !currentConfigId.equals(lastConfigId)) {
            synchronized (this) {
                if (chatDelegate == null || streamingDelegate == null || !currentConfigId.equals(lastConfigId)) {
                    log.debug("初始化/更新动态模型代理: Source={}, Model={}, BaseUrl={}",
                            setting.getSource(), setting.getChatModel(), setting.getBaseUrl());

                    String source = setting.getSource();
                    String baseUrl = setting.getBaseUrl();
                    String modelName = setting.getChatModel();
                    String apiKey = setting.getApiKey();
                    Boolean enableThinking = setting.getEnableThinking();

                    if ("ollama".equalsIgnoreCase(source)
                            || (source == null && baseUrl != null && baseUrl.contains("11434"))) {
                        chatDelegate = OllamaChatModel.builder()
                                .baseUrl(baseUrl)
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(5))
                                .listeners(listeners)
                                .build();
                        streamingDelegate = OllamaStreamingChatModel.builder()
                                .baseUrl(baseUrl)
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(5))
                                .listeners(listeners)
                                .build();
                    } else {
                        String effectiveUrl = baseUrl;
                        if (effectiveUrl != null && !effectiveUrl.endsWith("/v1") && !effectiveUrl.endsWith("/v1/")) {
                            effectiveUrl = effectiveUrl + (effectiveUrl.endsWith("/") ? "v1" : "/v1");
                        }

                        JdkHttpClientBuilder httpClientBuilder = dev.langchain4j.http.client.jdk.JdkHttpClient.builder()
                                .httpClientBuilder(HttpClient.newBuilder()
                                        .version(HttpClient.Version.HTTP_1_1)
                                        .connectTimeout(Duration.ofSeconds(30))
                                        .executor(Executors.newCachedThreadPool()));

                        var chatBuilder = OpenAiChatModel.builder()
                                .baseUrl(effectiveUrl)
                                .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "no-key")
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(5))
                                .listeners(listeners)
                                .httpClientBuilder(httpClientBuilder);

                        boolean isGemini = modelName != null && modelName.toLowerCase().contains("gemini");

                        if (Boolean.TRUE.equals(enableThinking)) {
                            if (isGemini) {
                                log.warn(
                                        "检测到 Gemini 模型 [{}]，当前 LangChain4j 版本不支持 Gemini 在 Thinking 模式下的工具调用 (缺少 thought_signature)，已自动回退到普通模式以保证工具正常运行。",
                                        modelName);
                            } else {
                                chatBuilder.returnThinking(true);
                                log.info("启用 Thinking/Reasoning 模式: [{}]", modelName);
                            }
                        }

                        chatDelegate = chatBuilder.build();

                        var streamingBuilder = OpenAiStreamingChatModel.builder()
                                .baseUrl(effectiveUrl)
                                .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "no-key")
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(5))
                                .listeners(listeners)
                                .httpClientBuilder(httpClientBuilder);

                        if (Boolean.TRUE.equals(enableThinking) && !isGemini) {
                            streamingBuilder.returnThinking(true);
                        }

                        streamingDelegate = streamingBuilder.build();
                    }

                    lastConfigId = currentConfigId;
                }
            }
        }
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        ensureDelegate();
        return chatDelegate.chat(request);
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        ensureDelegate();
        streamingDelegate.chat(request, handler);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        ensureDelegate();
        return chatDelegate.supportedCapabilities();
    }

    @Override
    public ModelProvider provider() {
        ensureDelegate();
        return chatDelegate.provider();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        ensureDelegate();
        return chatDelegate.defaultRequestParameters();
    }
}
