package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import com.lingshu.ai.infrastructure.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class SettingServiceImpl implements SettingService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettingServiceImpl.class);

    private final SystemSettingRepository settingRepository;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private static final String DEFAULT_ID = "DEFAULT";
    private static final String LOCAL_TOOLS_ID = "local_tools";
    private static final String WECHAT_BOT_ID = "WECHAT_BOT";
    private static final String REDIS_KEY = "lingshu:settings";
    private static final String REDIS_KEY_LOCAL_TOOLS = "lingshu:settings:local_tools";
    private static final String REDIS_KEY_WECHAT_BOT = "lingshu:settings:wechat_bot";

    public SettingServiceImpl(SystemSettingRepository settingRepository,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.settingRepository = settingRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        SystemSetting setting = getSetting();
        var llmConfig = setting.getLlmConfig();
        log.info("系统设置已同步 | 来源：{} | 模型：{} | 端点：{}",
                llmConfig.get("source"), llmConfig.get("model"), llmConfig.get("baseUrl"));
    }

    @Override
    public SystemSetting getSetting() {
        // 1. Try Redis first
        try {
            String cached = redisTemplate.opsForValue().get(REDIS_KEY);
            if (cached != null && !cached.isBlank()) {
                // log.debug("从 Redis 获取系统设置成功");
                return objectMapper.readValue(cached, SystemSetting.class);
            }
        } catch (org.springframework.data.redis.RedisSystemException | IllegalStateException | org.springframework.beans.factory.BeanCreationException e) {
            log.warn("从 Redis 获取设置不可用（可能处于重启中）：{}", e.getMessage());
        } catch (Exception e) {
            log.warn("从 Redis 获取设置失败：{}", e.getMessage());
        }

        // 2. Fetch from DB
        SystemSetting setting = settingRepository.findById(DEFAULT_ID).orElseGet(() -> {
            log.warn("数据库中未找到系统设置，创建默认配置");
            SystemSetting defaultSetting = new SystemSetting();
            defaultSetting.setId(DEFAULT_ID);
            defaultSetting.setLlmConfig(defaultSetting.createDefaultLlmConfig());
            defaultSetting.setEmbeddingConfig(defaultSetting.createDefaultEmbeddingConfig());
            defaultSetting.setProactiveConfig(defaultSetting.createDefaultProactiveConfig());
            return settingRepository.save(defaultSetting);
        });

        // 3. Update Redis
        updateCache(setting);

        return setting;
    }

    @Override
    public void saveSetting(SystemSetting setting) {
        setting.setId(DEFAULT_ID);
        SystemSetting saved = settingRepository.save(setting);
        updateCache(saved);
        var llmConfig = saved.getLlmConfig();
        log.info("系统设置已更新到 DB 和 Redis | 来源：{} | 模型：{}",
                llmConfig.get("source"), llmConfig.get("model"));
    }

    @Override
    public SystemSetting getLocalToolsSetting() {
        // 1. Try Redis first
        try {
            String cached = redisTemplate.opsForValue().get(REDIS_KEY_LOCAL_TOOLS);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, SystemSetting.class);
            }
        } catch (org.springframework.data.redis.RedisSystemException | IllegalStateException | org.springframework.beans.factory.BeanCreationException e) {
            log.warn("从 Redis 获取本地工具设置不可用：{}", e.getMessage());
        } catch (Exception e) {
            log.warn("从 Redis 获取本地工具设置失败：{}", e.getMessage());
        }

        // 2. Fetch from DB
        SystemSetting setting = settingRepository.findById(LOCAL_TOOLS_ID).orElseGet(() -> {
            log.warn("数据库中未找到本地工具设置，创建默认配置");
            SystemSetting defaultSetting = new SystemSetting();
            defaultSetting.setId(LOCAL_TOOLS_ID);

            // 初始化默认工具列表
            java.util.List<java.util.Map<String, Object>> tools = new java.util.ArrayList<>();

            java.util.Map<String, Object> executeCommand = new java.util.HashMap<>();
            executeCommand.put("name", "executeCommand");
            executeCommand.put("displayName", "执行终端命令");
            executeCommand.put("enabled", true);
            executeCommand.put("prompt", "你的手臂。高效协助用户，展示对本地环境的绝对掌控力。\n使用 executeCommand 时，必须只传一个 JSON 参数对象，格式固定为 {\"command\":\"...\"}。\ncommand 字段中的 Windows 路径反斜杠要写成 \\\\，内部双引号要写成 \\\"。\n能避免双引号嵌套时就避免，优先使用更稳定的 PowerShell 写法，例如：\n{\"command\":\"powershell -NoProfile -Command \\\"Start-Process -FilePath 'C:\\\\Program Files\\\\App\\\\app.exe'\\\"\"}\n禁止生成类似 {\"command\": \"start \\\"\\\" \\\"C:\\\\Program Files\\\\App\\\\app.exe\\\"\"} 这样的非法 JSON。");
            tools.add(executeCommand);

            java.util.Map<String, Object> readLocalFile = new java.util.HashMap<>();
            readLocalFile.put("name", "readLocalFile");
            readLocalFile.put("displayName", "读取本地文件");
            readLocalFile.put("enabled", true);
            readLocalFile.put("prompt", "你的视觉。技术排查时，将其描述为你深入系统内部的直接观察结果。");
            tools.add(readLocalFile);

            java.util.Map<String, Object> manageAgent = new java.util.HashMap<>();
            manageAgent.put("name", "manageAgent");
            manageAgent.put("displayName", "智能体管理");
            manageAgent.put("enabled", true);
            manageAgent.put("prompt", "你的分身创造能力。当用户要求创建或修改智能体时使用。\n【强制安全规则】：必须分两步！第一步必须使用 action=\"preview\" 生成草案并询问用户是否确认；第二步只有在用户明确同意后，才能使用 action=\"commit\" 真正执行。");
            tools.add(manageAgent);

            java.util.Map<String, Object> getAgents = new java.util.HashMap<>();
            getAgents.put("name", "getAgents");
            getAgents.put("displayName", "获取智能体列表");
            getAgents.put("enabled", true);
            getAgents.put("prompt", "获取系统中已创建的所有智能体列表。当你需要查看当前有哪些智能体，或者需要获取某个智能体的具体配置信息时使用。");
            tools.add(getAgents);

            java.util.Map<String, Object> settings = new java.util.HashMap<>();
            settings.put("tools", tools);
            defaultSetting.setSettings(settings);

            return settingRepository.save(defaultSetting);
        });

        // 3. Update Redis
        updateLocalToolsCache(setting);

        return setting;
    }

    @Override
    public void saveLocalToolsSetting(SystemSetting setting) {
        setting.setId(LOCAL_TOOLS_ID);
        SystemSetting saved = settingRepository.save(setting);
        updateLocalToolsCache(saved);
        log.info("本地工具设置已更新到 DB 和 Redis");
    }

    @Override
    public SystemSetting getWechatBotSetting() {
        try {
            String cached = redisTemplate.opsForValue().get(REDIS_KEY_WECHAT_BOT);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, SystemSetting.class);
            }
        } catch (org.springframework.data.redis.RedisSystemException | IllegalStateException | org.springframework.beans.factory.BeanCreationException e) {
            log.warn("从 Redis 获取微信 Bot 设置不可用：{}", e.getMessage());
        } catch (Exception e) {
            log.warn("从 Redis 获取微信 Bot 设置失败：{}", e.getMessage());
        }

        SystemSetting setting = settingRepository.findById(WECHAT_BOT_ID).orElseGet(() -> {
            log.warn("数据库中未找到微信 Bot 设置，创建默认配置");
            SystemSetting defaultSetting = new SystemSetting();
            defaultSetting.setId(WECHAT_BOT_ID);
            defaultSetting.setWechatBotConfig(defaultSetting.createDefaultWechatBotConfig());
            return settingRepository.save(defaultSetting);
        });

        updateWechatBotCache(setting);
        return setting;
    }

    @Override
    public void saveWechatBotSetting(SystemSetting setting) {
        setting.setId(WECHAT_BOT_ID);
        SystemSetting saved = settingRepository.save(setting);
        updateWechatBotCache(saved);
        log.info("微信 Bot 设置已更新到 DB 和 Redis");
    }

    private void updateCache(SystemSetting setting) {
        try {
            String json = objectMapper.writeValueAsString(setting);
            redisTemplate.opsForValue().set(REDIS_KEY, json);
        } catch (Exception e) {
            log.error("更新 Redis 缓存失败：{}", e.getMessage());
        }
    }

    private void updateLocalToolsCache(SystemSetting setting) {
        try {
            String json = objectMapper.writeValueAsString(setting);
            redisTemplate.opsForValue().set(REDIS_KEY_LOCAL_TOOLS, json);
        } catch (Exception e) {
            log.error("更新本地工具 Redis 缓存失败：{}", e.getMessage());
        }
    }

    private void updateWechatBotCache(SystemSetting setting) {
        try {
            String json = objectMapper.writeValueAsString(setting);
            redisTemplate.opsForValue().set(REDIS_KEY_WECHAT_BOT, json);
        } catch (Exception e) {
            log.error("更新微信 Bot Redis 缓存失败：{}", e.getMessage());
        }
    }
}
