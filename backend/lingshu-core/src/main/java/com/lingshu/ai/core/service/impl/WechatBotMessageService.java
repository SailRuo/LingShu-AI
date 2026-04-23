package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.TurnTimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WechatBotMessageService {
    private static final Logger log = LoggerFactory.getLogger(WechatBotMessageService.class);

    private final RestTemplate restTemplate;
    private final SettingService settingService;
    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, String> updateBufMap = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Set<String> pollingAccounts = ConcurrentHashMap.newKeySet();

    private final Map<String, String> typingTicketCache = new ConcurrentHashMap<>();

    public WechatBotMessageService(RestTemplate restTemplate, SettingService settingService, ChatService chatService) {
        this.restTemplate = restTemplate;
        this.settingService = settingService;
        this.chatService = chatService;

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
        if (botToken != null && !botToken.isEmpty()) {
            headers.set("Authorization", botToken.startsWith("Bearer ") ? botToken : "Bearer " + botToken);
        }
        return headers;
    }

    @Scheduled(fixedDelay = 1000)
    public void pollMessages() {
        List<Map<String, Object>> accounts = settingService.getWechatBotAccounts();
        
        for (Map<String, Object> account : accounts) {
            String accountId = (String) account.get("accountId");
            String status = (String) account.get("status");
            String botToken = (String) account.get("botToken");
            String baseUrl = (String) account.get("baseUrl");
            
            if (accountId == null || !"confirmed".equals(status) || botToken == null || botToken.isEmpty()) {
                continue;
            }
            
            if (pollingAccounts.contains(accountId)) {
                continue;
            }
            
            pollingAccounts.add(accountId);
            
            try {
                pollAccountMessages(accountId, botToken, baseUrl);
            } finally {
                pollingAccounts.remove(accountId);
            }
        }
    }

    private void pollAccountMessages(String accountId, String botToken, String baseUrl) {
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        try {
            String url = baseUrl + "/ilink/bot/getupdates";
            Map<String, Object> body = new HashMap<>();
            body.put("get_updates_buf", updateBufMap.getOrDefault(accountId, ""));
            Map<String, Object> baseInfo = new HashMap<>();
            baseInfo.put("channel_version", "2.1.3");
            body.put("base_info", baseInfo);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, createHeaders(botToken));
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);

            if (response.getBody() != null) {
                String responseBodyStr = new String(response.getBody(), StandardCharsets.UTF_8);
                Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);

                Integer errcode = (Integer) responseBody.get("errcode");
                if (errcode != null && errcode == -14) {
                    log.warn("微信会话已过期 (errcode: -14)，账户: {}, 更新状态为 session_timeout", accountId);
                    Map<String, Object> account = settingService.getWechatBotAccount(accountId);
                    if (account != null) {
                        account.put("status", "session_timeout");
                        settingService.saveWechatBotAccount(account);
                    }
                    return;
                }

                if (responseBody.containsKey("get_updates_buf")) {
                    updateBufMap.put(accountId, (String) responseBody.get("get_updates_buf"));
                }
                if (responseBody.containsKey("msgs")) {
                    List<Map<String, Object>> msgs = (List<Map<String, Object>>) responseBody.get("msgs");
                    for (Map<String, Object> msg : msgs) {
                        handleMessage(msg, botToken, baseUrl, accountId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("微信长轮询出错，账户 {}: {}", accountId, e.getMessage());
        }
    }

    private void handleMessage(Map<String, Object> messageItem, String botToken, String baseUrl, String accountId) {
        try {
            String fromUserId = (String) messageItem.get("from_user_id");
            String toUserId = (String) messageItem.get("to_user_id");
            Integer messageType = (Integer) messageItem.get("message_type");
            String contextToken = (String) messageItem.get("context_token");

            if (messageType == null || messageType != 1) {
                return;
            }

            List<Map<String, Object>> itemList = (List<Map<String, Object>>) messageItem.get("item_list");
            if (itemList == null || itemList.isEmpty()) return;

            StringBuilder textBuilder = new StringBuilder();
            for (Map<String, Object> item : itemList) {
                Integer itemType = (Integer) item.get("type");
                if (itemType != null && itemType == 1) {
                    Map<String, Object> textItem = (Map<String, Object>) item.get("text_item");
                    if (textItem != null) {
                        String t = (String) textItem.get("text");
                        if (t != null) {
                            textBuilder.append(t);
                        }
                    }
                } else if (itemType != null && itemType == 3) {
                    Map<String, Object> voiceItem = (Map<String, Object>) item.get("voice_item");
                    if (voiceItem != null) {
                        String t = (String) voiceItem.get("text");
                        if (t != null && !t.isBlank()) {
                            textBuilder.append(t);
                            log.info("提取到微信用户的语音识别文本: {}", t);
                        }
                    }
                }
            }
            String text = textBuilder.toString();
            if (text.trim().isEmpty()) return;

            String wechatUserId = "wechat:" + fromUserId;
            log.info("收到微信用户 [{}] 的消息：{}, 使用 userId: {}", fromUserId, text, wechatUserId);

            sendTyping(fromUserId, contextToken, botToken, baseUrl, 1);

            log.info("开始调用 ChatService.streamChat, userId={}, message={}", wechatUserId, text);
            chatService.streamChat(ChatService.ChatStreamRequest.builder()
                    .message(text)
                    .userId(wechatUserId)
                    .toolEventListener(new ChatService.ToolEventListener() {
                @Override
                public void onToolEnd(String toolCallId, String toolName, String arguments, 
                                      String result, boolean isError, List<TurnTimelineService.ArtifactPayload> artifacts) {
                    if (!artifacts.isEmpty()) {
                        log.info("工具 {} 返回了 {} 个产物，准备发送给微信用户", toolName, artifacts.size());
                        for (TurnTimelineService.ArtifactPayload artifact : artifacts) {
                            if ("image".equals(artifact.artifactType())) {
                                log.info("检测到图片产物，准备发送给微信用户 [{}]", fromUserId);
                                sendImageMessage(fromUserId, toUserId, contextToken, 
                                                artifact.base64Data(), artifact.mimeType(), 
                                                botToken, baseUrl);
                            }
                        }
                    }
                }
            })
                    .build())
                    .reduce("", String::concat)
                    .doOnNext(fullResponse -> {
                        log.info("收到 AI 完整响应，长度={}, 内容={}", fullResponse.length(),
                                fullResponse.length() > 100 ? fullResponse.substring(0, 100) + "..." : fullResponse);
                        String cleanResponse = fullResponse.replaceAll("(?s)\u0001REASONING\u0001.*?\u0001/REASONING\u0001", "");
                        log.info("清理 reasoning 后的响应，长度={}", cleanResponse.length());
                        if (cleanResponse.trim().isEmpty()) {
                            log.warn("AI 响应为空，不发送消息给用户");
                        } else {
                            sendMessage(fromUserId, toUserId, contextToken, cleanResponse, botToken, baseUrl);
                        }
                        sendTyping(fromUserId, contextToken, botToken, baseUrl, 2);
                    })
                    .doOnError(e -> {
                        log.error("对话生成失败：{}", e.getMessage(), e);
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
            msg.put("from_user_id", "");
            msg.put("to_user_id", toUserId);
            msg.put("client_id", "openclaw-weixin-" + String.format("%08x", random.nextInt()));
            msg.put("message_type", 2);
            msg.put("message_state", 2);
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
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("已回复微信用户 [{}]: {}, 响应: {}", toUserId, text, response.getBody());
        } catch (Exception e) {
            log.error("发送微信消息失败: {}", e.getMessage());
        }
    }

    private void sendImageMessage(String toUserId, String botUserId, String contextToken,
                                  String base64Data, String mimeType, String botToken, String baseUrl) {
        try {
            log.info("开始发送图片给微信用户 [{}], mimeType={}", toUserId, mimeType);
            
            byte[] imageData = Base64.getDecoder().decode(base64Data);
            log.info("图片解码成功，大小: {} bytes", imageData.length);
            
            byte[] aesKey = new byte[16];
            secureRandom.nextBytes(aesKey);
            
            byte[] encryptedData = encryptAES(imageData, aesKey);
            log.info("图片加密成功，加密后大小: {} bytes", encryptedData.length);
            
            String fileKey = UUID.randomUUID().toString().replace("-", "");
            String rawMd5 = getMD5(imageData);
            String aesKeyHex = bytesToHex(aesKey);
            
            String uploadUrl = getUploadUrl(botToken, baseUrl, toUserId, imageData.length, 
                                            encryptedData.length, fileKey, rawMd5, aesKeyHex);
            
            if (uploadUrl == null) {
                log.error("获取上传 URL 失败");
                return;
            }
            log.info("获取上传 URL 成功: {}", uploadUrl);
            
            String downloadParam = uploadToCdn(uploadUrl, encryptedData);
            if (downloadParam == null) {
                log.error("上传图片到 CDN 失败");
                return;
            }
            log.info("上传图片成功，获得凭证: {}", downloadParam.substring(0, Math.min(30, downloadParam.length())));
            
            String aesKeyB64 = Base64.getEncoder().encodeToString(aesKeyHex.getBytes(StandardCharsets.UTF_8));
            
            sendImageMessageRequest(toUserId, contextToken, downloadParam, aesKeyB64, 
                                    encryptedData.length, botToken, baseUrl);
            
            log.info("已发送图片给微信用户 [{}]", toUserId);
        } catch (Exception e) {
            log.error("发送图片失败: {}", e.getMessage(), e);
        }
    }

    private byte[] encryptAES(byte[] data, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    private String getUploadUrl(String botToken, String baseUrl, String toUserId, 
                                int rawSize, int encryptedSize, String fileKey, 
                                String rawMd5, String aesKeyHex) throws Exception {
        String url = baseUrl + "/ilink/bot/getuploadurl";
        
        Map<String, Object> body = new HashMap<>();
        body.put("filekey", fileKey);
        body.put("media_type", 1);
        body.put("to_user_id", toUserId);
        body.put("rawsize", rawSize);
        body.put("rawfilemd5", rawMd5);
        body.put("filesize", encryptedSize);
        body.put("no_need_thumb", true);
        body.put("aeskey", aesKeyHex);
        
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, createHeaders(botToken));
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("upload_full_url")) {
            return (String) responseBody.get("upload_full_url");
        }
        
        log.warn("getuploadurl 响应: {}", responseBody);
        return null;
    }

    private String uploadToCdn(String uploadUrl, byte[] encryptedData) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> entity = new HttpEntity<>(encryptedData, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, String.class);
        
        String downloadParam = response.getHeaders().getFirst("x-encrypted-param");
        if (downloadParam != null) {
            return downloadParam;
        }
        
        try {
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
            if (responseBody.containsKey("x-encrypted-param")) {
                return (String) responseBody.get("x-encrypted-param");
            }
        } catch (Exception e) {
            log.warn("解析 CDN 响应失败: {}", e.getMessage());
        }
        
        log.warn("CDN 响应头: {}", response.getHeaders());
        return null;
    }

    private void sendImageMessageRequest(String toUserId, String contextToken, 
                                         String downloadParam, String aesKeyB64, 
                                         int encryptedSize, String botToken, String baseUrl) throws Exception {
        String url = baseUrl + "/ilink/bot/sendmessage";
        
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> msg = new HashMap<>();
        msg.put("from_user_id", "");
        msg.put("to_user_id", toUserId);
        msg.put("client_id", "openclaw-weixin-" + String.format("%08x", random.nextInt()));
        msg.put("message_type", 2);
        msg.put("message_state", 2);
        msg.put("context_token", contextToken);
        
        List<Map<String, Object>> itemList = new ArrayList<>();
        Map<String, Object> imageItemWrapper = new HashMap<>();
        imageItemWrapper.put("type", 2);
        
        Map<String, Object> imageItem = new HashMap<>();
        Map<String, Object> media = new HashMap<>();
        media.put("encrypt_query_param", downloadParam);
        media.put("aes_key", aesKeyB64);
        media.put("encrypt_type", 1);
        imageItem.put("media", media);
        imageItem.put("mid_size", encryptedSize);
        
        imageItemWrapper.put("image_item", imageItem);
        itemList.add(imageItemWrapper);
        msg.put("item_list", itemList);
        
        body.put("msg", msg);
        body.put("base_info", Map.of("channel_version", "2.1.3"));
        
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, createHeaders(botToken));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        
        log.info("发送图片消息响应: {}", response.getBody());
    }

    private String getMD5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data);
        return bytesToHex(digest);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
