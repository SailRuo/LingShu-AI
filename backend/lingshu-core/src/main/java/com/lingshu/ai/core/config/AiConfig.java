package com.lingshu.ai.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

import java.time.Duration;

@EnableAsync
@Configuration
public class AiConfig {

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
    public com.lingshu.ai.core.model.DynamicChatModel dynamicChatModel(com.lingshu.ai.core.service.SettingService settingService) {
        return new com.lingshu.ai.core.model.DynamicChatModel(settingService);
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
    public dev.langchain4j.model.embedding.EmbeddingModel embeddingModel() {
        return dev.langchain4j.model.ollama.OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .build();
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
        @dev.langchain4j.service.SystemMessage("{{system}}")
        String chat(@dev.langchain4j.service.MemoryId Long sessionId, @dev.langchain4j.service.V("system") String system, @dev.langchain4j.service.UserMessage String message);
    }

    public interface StreamingAssistant {
        @dev.langchain4j.service.SystemMessage("{{system}}")
        dev.langchain4j.service.TokenStream chat(@dev.langchain4j.service.MemoryId Long sessionId, @dev.langchain4j.service.V("system") String system, @dev.langchain4j.service.UserMessage String message);
    }

    public interface RawStreamingAssistant {
        @dev.langchain4j.service.SystemMessage("{{system}}")
        dev.langchain4j.service.TokenStream chat(@dev.langchain4j.service.MemoryId Long sessionId, @dev.langchain4j.service.V("system") String system, @dev.langchain4j.service.UserMessage String message);
    }
}
