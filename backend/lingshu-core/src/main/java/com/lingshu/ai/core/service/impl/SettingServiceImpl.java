package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import com.lingshu.ai.infrastructure.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SettingServiceImpl implements SettingService {

    private final SystemSettingRepository settingRepository;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private static final String DEFAULT_ID = "DEFAULT";
    private static final String REDIS_KEY = "lingshu:settings";

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
        log.info("系统设置已同步 | 来源: {} | 模型: {} | 端点: {}",
                setting.getSource(), setting.getChatModel(), setting.getBaseUrl());
    }

    @Override
    public SystemSetting getSetting() {
        // 1. Try Redis first
        try {
            String cached = redisTemplate.opsForValue().get(REDIS_KEY);
            if (cached != null && !cached.isBlank()) {
                log.debug("从 Redis 获取系统设置成功");
                return objectMapper.readValue(cached, SystemSetting.class);
            }
        } catch (Exception e) {
            log.warn("从 Redis 获取设置失败: {}", e.getMessage());
        }

        // 2. Fetch from DB
        SystemSetting setting = settingRepository.findById(DEFAULT_ID).orElseGet(() -> {
            log.warn("数据库中未找到系统设置，创建默认配置");
            SystemSetting defaultSetting = SystemSetting.builder()
                    .id(DEFAULT_ID)
                    .source("ollama")
                    .chatModel("qwen2.5:latest")
                    .baseUrl("http://localhost:11434")
                    .apiKey("")
                    .proactiveEnabled(true)
                    .inactiveThresholdMinutes(5)
                    .greetingCooldownSeconds(300)
                    .inactiveCheckIntervalMs(3600000L)
                    .build();
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
        log.info("系统设置已更新到 DB 和 Redis | 来源: {} | 模型: {}",
                saved.getSource(), saved.getChatModel());
    }

    private void updateCache(SystemSetting setting) {
        try {
            String json = objectMapper.writeValueAsString(setting);
            redisTemplate.opsForValue().set(REDIS_KEY, json);
            log.debug("系统设置已缓存到 Redis");
        } catch (Exception e) {
            log.error("更新 Redis 缓存失败: {}", e.getMessage());
        }
    }
}
