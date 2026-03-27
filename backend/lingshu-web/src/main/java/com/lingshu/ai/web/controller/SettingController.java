package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.dto.SystemSettingDTO;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * 获取本地工具配置
     */
    @GetMapping("/local-tools")
    public Map<String, Object> getLocalToolsSetting() {
        SystemSetting setting = settingService.getLocalToolsSetting();
        return setting.getSettings();
    }

    /**
     * 保存本地工具配置
     */
    @PostMapping("/local-tools")
    public void saveLocalToolsSetting(@RequestBody Map<String, Object> settings) {
        SystemSetting setting = new SystemSetting();
        setting.setId("local_tools");
        setting.setSettings(settings);
        settingService.saveLocalToolsSetting(setting);
    }

    /**
     * 获取当前系统配置信息（模型、API Key 等）。
     * 为了保持向后兼容，将 JSON 配置扁平化返回
     */
    @GetMapping
    public Map<String, Object> getSetting() {
        SystemSetting setting = settingService.getSetting();
        
        // 将 JSON 配置扁平化，保持与前端接口的兼容性
        Map<String, Object> result = new HashMap<>();
        
        // LLM 配置
        Map<String, Object> llmConfig = setting.getLlmConfig();
        result.put("source", llmConfig.get("source"));
        result.put("chatModel", llmConfig.get("model"));
        result.put("baseUrl", llmConfig.get("baseUrl"));
        result.put("apiKey", llmConfig.get("apiKey"));
        
        // Embedding 配置
        Map<String, Object> embeddingConfig = setting.getEmbeddingConfig();
        result.put("embedSource", embeddingConfig.get("source"));
        result.put("embedModel", embeddingConfig.get("model"));
        result.put("embedBaseUrl", embeddingConfig.get("baseUrl"));
        result.put("embedApiKey", embeddingConfig.get("apiKey"));
        
        // 主动问候配置
        Map<String, Object> proactiveConfig = setting.getProactiveConfig();
        result.put("proactiveEnabled", proactiveConfig.get("enabled"));
        result.put("inactiveThresholdMinutes", proactiveConfig.get("inactiveThresholdMinutes"));
        result.put("greetingCooldownSeconds", proactiveConfig.get("greetingCooldownSeconds"));
        result.put("inactiveCheckIntervalMs", proactiveConfig.get("inactiveCheckIntervalMs"));
        
        return result;
    }

    /**
     * 保存并应用新的系统配置。
     * 接收扁平化的配置数据，转换为 JSON 格式存储
     */
    @PostMapping
    public void saveSetting(@RequestBody SystemSettingDTO dto) {
        SystemSetting setting = new SystemSetting();
        setting.setId("DEFAULT");
        
        // 构建 LLM 配置
        Map<String, Object> llmConfig = new HashMap<>();
        llmConfig.put("source", dto.getSource() != null ? dto.getSource() : "ollama");
        llmConfig.put("model", dto.getChatModel() != null ? dto.getChatModel() : "");
        llmConfig.put("baseUrl", dto.getBaseUrl() != null ? dto.getBaseUrl() : "");
        llmConfig.put("apiKey", dto.getApiKey() != null ? dto.getApiKey() : "");
        setting.setLlmConfig(llmConfig);
        
        // 构建 Embedding 配置
        Map<String, Object> embeddingConfig = new HashMap<>();
        embeddingConfig.put("source", dto.getEmbedSource() != null ? dto.getEmbedSource() : "ollama");
        embeddingConfig.put("model", dto.getEmbedModel() != null ? dto.getEmbedModel() : "");
        embeddingConfig.put("baseUrl", dto.getEmbedBaseUrl() != null ? dto.getEmbedBaseUrl() : "http://localhost:11434");
        embeddingConfig.put("apiKey", dto.getEmbedApiKey() != null ? dto.getEmbedApiKey() : "");
        setting.setEmbeddingConfig(embeddingConfig);
        
        // 构建主动问候配置
        Map<String, Object> proactiveConfig = new HashMap<>();
        proactiveConfig.put("enabled", dto.getProactiveEnabled() != null ? dto.getProactiveEnabled() : true);
        proactiveConfig.put("inactiveThresholdMinutes", dto.getInactiveThresholdMinutes() != null ? dto.getInactiveThresholdMinutes() : 5);
        proactiveConfig.put("greetingCooldownSeconds", dto.getGreetingCooldownSeconds() != null ? dto.getGreetingCooldownSeconds() : 300);
        proactiveConfig.put("inactiveCheckIntervalMs", dto.getInactiveCheckIntervalMs() != null ? dto.getInactiveCheckIntervalMs() : 3600000L);
        setting.setProactiveConfig(proactiveConfig);
        
        settingService.saveSetting(setting);
    }
}
