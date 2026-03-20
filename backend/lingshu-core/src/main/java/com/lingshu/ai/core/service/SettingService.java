package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.SystemSetting;

public interface SettingService {
    SystemSetting getSetting();
    void saveSetting(SystemSetting setting);
}
