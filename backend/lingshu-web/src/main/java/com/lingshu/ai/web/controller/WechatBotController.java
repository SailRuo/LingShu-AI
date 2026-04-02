package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.impl.WechatBotAuthService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/wechat-bot")
public class WechatBotController {

    private final WechatBotAuthService wechatBotAuthService;
    private final SettingService settingService;

    public WechatBotController(WechatBotAuthService wechatBotAuthService, SettingService settingService) {
        this.wechatBotAuthService = wechatBotAuthService;
        this.settingService = settingService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        SystemSetting setting = settingService.getWechatBotSetting();
        Map<String, Object> config = setting.getWechatBotConfig();

        // 脱敏处理 botToken
        String token = (String) config.get("botToken");
        if (token != null && token.length() > 10) {
            String maskedToken = token.substring(0, 10) + "...";
            config.put("botToken", maskedToken);
        }

        return ResponseEntity.ok(config);
    }
    @PostMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> getQrCode() {
        Map<String, Object> response = wechatBotAuthService.getLoginQrCode();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam String qrcode) {
        Map<String, Object> response = wechatBotAuthService.getLoginStatus(qrcode);
        return ResponseEntity.ok(response);
    }
}
