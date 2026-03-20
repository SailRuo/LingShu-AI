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

    @GetMapping
    public SystemSetting getSetting() {
        return settingService.getSetting();
    }

    @PostMapping
    public void saveSetting(@RequestBody SystemSetting setting) {
        settingService.saveSetting(setting);
    }
}
