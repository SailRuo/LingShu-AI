package com.lingshu.ai.infrastructure.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统设置实体（JSON 存储方案）
 * 所有配置存储在一个 JSON 字段中，方便扩展
 */
@Entity
@Table(name = "system_settings")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    @Id
    @Builder.Default
    private String id = "DEFAULT";

    /**
     * 所有配置项的 JSON 存储
     * 结构示例：
     * {
     *   "llm": { ... },
     *   "embedding": { ... },
     *   "proactive": { ... }
     * }
     * 
     * 或者对于 local_tools ID：
     * {
     *   "tools": [
     *     {
     *       "name": "executeCommand",
     *       "displayName": "执行终端命令",
     *       "enabled": true,
     *       "prompt": "..."
     *     }
     *   ]
     * }
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 获取 LLM 配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLlmConfig() {
        if (settings == null) {
            return createDefaultLlmConfig();
        }
        Object llm = settings.get("llm");
        if (llm instanceof Map) {
            return (Map<String, Object>) llm;
        }
        return createDefaultLlmConfig();
    }

    /**
     * 获取 Embedding 配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getEmbeddingConfig() {
        if (settings == null) {
            return createDefaultEmbeddingConfig();
        }
        Object embedding = settings.get("embedding");
        if (embedding instanceof Map) {
            return (Map<String, Object>) embedding;
        }
        return createDefaultEmbeddingConfig();
    }

    /**
     * 获取主动问候配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProactiveConfig() {
        if (settings == null) {
            return createDefaultProactiveConfig();
        }
        Object proactive = settings.get("proactive");
        if (proactive instanceof Map) {
            return (Map<String, Object>) proactive;
        }
        return createDefaultProactiveConfig();
    }

    // ========== 便捷访问方法 ==========
    
    /**
     * 获取 LLM 的 source 配置
     */
    public String getSource() {
        return (String) getLlmConfig().get("source");
    }
    
    /**
     * 获取 LLM 的 model 配置
     */
    public String getChatModel() {
        return (String) getLlmConfig().get("model");
    }
    
    /**
     * 获取 LLM 的 baseUrl 配置
     */
    public String getBaseUrl() {
        return (String) getLlmConfig().get("baseUrl");
    }
    
    /**
     * 获取 LLM 的 apiKey 配置
     */
    public String getApiKey() {
        return (String) getLlmConfig().get("apiKey");
    }
    
    /**
     * 获取 Embedding 的 source 配置
     */
    public String getEmbedSource() {
        return (String) getEmbeddingConfig().get("source");
    }
    
    /**
     * 获取 Embedding 的 model 配置
     */
    public String getEmbedModel() {
        return (String) getEmbeddingConfig().get("model");
    }
    
    /**
     * 获取 Embedding 的 baseUrl 配置
     */
    public String getEmbedBaseUrl() {
        return (String) getEmbeddingConfig().get("baseUrl");
    }
    
    /**
     * 获取 Embedding 的 apiKey 配置
     */
    public String getEmbedApiKey() {
        return (String) getEmbeddingConfig().get("apiKey");
    }
    
    /**
     * 获取主动问候是否启用
     */
    public Boolean getProactiveEnabled() {
        return (Boolean) getProactiveConfig().get("enabled");
    }
    
    /**
     * 获取不活跃阈值（分钟）
     */
    public Integer getInactiveThresholdMinutes() {
        Object value = getProactiveConfig().get("inactiveThresholdMinutes");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 5;
    }
    
    /**
     * 获取问候冷却时间（秒）
     */
    public Integer getGreetingCooldownSeconds() {
        Object value = getProactiveConfig().get("greetingCooldownSeconds");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 300;
    }
    
    /**
     * 获取不活跃检查间隔（毫秒）
     */
    public Long getInactiveCheckIntervalMs() {
        Object value = getProactiveConfig().get("inactiveCheckIntervalMs");
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 3600000L;
    }

    /**
     * 更新 LLM 配置
     */
    public void setLlmConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("llm", config);
    }

    /**
     * 更新 Embedding 配置
     */
    public void setEmbeddingConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("embedding", config);
    }

    /**
     * 更新主动问候配置
     */
    public void setProactiveConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("proactive", config);
    }

    /**
     * 创建默认 LLM 配置
     */
    public Map<String, Object> createDefaultLlmConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("source", "ollama");
        config.put("model", "qwen2.5:latest");
        config.put("baseUrl", "http://localhost:11434");
        config.put("apiKey", "");
        return config;
    }

    /**
     * 创建默认 Embedding 配置
     */
    public Map<String, Object> createDefaultEmbeddingConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("source", "ollama");
        config.put("model", "nomic-embed-text");
        config.put("baseUrl", "http://localhost:11434");
        config.put("apiKey", "");
        return config;
    }

    /**
     * 创建默认主动问候配置
     */
    public Map<String, Object> createDefaultProactiveConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("enabled", true);
        config.put("inactiveThresholdMinutes", 5);
        config.put("greetingCooldownSeconds", 300);
        config.put("inactiveCheckIntervalMs", 3600000L);
        return config;
    }
}
