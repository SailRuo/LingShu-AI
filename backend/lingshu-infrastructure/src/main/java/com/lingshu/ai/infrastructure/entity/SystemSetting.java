package com.lingshu.ai.infrastructure.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 获取 ASR 配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAsrConfig() {
        if (settings == null) {
            return createDefaultAsrConfig();
        }
        Object asr = settings.get("asr");
        if (asr instanceof Map) {
            return (Map<String, Object>) asr;
        }
        return createDefaultAsrConfig();
    }

    /**
     * 获取记忆模型配置（用于情感分析和事实提取）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMemoryModelConfig() {
        if (settings == null) {
            return createDefaultMemoryModelConfig();
        }
        Object memoryModel = settings.get("memoryModel");
        if (memoryModel instanceof Map) {
            return (Map<String, Object>) memoryModel;
        }
        Object sentiment = settings.get("sentiment");
        if (sentiment instanceof Map) {
            return (Map<String, Object>) sentiment;
        }
        return createDefaultMemoryModelConfig();
    }

    /**
     * 获取情感分析模型配置（兼容旧接口）
     * @deprecated 使用 getMemoryModelConfig() 代替
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSentimentConfig() {
        return getMemoryModelConfig();
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
     * 获取 LLM 的 enableThinking 配置（是否启用推理/思考模式）
     */
    public Boolean getEnableThinking() {
        Object value = getLlmConfig().get("enableThinking");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
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
     * 获取工具结果总结阈值（字符数）
     * 超过此阈值的结果会被总结
     */
    public Integer getToolResultSummaryThreshold() {
        Object value = getLlmConfig().get("toolResultSummaryThreshold");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 2000;
    }

    /**
     * 获取情感分析模型的 source，为空时使用对话模型配置
     * @deprecated 使用 getMemoryModelSource() 代替
     */
    @Deprecated
    public String getSentimentSource() {
        return getMemoryModelSource();
    }

    /**
     * 获取情感分析模型的 model，为空时使用对话模型配置
     * @deprecated 使用 getMemoryModel() 代替
     */
    @Deprecated
    public String getSentimentModel() {
        return getMemoryModel();
    }

    /**
     * 获取情感分析模型的 baseUrl，为空时使用对话模型配置
     * @deprecated 使用 getMemoryModelBaseUrl() 代替
     */
    @Deprecated
    public String getSentimentBaseUrl() {
        return getMemoryModelBaseUrl();
    }

    /**
     * 获取情感分析模型的 apiKey，为空时使用对话模型配置
     * @deprecated 使用 getMemoryModelApiKey() 代替
     */
    @Deprecated
    public String getSentimentApiKey() {
        return getMemoryModelApiKey();
    }

    /**
     * 获取记忆模型的 source，为空时使用对话模型配置
     */
    public String getMemoryModelSource() {
        String value = (String) getMemoryModelConfig().get("source");
        return (value != null && !value.isBlank()) ? value : getSource();
    }

    /**
     * 获取记忆模型的 model，为空时使用对话模型配置
     */
    public String getMemoryModel() {
        String value = (String) getMemoryModelConfig().get("model");
        return (value != null && !value.isBlank()) ? value : getChatModel();
    }

    /**
     * 获取记忆模型的 baseUrl，为空时使用对话模型配置
     */
    public String getMemoryModelBaseUrl() {
        String value = (String) getMemoryModelConfig().get("baseUrl");
        return (value != null && !value.isBlank()) ? value : getBaseUrl();
    }

    /**
     * 获取记忆模型的 apiKey，为空时使用对话模型配置
     */
    public String getMemoryModelApiKey() {
        String value = (String) getMemoryModelConfig().get("apiKey");
        return (value != null && !value.isBlank()) ? value : getApiKey();
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
     * 更新 ASR 配置
     */
    public void setAsrConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("asr", config);
    }

    /**
     * 更新情感分析模型配置
     */
    public void setSentimentConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("sentiment", config);
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
        config.put("enableThinking", false);
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

    /**
     * 创建默认 ASR 配置
     */
    public Map<String, Object> createDefaultAsrConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("enabled", false);
        config.put("url", "http://localhost:50001");
        return config;
    }

    /**
     * 创建默认记忆模型配置
     */
    public Map<String, Object> createDefaultMemoryModelConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("source", "");
        config.put("model", "");
        config.put("baseUrl", "");
        config.put("apiKey", "");
        return config;
    }

    /**
     * 创建默认情感分析模型配置
     * @deprecated 使用 createDefaultMemoryModelConfig() 代替
     */
    @Deprecated
    public Map<String, Object> createDefaultSentimentConfig() {
        return createDefaultMemoryModelConfig();
    }

    /**
     * 更新记忆模型配置
     */
    public void setMemoryModelConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("memoryModel", config);
    }

    /**
     * 获取 TTS 配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTtsConfig() {
        if (settings == null) {
            return createDefaultTtsConfig();
        }
        Object tts = settings.get("tts");
        if (tts instanceof Map) {
            return (Map<String, Object>) tts;
        }
        return createDefaultTtsConfig();
    }

    /**
     * 获取微信 Bot 配置（单个账户，兼容旧数据）
     * @deprecated 使用 getWechatBotAccounts() 代替
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public Map<String, Object> getWechatBotConfig() {
        if (settings == null) {
            return createDefaultWechatBotConfig();
        }
        Object wechatBot = settings.get("wechatBot");
        if (wechatBot instanceof Map) {
            return (Map<String, Object>) wechatBot;
        }
        return createDefaultWechatBotConfig();
    }

    /**
     * 更新微信 Bot 配置（单个账户，兼容旧数据）
     * @deprecated 使用 setWechatBotAccounts() 代替
     */
    @Deprecated
    public void setWechatBotConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("wechatBot", config);
    }

    /**
     * 获取微信 Bot 账户列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getWechatBotAccounts() {
        if (settings == null) {
            return new java.util.ArrayList<>();
        }
        Object accounts = settings.get("wechatBotAccounts");
        if (accounts instanceof List) {
            return (List<Map<String, Object>>) accounts;
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        Map<String, Object> legacyConfig = getWechatBotConfig();
        if (legacyConfig != null && legacyConfig.get("botToken") != null 
                && !((String) legacyConfig.get("botToken")).isEmpty()) {
            result.add(legacyConfig);
        }
        return result;
    }

    /**
     * 设置微信 Bot 账户列表
     */
    public void setWechatBotAccounts(List<Map<String, Object>> accounts) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("wechatBotAccounts", accounts);
    }

    /**
     * 添加微信 Bot 账户
     */
    public void addWechatBotAccount(Map<String, Object> account) {
        List<Map<String, Object>> accounts = new java.util.ArrayList<>(getWechatBotAccounts());
        String accountId = (String) account.get("accountId");
        boolean found = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accountId != null && accountId.equals(accounts.get(i).get("accountId"))) {
                accounts.set(i, account);
                found = true;
                break;
            }
        }
        if (!found) {
            accounts.add(account);
        }
        setWechatBotAccounts(accounts);
    }

    /**
     * 删除微信 Bot 账户
     */
    public void removeWechatBotAccount(String accountId) {
        List<Map<String, Object>> accounts = new java.util.ArrayList<>(getWechatBotAccounts());
        accounts.removeIf(acc -> accountId != null && accountId.equals(acc.get("accountId")));
        setWechatBotAccounts(accounts);
    }

    /**
     * 根据 accountId 获取微信 Bot 账户
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getWechatBotAccount(String accountId) {
        List<Map<String, Object>> accounts = getWechatBotAccounts();
        for (Map<String, Object> acc : accounts) {
            if (accountId != null && accountId.equals(acc.get("accountId"))) {
                return acc;
            }
        }
        return null;
    }

    /**
     * 创建默认微信 Bot 配置
     */
    public Map<String, Object> createDefaultWechatBotConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("accountId", java.util.UUID.randomUUID().toString());
        config.put("botToken", "");
        config.put("baseUrl", "https://ilinkai.weixin.qq.com");
        config.put("status", "wait");
        config.put("lastLoginTime", "");
        config.put("nickname", "");
        return config;
    }

    /**
     * 更新 TTS 配置
     */
    public void setTtsConfig(Map<String, Object> config) {
        if (this.settings == null) {
            this.settings = new java.util.HashMap<>();
        }
        this.settings.put("tts", config);
    }

    /**
     * 创建默认 TTS 配置
     */
    public Map<String, Object> createDefaultTtsConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("enabled", false);
        config.put("baseUrl", "http://localhost:5050");
        config.put("apiKey", "");
        config.put("defaultVoice", "alloy");
        config.put("defaultSpeed", 1.0);
        config.put("defaultFormat", "mp3");
        return config;
    }
}
