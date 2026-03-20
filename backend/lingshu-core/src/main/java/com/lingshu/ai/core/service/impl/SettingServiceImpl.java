package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import com.lingshu.ai.infrastructure.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class SettingServiceImpl implements SettingService {

    private final SystemSettingRepository settingRepository;
    private static final String DEFAULT_ID = "DEFAULT";

    public SettingServiceImpl(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    public SystemSetting getSetting() {
        return settingRepository.findById(DEFAULT_ID).orElseGet(() -> {
            SystemSetting defaultSetting = SystemSetting.builder()
                    .id(DEFAULT_ID)
                    .source("ollama")
                    .chatModel("qwen3.5:4b")
                    .baseUrl("http://localhost:11434")
                    .apiKey("")
                    .build();
            return settingRepository.save(defaultSetting);
        });
    }

    @Override
    public void saveSetting(SystemSetting setting) {
        setting.setId(DEFAULT_ID);
        settingRepository.save(setting);
    }
}
