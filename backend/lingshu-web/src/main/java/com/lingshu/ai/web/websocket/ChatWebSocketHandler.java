package com.lingshu.ai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.AffinityService;
import com.lingshu.ai.core.service.AsrService;
import com.lingshu.ai.core.service.ChatSessionService;
import com.lingshu.ai.core.service.ProactiveService;
import com.lingshu.ai.core.util.SkillNameResolver;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import com.lingshu.ai.infrastructure.entity.UserState;
import com.lingshu.ai.core.service.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatService chatService;
    private final ProactiveService proactiveService;
    private final AffinityService affinityService;
    private final AsrService asrService;
    private final ChatSessionService chatSessionService;
    private final SettingService settingService;
    
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> sessionChatMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatService chatService, 
                                ProactiveService proactiveService,
                                AffinityService affinityService,
                                AsrService asrService,
                                ChatSessionService chatSessionService,
                                SettingService settingService) {
        this.chatService = chatService;
        this.proactiveService = proactiveService;
        this.affinityService = affinityService;
        this.asrService = asrService;
        this.chatSessionService = chatSessionService;
        this.settingService = settingService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        //log.info("WebSocket 连接建立: {}", sessionId);
        
        sendMessage(session, Map.of(
            "type", "connected",
            "sessionId", sessionId,
            "message", "连接成功"
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        //log.debug("收到 WebSocket 消息: {}", payload != null && payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
        
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            
            switch (type) {
                case "register" -> handleRegister(session, data);
                case "chat" -> handleChat(session, data);
                case "history" -> handleHistory(session, data);
                case "audio" -> handleAudio(session, data);
                case "ping" -> sendMessage(session, Map.of("type", "pong"));
                default -> log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage());
            sendMessage(session, Map.of(
                "type", "error",
                "message", "消息处理失败: " + e.getMessage()
            ));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionUserMap.remove(sessionId);
        sessionChatMap.remove(sessionId);
        //log.info("WebSocket 连接关闭: {}, 状态: {}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 传输错误: {}", exception.getMessage());
    }

    private void handleRegister(WebSocketSession session, Map<String, Object> data) throws IOException {
        String userId = resolveUserId(session, data.get("userId"));
        Long chatSessionId = resolveChatSessionId(userId, data.get("sessionId"), session.getId());
        sessionUserMap.put(session.getId(), userId);
        sessionChatMap.put(session.getId(), chatSessionId);
        
        //log.info("用户注册: {} -> {}", session.getId(), userId);
        
        sendMessage(session, Map.of(
            "type", "registered",
            "userId", userId,
            "chatSessionId", chatSessionId
        ));
        
        UserState state = affinityService.getOrCreateUserState(userId);
        sendMessage(session, Map.of(
            "type", "userState",
            "affinity", state.getAffinity(),
            "relationshipStage", state.getRelationshipStage()
        ));
    }

    private void handleChat(WebSocketSession session, Map<String, Object> data) throws IOException {
        String message = (String) data.get("message");
        List<String> images = (List<String>) data.get("images");
        Long agentId = data.get("agentId") != null ?
                ((Number) data.get("agentId")).longValue() : null;
        String userId = sessionUserMap.getOrDefault(session.getId(), fallbackUserId(session));
        Long chatSessionId = resolveChatSessionId(userId, data.get("sessionId"), session.getId());
        sessionChatMap.put(session.getId(), chatSessionId);
        String model = (String) data.get("model");
        String apiKey = (String) data.get("apiKey");
        String baseUrl = (String) data.get("baseUrl");
        Boolean enableThinking = (Boolean) data.get("enableThinking");
        
        if (enableThinking == null) {
            enableThinking = settingService.getSetting().getEnableThinking();
        }

        if ((message == null || message.isBlank()) && (images == null || images.isEmpty())) {
            sendMessage(session, Map.of(
                "type", "error",
                "message", "消息不能为空"
            ));
            return;
        }


        log.info("处理聊天消息: userId={}, message={}, imagesCount={}, model={}, baseUrl={}, enableThinking={}", userId,
                message != null && message.length() > 50 ? message.substring(0, 50) + "..." : message,
                images != null ? images.size() : 0,
                model, baseUrl, enableThinking);

        sendMessage(session, Map.of(
            "type", "chatStart",
            "userMessage", message != null ? message : "",
            "chatSessionId", chatSessionId
        ));

        try {
            chatService.streamChat(message, images, chatSessionId, agentId, userId, model, apiKey, baseUrl, enableThinking, new ChatService.ToolEventListener() {
                        @Override
                        public void onToolStart(String toolCallId, String toolName, String arguments) {
                            try {
                                String skillName = SkillNameResolver.resolve(toolName, arguments, objectMapper);
                                sendToolEvent(session, "toolCallStart", toolCallId, toolName, skillName, arguments, null, false, java.util.List.of());
                            } catch (IOException e) {
                                log.error("发送工具开始事件失败: {}", e.getMessage());
                            }
                        }

                        @Override
                        public void onToolEnd(String toolCallId, String toolName, String arguments, String result, boolean isError,
                                              java.util.List<com.lingshu.ai.core.service.TurnTimelineService.ArtifactPayload> artifacts) {
                            try {
                                String skillName = SkillNameResolver.resolve(toolName, arguments, objectMapper);
                                sendToolEvent(session, "toolCallEnd", toolCallId, toolName, skillName, arguments, result, isError, artifacts);
                            } catch (IOException e) {
                                log.error("发送工具完成事件失败: {}", e.getMessage());
                            }
                        }
                    })
                    .doOnNext(chunk -> {
                        try {
                            if (chunk.startsWith("\u0001REASONING\u0001") && chunk.endsWith("\u0001/REASONING\u0001")) {
                                String reasoningContent = chunk.substring("\u0001REASONING\u0001".length(), chunk.length() - "\u0001/REASONING\u0001".length());
                                sendMessage(session, Map.of(
                                    "type", "reasoningChunk",
                                    "content", reasoningContent
                                ));
                            } else {
                                sendMessage(session, Map.of(
                                    "type", "chatChunk",
                                    "content", chunk
                                ));
                            }
                        } catch (IOException e) {
                            log.error("发送消息块失败: {}", e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            sendMessage(session, Map.of("type", "chatEnd"));
                        } catch (IOException e) {
                            log.error("发送结束信号失败: {}", e.getMessage());
                        }
                    })
                    .doOnError(error -> {
                        try {
                            sendMessage(session, Map.of(
                                "type", "error",
                                "message", "聊天处理失败: " + error.getMessage()
                            ));
                        } catch (IOException e) {
                            log.error("发送错误消息失败: {}", e.getMessage());
                        }
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("聊天处理异常: {}", e.getMessage());
            sendMessage(session, Map.of(
                "type", "error",
                "message", "聊天处理失败"
            ));
        }
    }

    private void handleHistory(WebSocketSession session, Map<String, Object> data) throws IOException {
        String userId = sessionUserMap.getOrDefault(session.getId(), fallbackUserId(session));
        int size = data.get("size") != null ? ((Number) data.get("size")).intValue() : 20;
        Long beforeId = data.get("beforeId") != null ? 
                ((Number) data.get("beforeId")).longValue() : null;
        
        log.info("获取历史消息: userId={}, size={}, beforeId={}", userId, size, beforeId);
        
        sendMessage(session, Map.of(
            "type", "historyLoad",
            "size", size,
            "beforeId", beforeId
        ));
    }

    @SuppressWarnings("unchecked")
    private void handleAudio(WebSocketSession session, Map<String, Object> data) {
        String base64Audio = (String) data.get("data");
        String mimeType = (String) data.get("mimeType");
        
        if (base64Audio == null || base64Audio.isBlank()) {
            try {
                sendMessage(session, Map.of(
                    "type", "asrError",
                    "message", "音频数据为空"
                ));
            } catch (IOException e) {
                log.error("发送错误消息失败", e);
            }
            return;
        }

        SystemSetting setting = settingService.getSetting();
        Map<String, Object> asrConfig = setting.getAsrConfig();
        
        if (asrConfig == null) {
            asrConfig = setting.createDefaultAsrConfig();
        }
        
        Boolean enabled = (Boolean) asrConfig.get("enabled");
        if (enabled == null || !enabled) {
            try {
                sendMessage(session, Map.of(
                    "type", "asrError",
                    "message", "ASR 服务未启用"
                ));
            } catch (IOException e) {
                log.error("发送错误消息失败", e);
            }
            return;
        }
        
        String asrUrl = (String) asrConfig.get("url");
        if (asrUrl == null || asrUrl.isBlank()) {
            try {
                sendMessage(session, Map.of(
                    "type", "asrError",
                    "message", "ASR 服务地址未配置"
                ));
            } catch (IOException e) {
                log.error("发送错误消息失败", e);
            }
            return;
        }

        log.info("处理音频识别请求: mimeType={}, dataLength={}", mimeType, base64Audio.length());
        
        asrService.recognizeFromBase64(asrUrl, base64Audio, mimeType)
                .thenAccept(text -> {
                    try {
                        if (text != null && !text.isBlank()) {
                            sendMessage(session, Map.of(
                                "type", "asrResult",
                                "text", text
                            ));
                        } else {
                            sendMessage(session, Map.of(
                                "type", "asrResult",
                                "text", ""
                            ));
                        }
                    } catch (IOException e) {
                        log.error("发送识别结果失败", e);
                    }
                })
                .exceptionally(ex -> {
                    try {
                        sendMessage(session, Map.of(
                            "type", "asrError",
                            "message", "语音识别失败: " + ex.getMessage()
                        ));
                    } catch (IOException e) {
                        log.error("发送错误消息失败", e);
                    }
                    return null;
                });
    }

    private String resolveUserId(WebSocketSession session, Object rawUserId) {
        if (rawUserId instanceof String str && !str.isBlank()) {
            return str.trim();
        }
        return fallbackUserId(session);
    }

    private String fallbackUserId(WebSocketSession session) {
        return "web-ws:" + session.getId();
    }

    private Long resolveChatSessionId(String userId, Object rawSessionId, String webSocketSessionId) {
        Long explicitSessionId = null;
        if (rawSessionId instanceof Number number) {
            explicitSessionId = number.longValue();
        } else if (rawSessionId instanceof String value && !value.isBlank()) {
            explicitSessionId = Long.parseLong(value.trim());
        }
        Long cachedSessionId = sessionChatMap.get(webSocketSessionId);
        return chatSessionService.resolveSessionId(userId, explicitSessionId != null ? explicitSessionId : cachedSessionId);
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    private void sendToolEvent(WebSocketSession session,
                               String type,
                               String toolCallId,
                               String toolName,
                               String skillName,
                               String arguments,
                               String result,
                               boolean isError,
                               java.util.List<com.lingshu.ai.core.service.TurnTimelineService.ArtifactPayload> artifacts) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("toolCallId", toolCallId != null ? toolCallId : "");
        payload.put("toolName", toolName != null ? toolName : "");
        if (skillName != null && !skillName.isBlank()) {
            payload.put("skillName", skillName);
        }
        payload.put("arguments", arguments != null ? arguments : "");
        payload.put("isError", isError);
        if (result != null) {
            payload.put("result", result);
        }
        if (artifacts != null && !artifacts.isEmpty()) {
            payload.put("artifacts", artifacts);
        }
        sendMessage(session, payload);
    }

    public void broadcastToUser(String userId, Map<String, Object> message) {
        sessionUserMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(userId))
                .map(entry -> sessions.get(entry.getKey()))
                .filter(session -> session != null && session.isOpen())
                .forEach(session -> {
                    try {
                        sendMessage(session, message);
                    } catch (IOException e) {
                        log.error("广播消息失败: {}", e.getMessage());
                    }
                });
    }

    public void broadcastProactiveGreeting(String userId, String greeting) {
        broadcastToUser(userId, Map.of(
            "type", "proactiveGreeting",
            "content", greeting
        ));
    }

    public static Map<String, WebSocketSession> getSessions() {
        return sessions;
    }

    public static Map<String, String> getSessionUserMap() {
        return sessionUserMap;
    }

    public static Map<String, Long> getSessionChatMap() {
        return sessionChatMap;
    }
}
