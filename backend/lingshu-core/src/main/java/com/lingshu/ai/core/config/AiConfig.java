package com.lingshu.ai.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

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

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
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

                for (int i = 0; i < request.messages().size(); i++) {
                    var message = request.messages().get(i);
                    String content = compact(message.toString());
                    int charLength = message.toString() != null ? message.toString().length() : 0;
                    log.info(
                            "LLM Request Message[{}] => type: {}, chars: {}, preview: {}",
                            i,
                            message.type(),
                            charLength,
                            content
                    );
                }
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
    public dev.langchain4j.store.embedding.EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore(dev.langchain4j.model.embedding.EmbeddingModel embeddingModel) {
        // 从 JDBC URL 解析数据库配置 (例如：jdbc:postgresql://postgres:5432/lingshu)
        String dbHost = "localhost";
        int dbPort = 5432;
        String dbName = "lingshu";
        
        if (jdbcUrl != null && jdbcUrl.contains(":postgresql://")) {
            try {
                // 解析 JDBC URL: jdbc:postgresql://host:port/database
                String[] parts = jdbcUrl.substring(jdbcUrl.indexOf(":postgresql://") + 14).split("/");
                if (parts.length > 0) {
                    String[] hostPort = parts[0].split(":");
                    dbHost = hostPort[0];
                    if (hostPort.length > 1) {
                        dbPort = Integer.parseInt(hostPort[1]);
                    }
                }
                if (parts.length > 1) {
                    dbName = parts[1];
                }
            } catch (Exception e) {
                log.warn("Failed to parse JDBC URL, using defaults: {}", e.getMessage());
            }
        }
        
        log.info("Initializing PgVectorEmbeddingStore with host={}, port={}, database={}", dbHost, dbPort, dbName);
        
        return dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.builder()
                .host(dbHost)
                .port(dbPort)
                .database(dbName)
                .user(username)
                .password(password)
                .table("memory_segments")
                .dimension(embeddingModel.dimension())
                .build();
    }

    @Bean
    public dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider(
            dev.langchain4j.store.memory.chat.ChatMemoryStore chatMemoryStore) {
        return sessionId -> dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(10)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    @Bean
    public Assistant assistant(
            ChatModel chatLanguageModel,
            dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider) {
        return dev.langchain4j.service.AiServices.builder(Assistant.class)
                .chatModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
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
    public StatelessStreamingAssistant statelessStreamingAssistant(
            StreamingChatModel streamingChatLanguageModel) {
        return dev.langchain4j.service.AiServices.builder(StatelessStreamingAssistant.class)
                .streamingChatModel(streamingChatLanguageModel)
                .build();
    }

    @Bean
    public com.lingshu.ai.core.service.EmotionAnalyzer emotionAnalyzer(
            com.lingshu.ai.core.model.DynamicMemoryModel dynamicMemoryModel) {
        return dev.langchain4j.service.AiServices.builder(com.lingshu.ai.core.service.EmotionAnalyzer.class)
                .chatModel(dynamicMemoryModel)
                .build();
    }

    @Bean
    public com.lingshu.ai.core.model.DynamicMemoryModel dynamicMemoryModel(
            com.lingshu.ai.core.service.SettingService settingService) {
        return new com.lingshu.ai.core.model.DynamicMemoryModel(settingService);
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


    /**
     * 无记忆的流式 Assistant 接口，用于一次性生成任务（如问候语生成）。
     * 不需要 ChatMemoryProvider，因为不需要保持对话历史。
     */
    public interface StatelessStreamingAssistant {
        @dev.langchain4j.service.SystemMessage("{{systemPrompt}}")
        dev.langchain4j.service.TokenStream chat(
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
