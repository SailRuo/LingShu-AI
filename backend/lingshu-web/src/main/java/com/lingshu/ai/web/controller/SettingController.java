package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.infrastructure.dto.SystemSettingDTO;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettingController.class);
    private static final Path SKILLS_DIR = Paths.get(System.getProperty("user.dir"), ".lingshu", "skills");
    private static final String DEFAULT_EMBED_TABLE = "memory_segments";

    private final SettingService settingService;
    private final MemoryService memoryService;

    public SettingController(SettingService settingService, MemoryService memoryService) {
        this.settingService = settingService;
        this.memoryService = memoryService;
    }

    /**
     * 获取当前已加载的 Skills 列表
     */
    @GetMapping("/skills")
    public Map<String, Object> getSkills() {
        List<Map<String, Object>> skills = new ArrayList<>();
        try {
            if (Files.exists(SKILLS_DIR)) {
                List<FileSystemSkill> loadedSkills = FileSystemSkillLoader.loadSkills(SKILLS_DIR);
                for (FileSystemSkill skill : loadedSkills) {
                    skills.add(toSkillSummary(skill));
                }
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("skills", skills);
            error.put("path", SKILLS_DIR.toString());
            error.put("error", e.getMessage());
            return error;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("skills", skills);
        response.put("path", SKILLS_DIR.toString());
        response.put("count", skills.size());
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
        result.put("embedDimension", resolveEmbeddingDimension(embeddingConfig));
        result.put("embedTable", resolveEmbeddingTableName(embeddingConfig));

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
        result.put("ttsDefaultSeed", ttsConfig.getOrDefault("defaultSeed", -1));
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

        Map<String, Object> oldEmbeddingConfig = new HashMap<>(setting.getEmbeddingConfig());
        String previousSignature = buildEmbeddingSignature(oldEmbeddingConfig);
        int previousDimension = resolveEmbeddingDimension(oldEmbeddingConfig);

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
        if (dto.getEmbedDimension() != null && dto.getEmbedDimension() > 0) {
            embeddingConfig.put("dimension", dto.getEmbedDimension());
        }
        if (dto.getEmbedTable() != null && !dto.getEmbedTable().isBlank()) {
            embeddingConfig.put("table", dto.getEmbedTable().trim());
        }

        int currentDimension = resolveEmbeddingDimension(embeddingConfig);
        boolean dimensionChanged = currentDimension != previousDimension;
        if (dimensionChanged) {
            String migratedTable = buildMigratedEmbeddingTableName(currentDimension);
            embeddingConfig.put("table", migratedTable);
            embeddingConfig.put("dimension", currentDimension);
            log.warn("检测到向量维度变更：{} -> {}，切换到新表 {}", previousDimension, currentDimension, migratedTable);
        } else {
            if (!(embeddingConfig.get("table") instanceof String table) || table.isBlank()) {
                embeddingConfig.put("table", resolveEmbeddingTableName(oldEmbeddingConfig));
            }
            embeddingConfig.putIfAbsent("dimension", currentDimension);
        }
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
        if (dto.getTtsDefaultSeed() != null) ttsConfig.put("defaultSeed", dto.getTtsDefaultSeed());
        if (dto.getTtsEnabled() != null) ttsConfig.put("enabled", dto.getTtsEnabled());
        setting.setTtsConfig(ttsConfig);

        settingService.saveSetting(setting);

        String currentSignature = buildEmbeddingSignature(setting.getEmbeddingConfig());
        boolean embeddingConfigChanged = !Objects.equals(previousSignature, currentSignature);
        if (embeddingConfigChanged) {
            if (isEmbeddingUsable(setting.getEmbeddingConfig())) {
                log.info("检测到向量配置变更，自动触发向量重建。old={}, new={}", previousSignature, currentSignature);
                memoryService.rebuildAllEmbeddings();
            } else {
                log.info("向量配置已变更但尚未完整配置（首次安装场景），跳过自动重建。new={}", currentSignature);
            }
        }
    }

    private String resolveEmbeddingTableName(Map<String, Object> config) {
        Object table = config.get("table");
        if (table instanceof String tableName && !tableName.isBlank()) {
            return tableName.trim();
        }
        return DEFAULT_EMBED_TABLE;
    }

    private String buildEmbeddingSignature(Map<String, Object> config) {
        String source = toNormalizedString(config.get("source"));
        String model = toNormalizedString(config.get("model"));
        String baseUrl = toNormalizedString(config.get("baseUrl"));
        int dimension = resolveEmbeddingDimension(config);
        return source + "|" + model + "|" + baseUrl + "|" + dimension;
    }

    private String toNormalizedString(Object value) {
        if (value == null) {
            return "";
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? "" : str;
    }

    private boolean isEmbeddingUsable(Map<String, Object> config) {
        String source = toNormalizedString(config.get("source"));
        String model = toNormalizedString(config.get("model"));
        String baseUrl = toNormalizedString(config.get("baseUrl"));
        return !source.isBlank() && !model.isBlank() && !baseUrl.isBlank();
    }

    private int resolveEmbeddingDimension(Map<String, Object> config) {
        Object configuredDimension = config.get("dimension");
        Integer parsedDimension = tryParseInteger(configuredDimension);
        if (parsedDimension != null && parsedDimension > 0) {
            return parsedDimension;
        }

        String modelName = toNormalizedString(config.get("model")).toLowerCase();
        if (modelName.isBlank()) {
            return 768;
        }
        if (modelName.contains("text-embedding-3-large")) {
            return 3072;
        }
        if (modelName.contains("text-embedding-3-small") || modelName.contains("text-embedding-ada-002")) {
            return 1536;
        }
        if (modelName.contains("mxbai-embed-large")
                || modelName.contains("bge-m3")
                || modelName.contains("qwen3-embedding")
                || modelName.contains("qwen-embedding")) {
            return 1024;
        }
        if (modelName.contains("nomic-embed-text")) {
            return 768;
        }
        return 768;
    }

    private Integer tryParseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String buildMigratedEmbeddingTableName(int dimension) {
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "memory_segments_d" + dimension + "_" + suffix;
    }

    private Map<String, Object> toSkillSummary(FileSystemSkill skill) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", skill.name());
        summary.put("description", skill.description());
        summary.put("basePath", skill.basePath().toString());
        summary.put("resourceCount", skill.resources() == null ? 0 : skill.resources().size());
        summary.put("scriptCount", countScripts(skill.basePath()));
        summary.put("resources", skill.resources() == null
                ? List.of()
                : skill.resources().stream()
                .map(resource -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("relativePath", resource.relativePath());
                    return item;
                })
                .toList());
        summary.put("scripts", listScripts(skill.basePath()));
        return summary;
    }

    private long countScripts(Path basePath) {
        return listScripts(basePath).size();
    }

    private List<Map<String, Object>> listScripts(Path basePath) {
        if (basePath == null) {
            return List.of();
        }
        Path scriptsDir = basePath.resolve("scripts");
        if (!Files.exists(scriptsDir)) {
            return List.of();
        }
        try {
            return Files.walk(scriptsDir)
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("relativePath", scriptsDir.relativize(path).toString().replace('\\', '/'));
                        item.put("fileName", path.getFileName().toString());
                        return item;
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
