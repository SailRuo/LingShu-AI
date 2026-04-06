package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.SettingService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WechatBotAuthService {

    private final RestTemplate restTemplate;
    private final SettingService settingService;
    private final Random random = new Random();

    private final Map<String, String> pendingAuthBaseUrl = new HashMap<>();

    public WechatBotAuthService(RestTemplate restTemplate, SettingService settingService) {
        this.restTemplate = restTemplate;
        this.settingService = settingService;

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

    private String getDefaultBaseUrl() {
        return "https://ilinkai.weixin.qq.com";
    }

    public Map<String, Object> getLoginQrCode() {
        String baseUrl = getDefaultBaseUrl();
        
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String url = baseUrl + "/ilink/bot/get_bot_qrcode?bot_type=3";
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> result = response.getBody();
        
        if (result != null) {
            String qrcode = (String) result.get("qrcode");
            if (qrcode != null) {
                pendingAuthBaseUrl.put(qrcode, baseUrl);
            }
        }
        
        return result;
    }

    public Map<String, Object> getLoginStatus(String qrcode) {
        String baseUrl = pendingAuthBaseUrl.getOrDefault(qrcode, getDefaultBaseUrl());
        
        String url = baseUrl + "/ilink/bot/get_qrcode_status?qrcode=" + qrcode;
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null) {
            Integer errcode = (Integer) body.get("errcode");
            if (errcode != null && errcode == -14) {
                pendingAuthBaseUrl.remove(qrcode);
                return body;
            }

            String status = (String) body.get("status");
            
            if ("scaned_but_redirect".equals(status)) {
                String redirectHost = (String) body.get("redirect_host");
                if (redirectHost != null && !redirectHost.isEmpty()) {
                    String newBaseUrl = "https://" + redirectHost;
                    pendingAuthBaseUrl.put(qrcode, newBaseUrl);
                }
            } else if ("confirmed".equals(status)) {
                String token = (String) body.get("bot_token");
                String ilinkUserId = (String) body.get("ilink_user_id");
                String ilinkBotId = (String) body.get("ilink_bot_id");
                String respBaseUrl = (String) body.get("baseurl");

                if (token != null && !token.isEmpty()) {
                    String accountId = ilinkUserId != null && !ilinkUserId.isEmpty() 
                        ? ilinkUserId 
                        : generateAccountIdFromToken(token);
                    
                    Map<String, Object> account = new HashMap<>();
                    account.put("accountId", accountId);
                    account.put("botToken", token);
                    account.put("status", "confirmed");
                    account.put("lastLoginTime", LocalDateTime.now().toString());
                    
                    if (ilinkBotId != null) {
                        account.put("ilinkBotId", ilinkBotId);
                    }
                    if (ilinkUserId != null) {
                        account.put("ilinkUserId", ilinkUserId);
                    }
                    
                    if (respBaseUrl != null && !respBaseUrl.isEmpty()) {
                        if (!respBaseUrl.startsWith("http")) {
                            respBaseUrl = "https://" + respBaseUrl;
                        }
                        account.put("baseUrl", respBaseUrl);
                    } else {
                        account.put("baseUrl", baseUrl);
                    }
                    
                    settingService.saveWechatBotAccount(account);
                    pendingAuthBaseUrl.remove(qrcode);
                }
            }
        }

        return body;
    }

    private String generateAccountIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        String cleanToken = token.replace("Bearer ", "").replace("bearer ", "");
        if (cleanToken.length() >= 16) {
            return cleanToken.substring(0, 16);
        }
        return cleanToken;
    }
}
