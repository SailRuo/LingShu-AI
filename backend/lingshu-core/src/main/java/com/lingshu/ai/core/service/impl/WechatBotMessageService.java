package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WechatBotMessageService {
    private static final Logger log = LoggerFactory.getLogger(WechatBotMessageService.class);

    private final RestTemplate restTemplate;
    private final SettingService settingService;
    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String updateBuf = "";
    private final Random random = new Random();
    private boolean isPolling = false;

    // 缓存 typing_ticket，有效期理论上 24 小时
    private final Map<String, String> typingTicketCache = new ConcurrentHashMap<>();

    public WechatBotMessageService(RestTemplate restTemplate, SettingService settingService, ChatService chatService) {
        this.restTemplate = restTemplate;
        this.settingService = settingService;
        this.chatService = chatService;

        // Add support for application/octet-stream as JSON for iLink responses
        for (org.springframework.http.converter.HttpMessageConverter<?> converter : this.restTemplate.getMessageConverters()) {
            if (converter instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) {
                org.springframework.http.converter.json.MappingJackson2HttpMessageConverter jsonConverter =
                        (org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) converter;
                java.util.List<org.springframework.http.MediaType> supportedMediaTypes =
                        new java.util.ArrayList<>(jsonConverter.getSupportedMediaTypes());
                if (!supportedMediaTypes.contains(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)) {
                    supportedMediaTypes.add(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
                    jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
                }
            }
        }
    }

    private String generateUin() {
        long uin = random.nextInt() & 0xFFFFFFFFL;
        return Base64.getEncoder().encodeToString(String.valueOf(uin).getBytes(StandardCharsets.UTF_8));
    }

    private HttpHeaders createHeaders(String botToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("AuthorizationType", "ilink_bot_token");
        headers.set("X-WECHAT-UIN", generateUin());
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        if (botToken != null && !botToken.isEmpty()) {
            headers.set("Authorization", botToken.startsWith("Bearer ") ? botToken : "Bearer " + botToken);
        }
        return headers;
    }

    @Scheduled(fixedDelay = 1000)
    public void pollMessages() {
        if (isPolling) return;

        SystemSetting setting = settingService.getWechatBotSetting();
        Map<String, Object> config = setting.getWechatBotConfig();
        String status = (String) config.get("status");
        String botToken = (String) config.get("botToken");
        String baseUrl = (String) config.get("baseUrl");
        if (!"confirmed".equals(status) || botToken == null || botToken.isEmpty()) {
            return;
        }

        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        isPolling = true;
        try {
            String url = baseUrl + "/ilink/bot/getupdates";
            Map<String, Object> body = new HashMap<>();
            body.put("get_updates_buf", updateBuf);
            Map<String, Object> baseInfo = new HashMap<>();
            baseInfo.put("channel_version", "2.1.3");
            body.put("base_info", baseInfo);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, createHeaders(botToken));
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);

            if (response.getBody() != null) {
                String responseBodyStr = new String(response.getBody(), StandardCharsets.UTF_8);
                //log.debug("微信长轮询响应: {}", responseBodyStr);
                Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
                
                Integer errcode = (Integer) responseBody.get("errcode");
                if (errcode != null && errcode == -14) {
                    log.warn("微信会话已过期 (errcode: -14)，更新状态为 session_timeout");
                    Map<String, Object> config1 = setting.getWechatBotConfig();
                    config1.put("status", "session_timeout");
                    setting.setWechatBotConfig(config1);
                    settingService.saveWechatBotSetting(setting);
                    return;
                }
                
                if (responseBody.containsKey("get_updates_buf")) {
                    updateBuf = (String) responseBody.get("get_updates_buf");
                }
                if (responseBody.containsKey("msgs")) {
                    List<Map<String, Object>> msgs = (List<Map<String, Object>>) responseBody.get("msgs");
                    for (Map<String, Object> msg : msgs) {
                        handleMessage(msg, botToken, baseUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.error("微信长轮询出错: {}", e.getMessage());
            // 如果报错（比如超时），等待下一次循环
        } finally {
            isPolling = false;
        }
    }

    private void handleMessage(Map<String, Object> messageItem, String botToken, String baseUrl) {
        try {
            String fromUserId = (String) messageItem.get("from_user_id");
            String toUserId = (String) messageItem.get("to_user_id");
            Integer messageType = (Integer) messageItem.get("message_type");
            String contextToken = (String) messageItem.get("context_token");

            if (messageType == null || messageType != 1) { // 只处理类型为 1 的普通消息
                return;
            }

            List<Map<String, Object>> itemList = (List<Map<String, Object>>) messageItem.get("item_list");
            if (itemList == null || itemList.isEmpty()) return;

            StringBuilder textBuilder = new StringBuilder();
            for (Map<String, Object> item : itemList) {
                Integer itemType = (Integer) item.get("type");
                if (itemType != null && itemType == 1) { // 文本项
                    Map<String, Object> textItem = (Map<String, Object>) item.get("text_item");
                    if (textItem != null) {
                        String t = (String) textItem.get("text");
                        if (t != null) {
                            textBuilder.append(t);
                        }
                    }
                }
            }
            String text = textBuilder.toString();
            if (text.trim().isEmpty()) return;

            log.info("收到微信用户 [{}] 的消息: {}", fromUserId, text);

            // 发送 "正在输入..."
            sendTyping(fromUserId, contextToken, botToken, baseUrl, 1);

            // 聚合响应
            chatService.streamChat(text, null, fromUserId, null, null, null)
                    .reduce("", String::concat)
                    .doOnNext(fullResponse -> {
                        String cleanResponse = fullResponse.replaceAll("\u0001REASONING\u0001.*?\u0001/REASONING\u0001", "");
                        sendMessage(fromUserId, toUserId, contextToken, cleanResponse, botToken, baseUrl);
                        sendTyping(fromUserId, contextToken, botToken, baseUrl, 2);
                    })
                    .doOnError(e -> {
                        log.error("对话生成失败: {}", e.getMessage());
                        sendTyping(fromUserId, contextToken, botToken, baseUrl, 2);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("处理微信消息失败: {}", e.getMessage(), e);
        }
    }

    private void sendTyping(String fromUserId, String contextToken, String botToken, String baseUrl, int status) {
        try {
            String typingTicket = typingTicketCache.get(fromUserId);
            if (typingTicket == null) {
                // 获取 typing_ticket
                String url = baseUrl + "/ilink/bot/getconfig";
                Map<String, Object> body = new HashMap<>();
                body.put("ilink_user_id", fromUserId);
                body.put("context_token", contextToken);
                Map<String, Object> baseInfo = new HashMap<>();
                baseInfo.put("channel_version", "2.1.3");
                body.put("base_info", baseInfo);

                String jsonBody = objectMapper.writeValueAsString(body);
                HttpEntity<String> entity = new HttpEntity<>(jsonBody, createHeaders(botToken));
                ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
                if (response.getBody() != null) {
                    String responseBodyStr = new String(response.getBody(), StandardCharsets.UTF_8);
                    Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
                    if (responseBody.containsKey("typing_ticket")) {
                        typingTicket = (String) responseBody.get("typing_ticket");
                        typingTicketCache.put(fromUserId, typingTicket);
                    }
                }
            }

            if (typingTicket != null) {
                String url = baseUrl + "/ilink/bot/sendtyping";
                Map<String, Object> body = new HashMap<>();
                body.put("ilink_user_id", fromUserId);
                body.put("typing_ticket", typingTicket);
                body.put("status", status);

                String jsonBody = objectMapper.writeValueAsString(body);
                HttpEntity<String> entity = new HttpEntity<>(jsonBody, createHeaders(botToken));
                restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            }
        } catch (Exception e) {
            log.error("发送 typing 状态失败: {}", e.getMessage());
        }
    }

    private void sendMessage(String toUserId, String botUserId, String contextToken, String text, String botToken, String baseUrl) {
        try {
            String url = baseUrl + "/ilink/bot/sendmessage";
            Map<String, Object> body = new HashMap<>();

            Map<String, Object> msg = new HashMap<>();
            msg.put("from_user_id", ""); // 官方文档建议为空字符串
            msg.put("to_user_id", toUserId);
            msg.put("client_id", "bot-" + System.currentTimeMillis());
            msg.put("message_type", 2); // 2 表示由 Bot 发出
            msg.put("message_state", 2); // 2 表示 FINISH
            msg.put("context_token", contextToken);

            List<Map<String, Object>> itemList = new ArrayList<>();
            Map<String, Object> textItemWrapper = new HashMap<>();
            textItemWrapper.put("type", 1);
            Map<String, Object> textItem = new HashMap<>();
            textItem.put("text", text);
            textItemWrapper.put("text_item", textItem);
            itemList.add(textItemWrapper);
            msg.put("item_list", itemList);

            body.put("msg", msg);

            Map<String, Object> baseInfo = new HashMap<>();
            baseInfo.put("channel_version", "2.1.3");
            body.put("base_info", baseInfo);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, createHeaders(botToken));
            restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
            log.info("已回复微信用户 [{}]: {}", toUserId, text);
        } catch (Exception e) {
            log.error("发送微信消息失败: {}", e.getMessage());
        }
    }
}
