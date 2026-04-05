package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.dto.SystemSettingDTO;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
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
    public Map<String, Object> saveLocalToolsSetting(@RequestBody Map<String, Object> settings) {
        SystemSetting setting = new SystemSetting();
        setting.setId("local_tools");
        setting.setSettings(settings);
        settingService.saveLocalToolsSetting(setting);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "配置已保存");
        return response;
    }

    /**
     * 获取 ASR 配置
     */
    @GetMapping("/asr")
    public Map<String, Object> getAsrSetting() {
        SystemSetting setting = settingService.getSetting();
        return setting.getAsrConfig();
    }

    /**
     * 保存 ASR 配置
     */
    @PostMapping("/asr")
    public void saveAsrSetting(@RequestBody Map<String, Object> asrConfig) {
        SystemSetting setting = settingService.getSetting();
        setting.setAsrConfig(asrConfig);
        settingService.saveSetting(setting);
    }

    /**
     * 获取当前系统配置信息（模型、API Key 等）。
     * 为了保持向后兼容，将 JSON 配置扁平化返回
     */
    @GetMapping
    public Map<String, Object> getSetting() {
        SystemSetting setting = settingService.getSetting();

        Map<String, Object> result = new HashMap<>();

        Map<String, Object> llmConfig = setting.getLlmConfig();
        result.put("source", llmConfig.get("source"));
        result.put("chatModel", llmConfig.get("model"));
        result.put("baseUrl", llmConfig.get("baseUrl"));
        result.put("apiKey", llmConfig.get("apiKey"));

        Map<String, Object> embeddingConfig = setting.getEmbeddingConfig();
        result.put("embedSource", embeddingConfig.get("source"));
        result.put("embedModel", embeddingConfig.get("model"));
        result.put("embedBaseUrl", embeddingConfig.get("baseUrl"));
        result.put("embedApiKey", embeddingConfig.get("apiKey"));

        Map<String, Object> proactiveConfig = setting.getProactiveConfig();
        result.put("proactiveEnabled", proactiveConfig.get("enabled"));
        result.put("inactiveThresholdMinutes", proactiveConfig.get("inactiveThresholdMinutes"));
        result.put("greetingCooldownSeconds", proactiveConfig.get("greetingCooldownSeconds"));
        result.put("inactiveCheckIntervalMs", proactiveConfig.get("inactiveCheckIntervalMs"));

        Map<String, Object> memoryModelConfig = setting.getMemoryModelConfig();
        result.put("memoryModelSource", memoryModelConfig.get("source"));
        result.put("memoryModel", memoryModelConfig.get("model"));
        result.put("memoryModelBaseUrl", memoryModelConfig.get("baseUrl"));
        result.put("memoryModelApiKey", memoryModelConfig.get("apiKey"));

        Map<String, Object> ttsConfig = setting.getTtsConfig();
        result.put("ttsBaseUrl", ttsConfig.get("baseUrl"));
        result.put("ttsApiKey", ttsConfig.get("apiKey"));
        result.put("ttsDefaultVoice", ttsConfig.get("defaultVoice"));
        result.put("ttsDefaultSpeed", ttsConfig.get("defaultSpeed"));
        result.put("ttsDefaultFormat", ttsConfig.get("defaultFormat"));
        result.put("ttsEnabled", ttsConfig.get("enabled"));

        result.put("enableThinking", setting.getEnableThinking());

        return result;
    }

    /**
     * 保存并应用新的系统配置。
     */
    @PostMapping
    public void saveSetting(@RequestBody SystemSettingDTO dto) {
        SystemSetting setting = settingService.getSetting();

        Map<String, Object> llmConfig = setting.getLlmConfig();
        if (dto.getSource() != null) llmConfig.put("source", dto.getSource());
        if (dto.getChatModel() != null) llmConfig.put("model", dto.getChatModel());
        if (dto.getBaseUrl() != null) llmConfig.put("baseUrl", dto.getBaseUrl());
        if (dto.getApiKey() != null) llmConfig.put("apiKey", dto.getApiKey());
        if (dto.getEnableThinking() != null) llmConfig.put("enableThinking", dto.getEnableThinking());
        setting.setLlmConfig(llmConfig);

        Map<String, Object> embeddingConfig = setting.getEmbeddingConfig();
        if (dto.getEmbedSource() != null) embeddingConfig.put("source", dto.getEmbedSource());
        if (dto.getEmbedModel() != null) embeddingConfig.put("model", dto.getEmbedModel());
        if (dto.getEmbedBaseUrl() != null) embeddingConfig.put("baseUrl", dto.getEmbedBaseUrl());
        if (dto.getEmbedApiKey() != null) embeddingConfig.put("apiKey", dto.getEmbedApiKey());
        setting.setEmbeddingConfig(embeddingConfig);

        Map<String, Object> proactiveConfig = setting.getProactiveConfig();
        if (dto.getProactiveEnabled() != null) proactiveConfig.put("enabled", dto.getProactiveEnabled());
        if (dto.getInactiveThresholdMinutes() != null) proactiveConfig.put("inactiveThresholdMinutes", dto.getInactiveThresholdMinutes());
        if (dto.getGreetingCooldownSeconds() != null) proactiveConfig.put("greetingCooldownSeconds", dto.getGreetingCooldownSeconds());
        if (dto.getInactiveCheckIntervalMs() != null) proactiveConfig.put("inactiveCheckIntervalMs", dto.getInactiveCheckIntervalMs());
        setting.setProactiveConfig(proactiveConfig);

        Map<String, Object> memoryModelConfig = setting.getMemoryModelConfig();
        if (dto.getMemoryModelSource() != null) memoryModelConfig.put("source", dto.getMemoryModelSource());
        if (dto.getMemoryModel() != null) memoryModelConfig.put("model", dto.getMemoryModel());
        if (dto.getMemoryModelBaseUrl() != null) memoryModelConfig.put("baseUrl", dto.getMemoryModelBaseUrl());
        if (dto.getMemoryModelApiKey() != null) memoryModelConfig.put("apiKey", dto.getMemoryModelApiKey());
        setting.setMemoryModelConfig(memoryModelConfig);

        Map<String, Object> ttsConfig = setting.getTtsConfig();
        if (dto.getTtsBaseUrl() != null) ttsConfig.put("baseUrl", dto.getTtsBaseUrl());
        if (dto.getTtsApiKey() != null) ttsConfig.put("apiKey", dto.getTtsApiKey());
        if (dto.getTtsDefaultVoice() != null) ttsConfig.put("defaultVoice", dto.getTtsDefaultVoice());
        if (dto.getTtsDefaultSpeed() != null) ttsConfig.put("defaultSpeed", dto.getTtsDefaultSpeed());
        if (dto.getTtsDefaultFormat() != null) ttsConfig.put("defaultFormat", dto.getTtsDefaultFormat());
        if (dto.getTtsEnabled() != null) ttsConfig.put("enabled", dto.getTtsEnabled());
        setting.setTtsConfig(ttsConfig);

        settingService.saveSetting(setting);
    }}
