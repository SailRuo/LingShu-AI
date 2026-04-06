package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.SystemSetting;
import java.util.List;
import java.util.Map;

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

    /**
     * 获取微信 Bot 配置 (WECHAT_BOT)。
     */
    SystemSetting getWechatBotSetting();

    /**
     * 保存微信 Bot 配置 (WECHAT_BOT)。
     */
    void saveWechatBotSetting(SystemSetting setting);

    /**
     * 获取所有微信 Bot 账户列表
     */
    List<Map<String, Object>> getWechatBotAccounts();

    /**
     * 添加或更新微信 Bot 账户
     */
    void saveWechatBotAccount(Map<String, Object> account);

    /**
     * 删除微信 Bot 账户
     */
    void removeWechatBotAccount(String accountId);

    /**
     * 根据 accountId 获取微信 Bot 账户
     */
    Map<String, Object> getWechatBotAccount(String accountId);
}
