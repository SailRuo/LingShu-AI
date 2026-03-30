package com.lingshu.ai.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.dto.EmotionContext;
import com.lingshu.ai.core.dto.EmotionContextResult;
import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.infrastructure.entity.ChatMessage;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmotionPreAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(EmotionPreAnalysisService.class);
    private static final int HISTORY_LIMIT = 5;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final EmotionContextCache contextCache;
    private final ChatMessageRepository messageRepository;
    private final DynamicMemoryModel dynamicMemoryModel;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper;
    private EmotionContextAnalyzer emotionContextAnalyzer;

    public EmotionPreAnalysisService(EmotionContextCache contextCache,
                                     ChatMessageRepository messageRepository,
                                     DynamicMemoryModel dynamicMemoryModel,
                                     SystemLogService systemLogService,
                                     ObjectMapper objectMapper) {
        this.contextCache = contextCache;
        this.messageRepository = messageRepository;
        this.dynamicMemoryModel = dynamicMemoryModel;
        this.systemLogService = systemLogService;
        this.objectMapper = objectMapper;
    }

    private EmotionContextAnalyzer getAnalyzer() {
        if (emotionContextAnalyzer == null) {
            emotionContextAnalyzer = AiServices.builder(EmotionContextAnalyzer.class)
                    .chatModel(dynamicMemoryModel)
                    .build();
        }
        return emotionContextAnalyzer;
    }

    public EmotionContextResult analyzeBeforeResponse(String userId, String currentMessage, Long sessionId) {
        try {
            String modelName = dynamicMemoryModel.getModelName();
            systemLogService.info(String.format("情感前置分析: 开始分析用户消息 (模型: %s)", modelName), "EMOTION");
            systemLogService.startTimer("emotion_pre_analysis");

            String conversationHistory = buildConversationHistory(sessionId);
            String previousEmotionState = buildPreviousEmotionState(userId);

            EmotionContextResult result = getAnalyzer().analyzeWithContext(
                    currentMessage,
                    conversationHistory,
                    previousEmotionState
            );

            if (result != null) {
                contextCache.updateContext(userId, result.toSnapshot());
                
                systemLogService.info(String.format(
                        "情感前置分析完成: emotion=%s, intensity=%.2f, trend=%s, needsComfort=%s",
                        result.getEmotion(),
                        result.getIntensity() != null ? result.getIntensity() : 0.0,
                        result.getTrend(),
                        result.getNeedsComfort()
                ), "EMOTION");
                
                systemLogService.endTimer("emotion_pre_analysis", "情感前置分析完成", "EMOTION");
            }

            return result;
        } catch (Exception e) {
            log.warn("情感前置分析失败: {}", e.getMessage(), e);
            systemLogService.error("情感前置分析失败: " + e.getMessage(), "EMOTION");
            return null;
        }
    }

    public EmotionAnalysis analyzeSimple(String userId, String message) {
        try {
            EmotionAnalyzer simpleAnalyzer = AiServices.builder(EmotionAnalyzer.class)
                    .chatModel(dynamicMemoryModel)
                    .build();
            
            EmotionAnalysis result = simpleAnalyzer.analyze(message);
            
            if (result != null) {
                contextCache.updateContext(userId, result);
            }
            
            return result;
        } catch (Exception e) {
            log.warn("简单情感分析失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildConversationHistory(Long sessionId) {
        if (sessionId == null) {
            return "无历史对话记录";
        }

        try {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, HISTORY_LIMIT);
            List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable).getContent();
            
            if (messages.isEmpty()) {
                return "无历史对话记录";
            }

            List<ChatMessage> recentMessages = messages.stream()
                    .limit(HISTORY_LIMIT)
                    .collect(Collectors.toList());
            
            java.util.Collections.reverse(recentMessages);

            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : recentMessages) {
                String time = msg.getCreatedAt() != null ? 
                    msg.getCreatedAt().format(TIME_FORMATTER) : "";
                String role = "user".equals(msg.getRole()) ? "用户" : "灵枢";
                
                String content = msg.getContent();
                String textOnly = content;
                
                // 处理多模态 JSON 存储格式
                if (content != null && content.trim().startsWith("{") && content.trim().endsWith("}")) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(content);
                        if (node.has("text")) {
                            textOnly = node.get("text").asText();
                        } else if (node.has("content") && node.get("content").isTextual()) {
                             textOnly = node.get("content").asText();
                        }
                    } catch (Exception e) {
                        // 解析失败则按原样截断处理
                        log.debug("Failed to pre-parse history message content as JSON, using raw text");
                    }
                }

                if (textOnly != null && textOnly.length() > 200) {
                    textOnly = textOnly.substring(0, 200) + "...";
                }
                
                sb.append(String.format("[%s] %s: %s\n", time, role, textOnly));
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.debug("获取对话历史失败: {}", e.getMessage());
            return "无法获取历史对话记录";
        }
    }

    private String buildPreviousEmotionState(String userId) {
        EmotionContext context = contextCache.getContext(userId);
        
        if (context == null || context.isEmpty()) {
            return "无之前的情感状态记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("最近情感趋势: %s\n", translateTrend(context.getEmotionTrend())));
        sb.append(String.format("累积情绪强度: %.2f\n", context.getCumulativeIntensity()));
        sb.append("\n最近情感历史:\n");
        sb.append(context.toHistoryString());
        
        return sb.toString();
    }

    private String translateTrend(String trend) {
        if (trend == null) return "稳定";
        return switch (trend.toLowerCase()) {
            case "improving" -> "好转中";
            case "declining" -> "有所下降";
            default -> "稳定";
        };
    }

    public String getEmotionPromptInjection(String userId) {
        return contextCache.getEmotionPromptInjection(userId);
    }

    public EmotionContext getEmotionContext(String userId) {
        return contextCache.getContext(userId);
    }

    public void updateEmotionContext(String userId, EmotionAnalysis analysis) {
        contextCache.updateContext(userId, analysis);
    }
}
