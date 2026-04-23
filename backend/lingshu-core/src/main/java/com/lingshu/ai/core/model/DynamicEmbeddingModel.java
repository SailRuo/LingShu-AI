package com.lingshu.ai.core.model;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 动态向量模型，根据系统设置实时切换底层的 Embedding 模型实例 (Ollama 或 OpenAI)。
 */
public class DynamicEmbeddingModel implements EmbeddingModel {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicEmbeddingModel.class);

    private final SettingService settingService;
    private volatile String lastConfigId;
    private volatile EmbeddingModel delegate;

    public DynamicEmbeddingModel(SettingService settingService) {
        this.settingService = settingService;
    }

    private void ensureDelegate() {
        SystemSetting setting = settingService.getSetting();
        // 配置指纹，用于检测变更
        String currentConfigId = String.format("%s|%s|%s|%s",
                setting.getEmbedSource(), setting.getEmbedModel(), setting.getEmbedBaseUrl(), setting.getEmbedApiKey());

        if (delegate == null || !currentConfigId.equals(lastConfigId)) {
            synchronized (this) {
                if (delegate == null || !currentConfigId.equals(lastConfigId)) {
                    log.info("初始化/更新动态向量模型：Source={}, Model={}, BaseUrl={}",
                            setting.getEmbedSource(), setting.getEmbedModel(), setting.getEmbedBaseUrl());

                    String source = setting.getEmbedSource();
                    String baseUrl = setting.getEmbedBaseUrl();
                    String modelName = setting.getEmbedModel();
                    String apiKey = setting.getEmbedApiKey();

                    if ("ollama".equalsIgnoreCase(source) || (source == null && baseUrl != null && baseUrl.contains("11434"))) {
                        delegate = OllamaEmbeddingModel.builder()
                                .baseUrl(baseUrl)
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(5))
                                .build();
                    } else {
                        String effectiveUrl = baseUrl;
                        if (effectiveUrl != null && !effectiveUrl.endsWith("/v1") && !effectiveUrl.endsWith("/v1/")) {
                            effectiveUrl = effectiveUrl + (effectiveUrl.endsWith("/") ? "v1" : "/v1");
                        }

                        delegate = OpenAiEmbeddingModel.builder()
                                .baseUrl(effectiveUrl)
                                .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "no-key")
                                .modelName(modelName)
                                .timeout(Duration.ofMinutes(5))
                                .build();
                    }

                    lastConfigId = currentConfigId;
                }
            }
        }
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        ensureDelegate();
        return delegate.embed(textSegment);
    }

    @Override
    public Response<Embedding> embed(String text) {
        ensureDelegate();
        return delegate.embed(text);
    }

    @Override
    public int dimension() {
        SystemSetting setting = settingService.getSetting();
        return resolveConfiguredDimension(setting);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        ensureDelegate();
        return delegate.embedAll(textSegments);
    }

    @Override
    public String modelName() {
        ensureDelegate();
        return delegate.modelName();
    }

    private int resolveConfiguredDimension(SystemSetting setting) {
        if (setting == null) {
            return 768;
        }

        Map<String, Object> embeddingConfig = setting.getEmbeddingConfig();
        Object configuredDimension = embeddingConfig.get("dimension");
        Integer parsedDimension = tryParseInteger(configuredDimension);
        if (parsedDimension != null && parsedDimension > 0) {
            return parsedDimension;
        }

        String modelName = setting.getEmbedModel();
        if (modelName == null || modelName.isBlank()) {
            return 768;
        }

        String normalizedModelName = modelName.toLowerCase();
        if (normalizedModelName.contains("text-embedding-3-large")) {
            return 3072;
        }
        if (normalizedModelName.contains("text-embedding-3-small")
                || normalizedModelName.contains("text-embedding-ada-002")) {
            return 1536;
        }
        if (normalizedModelName.contains("mxbai-embed-large")
                || normalizedModelName.contains("bge-m3")
                || normalizedModelName.contains("qwen3-embedding")
                || normalizedModelName.contains("qwen-embedding")) {
            return 1024;
        }
        if (normalizedModelName.contains("nomic-embed-text")) {
            return 768;
        }

        log.warn("未识别的 embedding 模型维度，使用默认值 768。model={}", modelName);
        return 768;
    }

    private Integer tryParseInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String strValue) {
            try {
                return Integer.parseInt(strValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
