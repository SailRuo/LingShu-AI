package com.lingshu.ai.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnableAsync
@Configuration
public class AiConfig {
    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("LingShuAsync-");
        executor.initialize();
        return executor;
    }

    @Value("${lingshu.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${lingshu.ollama.model:qwen2.5:latest}")
    private String modelName;

    @Value("${lingshu.ollama.embedding-model:qwen3-embedding:latest}")
    private String embeddingModelName;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public dev.langchain4j.model.chat.listener.ChatModelListener chatModelListener() {
        return new dev.langchain4j.model.chat.listener.ChatModelListener() {
            @Override
            public void onRequest(dev.langchain4j.model.chat.listener.ChatModelRequestContext requestContext) {
                dev.langchain4j.model.chat.request.ChatRequest request = requestContext.chatRequest();
                long systemCount = request.messages().stream()
                        .filter(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.SYSTEM)
                        .count();
                long userCount = request.messages().stream()
                        .filter(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.USER)
                        .count();
                long assistantCount = request.messages().stream()
                        .filter(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.AI)
                        .count();
                long toolCount = request.messages().stream()
                        .filter(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT)
                        .count();

                String lastUserPreview = request.messages().stream()
                        .filter(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.USER)
                        .reduce((first, second) -> second)
                        .map(Object::toString)
                        .map(this::compact)
                        .orElse("<none>");

                String lastToolPreview = request.messages().stream()
                        .filter(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT)
                        .reduce((first, second) -> second)
                        .map(Object::toString)
                        .map(this::compact)
                        .orElse("<none>");

                log.info(
                        "LLM Request Summary => messages: {}, system: {}, user: {}, assistant: {}, tool: {}, lastUser: {}, lastTool: {}",
                        request.messages().size(),
                        systemCount,
                        userCount,
                        assistantCount,
                        toolCount,
                        lastUserPreview,
                        lastToolPreview
                );
            }

            @Override
            public void onResponse(dev.langchain4j.model.chat.listener.ChatModelResponseContext responseContext) {
                String responseText = responseContext.chatResponse() != null
                        && responseContext.chatResponse().aiMessage() != null
                        ? responseContext.chatResponse().aiMessage().text()
                        : null;
                log.info("LLM Response Summary => {}", compact(responseText));
            }

            @Override
            public void onError(dev.langchain4j.model.chat.listener.ChatModelErrorContext errorContext) {
                log.error("LLM Error Summary => {}", errorContext.error().getMessage());
            }

            private String compact(String text) {
                if (text == null || text.isBlank()) {
                    return "<empty>";
                }
                String normalized = text.replaceAll("\\s+", " ").trim();
                return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
            }
        };
    }

    @Bean
    public com.lingshu.ai.core.model.DynamicChatModel dynamicChatModel(
            com.lingshu.ai.core.service.SettingService settingService,
            java.util.List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners) {
        return new com.lingshu.ai.core.model.DynamicChatModel(settingService, listeners);
    }

    @Bean
    public ChatModel chatLanguageModel(com.lingshu.ai.core.model.DynamicChatModel dynamicChatModel) {
        return dynamicChatModel;
    }

    @Bean
    public StreamingChatModel streamingChatLanguageModel(com.lingshu.ai.core.model.DynamicChatModel dynamicChatModel) {
        return dynamicChatModel;
    }

    @Bean
    public com.lingshu.ai.core.model.DynamicEmbeddingModel dynamicEmbeddingModel(
            com.lingshu.ai.core.service.SettingService settingService) {
        return new com.lingshu.ai.core.model.DynamicEmbeddingModel(settingService);
    }

    @Bean
    public dev.langchain4j.model.embedding.EmbeddingModel embeddingModel(
            com.lingshu.ai.core.model.DynamicEmbeddingModel dynamicEmbeddingModel) {
        return dynamicEmbeddingModel;
    }

    @Bean
    public dev.langchain4j.store.embedding.EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore() {
        return dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .database("lingshu")
                .user(username)
                .password(password)
                .table("memory_segments")
                .dimension(4096) // 针对 Qwen2.5/Llama3 等模型通常返回 4096 维嵌入
                .build();
    }

    @Bean
    public dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider(
            dev.langchain4j.store.memory.chat.ChatMemoryStore chatMemoryStore) {
        return sessionId -> dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(20)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    @Bean
    public Assistant assistant(
            ChatModel chatLanguageModel,
            dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider,
            com.lingshu.ai.core.tool.LocalTools localTools) {
        return dev.langchain4j.service.AiServices.builder(Assistant.class)
                .chatModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(localTools)
                .build();
    }

    @Bean
    public StreamingAssistant streamingAssistant(
            StreamingChatModel streamingChatLanguageModel,
            dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider) {
        return dev.langchain4j.service.AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(streamingChatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    @Bean
    public com.lingshu.ai.core.service.EmotionAnalyzer emotionAnalyzer(
            ChatModel chatLanguageModel) {
        return dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.service.EmotionAnalyzer.class)
                .chatModel(chatLanguageModel)
                .build();
    }

    public interface Assistant {
        @dev.langchain4j.service.SystemMessage("{{systemPrompt}}")
        String chat(@dev.langchain4j.service.MemoryId Long sessionId,
                @dev.langchain4j.service.UserMessage String message,
                @dev.langchain4j.service.V("systemPrompt") String systemPrompt);
    }

    public interface StreamingAssistant {
        @dev.langchain4j.service.SystemMessage("{{systemPrompt}}")
        dev.langchain4j.service.TokenStream chat(@dev.langchain4j.service.MemoryId Long sessionId,
                @dev.langchain4j.service.UserMessage String message,
                @dev.langchain4j.service.V("systemPrompt") String systemPrompt);
    }

    public interface RawStreamingAssistant {
        @dev.langchain4j.service.SystemMessage("{{systemPrompt}}")
        dev.langchain4j.service.TokenStream chat(@dev.langchain4j.service.MemoryId Long sessionId,
                @dev.langchain4j.service.UserMessage String message,
                @dev.langchain4j.service.V("systemPrompt") String systemPrompt);
    }

    /**
     * 不带 @SystemMessage 注解的 Assistant 接口。
     * System Prompt 由 ChatServiceImpl 手动注入到 ChatMemory 中，
     * 避免 @SystemMessage 模板变量与 chatMemoryProvider 交互导致 system prompt 丢失。
     */
    public interface PlainAssistant {
        String chat(@dev.langchain4j.service.MemoryId Long sessionId,
                @dev.langchain4j.service.UserMessage String message);
    }

    /**
     * 不带 @SystemMessage 注解的流式 Assistant 接口。
     * System Prompt 由 ChatServiceImpl 手动注入到 ChatMemory 中。
     */
    public interface PlainStreamingAssistant {
        dev.langchain4j.service.TokenStream chat(@dev.langchain4j.service.MemoryId Long sessionId,
                @dev.langchain4j.service.UserMessage String message);
    }
}
