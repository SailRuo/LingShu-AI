package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import com.lingshu.ai.infrastructure.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SettingServiceImpl implements SettingService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettingServiceImpl.class);

    private final SystemSettingRepository settingRepository;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private static final String DEFAULT_ID = "DEFAULT";
    private static final String WECHAT_BOT_ID = "WECHAT_BOT";
    private static final String REDIS_KEY = "lingshu:settings";
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
            Map<String, Object> settings = new java.util.HashMap<>();
            settings.put("wechatBotAccounts", new java.util.ArrayList<>());
            defaultSetting.setSettings(settings);
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
    private void updateWechatBotCache(SystemSetting setting) {
        try {
            String json = objectMapper.writeValueAsString(setting);
            redisTemplate.opsForValue().set(REDIS_KEY_WECHAT_BOT, json);
        } catch (Exception e) {
            log.error("更新微信 Bot Redis 缓存失败：{}", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getWechatBotAccounts() {
        SystemSetting setting = getWechatBotSetting();
        return setting.getWechatBotAccounts();
    }

    @Override
    public void saveWechatBotAccount(Map<String, Object> account) {
        SystemSetting setting = getWechatBotSetting();
        setting.addWechatBotAccount(account);
        saveWechatBotSetting(setting);
        log.info("微信 Bot 账户已保存: accountId={}", account.get("accountId"));
    }

    @Override
    public void removeWechatBotAccount(String accountId) {
        SystemSetting setting = getWechatBotSetting();
        setting.removeWechatBotAccount(accountId);
        saveWechatBotSetting(setting);
        log.info("微信 Bot 账户已删除: accountId={}", accountId);
    }

    @Override
    public Map<String, Object> getWechatBotAccount(String accountId) {
        SystemSetting setting = getWechatBotSetting();
        return setting.getWechatBotAccount(accountId);
    }
}
