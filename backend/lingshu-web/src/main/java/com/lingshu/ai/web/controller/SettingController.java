package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * 获取当前系统配置信息（模型、API Key 等）。
     */
    @GetMapping
    public SystemSetting getSetting() {
        return settingService.getSetting();
    }

    /**
     * 保存并应用新的系统配置。
     */
    @PostMapping
    public void saveSetting(@RequestBody SystemSetting setting) {
        settingService.saveSetting(setting);
    }
}
