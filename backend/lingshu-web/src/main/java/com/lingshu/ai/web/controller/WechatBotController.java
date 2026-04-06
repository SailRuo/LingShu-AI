package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.impl.WechatBotAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/settings/wechat-bot")
public class WechatBotController {

    private final WechatBotAuthService wechatBotAuthService;
    private final SettingService settingService;

    public WechatBotController(WechatBotAuthService wechatBotAuthService, SettingService settingService) {
        this.wechatBotAuthService = wechatBotAuthService;
        this.settingService = settingService;
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAccounts() {
        List<Map<String, Object>> accounts = settingService.getWechatBotAccounts();
        List<Map<String, Object>> maskedAccounts = new ArrayList<>();
        
        for (Map<String, Object> account : accounts) {
            Map<String, Object> masked = new HashMap<>(account);
            String token = (String) masked.get("botToken");
            if (token != null && token.length() > 10) {
                masked.put("botToken", token.substring(0, 10) + "...");
            }
            maskedAccounts.add(masked);
        }
        
        return ResponseEntity.ok(maskedAccounts);
    }

    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> removeAccount(@PathVariable String accountId) {
        settingService.removeWechatBotAccount(accountId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("accountId", accountId);
        result.put("message", "账户已删除");
        return ResponseEntity.ok(result);
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
