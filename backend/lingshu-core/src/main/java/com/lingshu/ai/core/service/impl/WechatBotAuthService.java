package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class WechatBotAuthService {

    private final RestTemplate restTemplate;
    private final SettingService settingService;
    private final Random random = new Random();

    public WechatBotAuthService(RestTemplate restTemplate, SettingService settingService) {
        this.restTemplate = restTemplate;
        this.settingService = settingService;

        // Add interceptor/converter to handle application/octet-stream as JSON
        for (org.springframework.http.converter.HttpMessageConverter<?> converter : this.restTemplate.getMessageConverters()) {
            if (converter instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) {
                org.springframework.http.converter.json.MappingJackson2HttpMessageConverter jsonConverter =
                        (org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) converter;
                java.util.List<org.springframework.http.MediaType> supportedMediaTypes =
                        new java.util.ArrayList<>(jsonConverter.getSupportedMediaTypes());
                supportedMediaTypes.add(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
                jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
            }
        }
    }

    /**
     * 生成动态的 X-WECHAT-UIN (无符号 32 位整数的 Base64)
     */
    private String generateUin() {
        long uin = random.nextInt() & 0xFFFFFFFFL;
        return Base64.getEncoder().encodeToString(String.valueOf(uin).getBytes());
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("AuthorizationType", "ilink_bot_token");
        headers.set("X-WECHAT-UIN", generateUin());
        return headers;
    }

    private String getBaseUrl() {
        SystemSetting setting = settingService.getWechatBotSetting();
        Map<String, Object> config = setting.getWechatBotConfig();
        String baseUrl = (String) config.getOrDefault("baseUrl", "https://ilinkai.weixin.qq.com");
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    public Map<String, Object> getLoginQrCode() {
        String url = getBaseUrl() + "/ilink/bot/get_bot_qrcode?bot_type=3";
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return response.getBody();
    }

    public Map<String, Object> getLoginStatus(String qrcode) {
        String url = getBaseUrl() + "/ilink/bot/get_qrcode_status?qrcode=" + qrcode;
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null) {
            String status = (String) body.get("status");
            SystemSetting setting = settingService.getWechatBotSetting();
            Map<String, Object> config = setting.getWechatBotConfig();
            boolean updated = false;

            if ("scaned_but_redirect".equals(status)) {
                String redirectHost = (String) body.get("redirect_host");
                if (redirectHost != null && !redirectHost.isEmpty()) {
                    String newBaseUrl = "https://" + redirectHost;
                    config.put("baseUrl", newBaseUrl);
                    updated = true;
                }
            } else if ("confirmed".equals(status)) {
                String token = (String) body.get("bot_token");
                String baseUrl = (String) body.get("baseurl");

                if (token != null && !token.isEmpty()) {
                    config.put("botToken", token);
                    config.put("status", "confirmed");
                    config.put("lastLoginTime", LocalDateTime.now().toString());
                    updated = true;
                }
                if (baseUrl != null && !baseUrl.isEmpty()) {
                    if (!baseUrl.startsWith("http")) {
                        baseUrl = "https://" + baseUrl;
                    }
                    config.put("baseUrl", baseUrl);
                    updated = true;
                }
            }

            if (updated) {
                setting.setWechatBotConfig(config);
                settingService.saveWechatBotSetting(setting);
            }
        }

        return body;
    }}
