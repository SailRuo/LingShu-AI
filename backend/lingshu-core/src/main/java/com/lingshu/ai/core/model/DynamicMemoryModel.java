package com.lingshu.ai.core.model;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executors;

/**
 * 动态记忆模型。
 * 用于情感分析和事实提取等记忆系统相关任务。
 * 当记忆模型配置字段为空时，自动使用对话模型配置。
 */
public class DynamicMemoryModel implements ChatModel {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicMemoryModel.class);

    private final SettingService settingService;
    private volatile String lastConfigId;
    private ChatModel delegate;

    public DynamicMemoryModel(SettingService settingService) {
        this.settingService = settingService;
    }

    private void ensureDelegate() {
        SystemSetting setting = settingService.getSetting();
        
        String source = setting.getMemoryModelSource();
        String baseUrl = setting.getMemoryModelBaseUrl();
        String modelName = setting.getMemoryModel();
        String apiKey = setting.getMemoryModelApiKey();
        
        String currentConfigId = String.format("memory|%s|%s|%s|%s",
                source, modelName, baseUrl, apiKey);
        
        if (delegate == null || !currentConfigId.equals(lastConfigId)) {
            synchronized (this) {
                if (delegate == null || !currentConfigId.equals(lastConfigId)) {
                    log.debug("初始化/更新记忆模型: Source={}, Model={}, BaseUrl={}",
                            source, modelName, baseUrl);

                    if ("ollama".equalsIgnoreCase(source) || (baseUrl != null && baseUrl.contains("11434"))) {
                        delegate = OllamaChatModel.builder()
                                .baseUrl(baseUrl)
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(2))
                                .listeners(Collections.emptyList())
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
                        
                        delegate = OpenAiChatModel.builder()
                                .baseUrl(effectiveUrl)
                                .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "no-key")
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(2))
                                .listeners(Collections.emptyList())
                                .httpClientBuilder(httpClientBuilder)
                                .build();
                    }
                    
                    lastConfigId = currentConfigId;
                }
            }
        }
    }

    @Override
    public dev.langchain4j.model.chat.response.ChatResponse chat(dev.langchain4j.model.chat.request.ChatRequest request) {
        ensureDelegate();
        return delegate.chat(request);
    }

    @Override
    public java.util.Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
        ensureDelegate();
        return delegate.supportedCapabilities();
    }

    @Override
    public dev.langchain4j.model.ModelProvider provider() {
        ensureDelegate();
        return delegate.provider();
    }

    @Override
    public java.util.List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners() {
        return Collections.emptyList();
    }

    @Override
    public dev.langchain4j.model.chat.request.ChatRequestParameters defaultRequestParameters() {
        ensureDelegate();
        return delegate.defaultRequestParameters();
    }
}
