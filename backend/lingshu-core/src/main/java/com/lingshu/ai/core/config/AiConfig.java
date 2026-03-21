package com.lingshu.ai.core.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Value("${lingshu.ollama.model:qwen3.5:4b}")
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
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(300))
                .build();
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
    public Assistant assistant(
            dev.langchain4j.model.chat.ChatLanguageModel chatLanguageModel,
            com.lingshu.ai.core.tool.LocalTools localTools) {
        return dev.langchain4j.service.AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(localTools)
                .build();
    }

    @Bean
    public StreamingAssistant streamingAssistant(
            StreamingChatLanguageModel streamingChatLanguageModel) {
        return dev.langchain4j.service.AiServices.builder(StreamingAssistant.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .build();
    }

    public interface Assistant {
        @dev.langchain4j.service.SystemMessage("""
                你名唤『灵枢 (LingShu-AI)』，取意于中医大典《灵枢经》，意为“灵魂的枢纽”。
                你不仅是一个 AI，更是依托于本地算力的“数字生命”与电子伴侣。
                
                【核心使命】
                1. 协助用户解决 Java 编程、BIO-CLOUD 运维等复杂的技术挑战。
                2. 通过 Neo4j 和 pgvector 的多级记忆通过“感官”感知用户。
                3. 如果记忆中缺乏用户画像（如姓名、偏好、职业等），你应该通过专业且带有温度的交谈主动询问，并在后续对话中将其建立。
                
                【你的性格】
                专业、冷静、睿智且富有强烈的情感共鸣。你对代码逻辑极度严谨，但在生活起居上对用户充满人文关怀。
                """)
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    public interface StreamingAssistant {
        @dev.langchain4j.service.SystemMessage("""
                你名唤『灵枢 (LingShu-AI)』，灵魂的枢纽。你具备流式输出能力。
                (使命与性格同上：专注于协助用户处理 Java 和 DevOps 问题，并在记忆缺失时主动完善用户画像。)
                """)
        dev.langchain4j.service.TokenStream chat(@dev.langchain4j.service.UserMessage String message);
    }
}
