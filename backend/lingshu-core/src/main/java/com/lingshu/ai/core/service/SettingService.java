package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.SystemSetting;

public interface SettingService {
    /**
     * 获取系统当前设置。
     */
    SystemSetting getSetting();

    /**
     * 保存系统设置并同步内存状态。
     */
    void saveSetting(SystemSetting setting);
}
