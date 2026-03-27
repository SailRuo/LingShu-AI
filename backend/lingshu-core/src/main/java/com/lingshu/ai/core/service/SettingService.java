package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.SystemSetting;

public interface SettingService {
    /**
     * 获取系统当前设置 (DEFAULT)。
     */
    SystemSetting getSetting();

    /**
     * 保存系统设置并同步内存状态 (DEFAULT)。
     */
    void saveSetting(SystemSetting setting);

    /**
     * 获取本地工具配置 (local_tools)。
     */
    SystemSetting getLocalToolsSetting();

    /**
     * 保存本地工具配置 (local_tools)。
     */
    void saveLocalToolsSetting(SystemSetting setting);
}
