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
    private static final String DEFAULT_ID = "DEFAULT";

    public SettingServiceImpl(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @PostConstruct
    public void init() {
        SystemSetting setting = getSetting();
        log.info("系统设置已加载 | 来源: {} | 模型: {} | 端点: {}",
                setting.getSource(), setting.getChatModel(), setting.getBaseUrl());
    }

    @Override
    public SystemSetting getSetting() {
        SystemSetting setting = settingRepository.findById(DEFAULT_ID).orElseGet(() -> {
            log.warn("数据库中未找到系统设置，创建默认配置");
            SystemSetting defaultSetting = SystemSetting.builder()
                    .id(DEFAULT_ID)
                    .source("ollama")
                    .chatModel("qwen3.5:4b")
                    .baseUrl("http://localhost:11434")
                    .apiKey("")
                    .proactiveEnabled(true)
                    .inactiveThresholdMinutes(5)
                    .greetingCooldownSeconds(300)
                    .inactiveCheckIntervalMs(3600000L)
                    .build();
            return settingRepository.save(defaultSetting);
        });
        log.debug("获取系统设置 | 来源: {} | 模型: {} | 端点: {}",
                setting.getSource(), setting.getChatModel(), setting.getBaseUrl());
        return setting;
    }

    @Override
    public void saveSetting(SystemSetting setting) {
        setting.setId(DEFAULT_ID);
        SystemSetting saved = settingRepository.save(setting);
        log.info("系统设置已保存 | 来源: {} | 模型: {} | 端点: {}",
                saved.getSource(), saved.getChatModel(), saved.getBaseUrl());
    }
}
