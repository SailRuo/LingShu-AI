package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.dto.EmotionContext;
import com.lingshu.ai.core.dto.EmotionContextResult;
import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.infrastructure.entity.ChatTurn;
import com.lingshu.ai.infrastructure.repository.ChatTurnRepository;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class EmotionPreAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(EmotionPreAnalysisService.class);
    private static final int HISTORY_LIMIT = 5;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final EmotionContextCache contextCache;
    private final ChatTurnRepository turnRepository;
    private final DynamicMemoryModel dynamicMemoryModel;
    private final SystemLogService systemLogService;
    private EmotionContextAnalyzer emotionContextAnalyzer;

    public EmotionPreAnalysisService(EmotionContextCache contextCache,
                                     ChatTurnRepository turnRepository,
                                     DynamicMemoryModel dynamicMemoryModel,
                                     SystemLogService systemLogService) {
        this.contextCache = contextCache;
        this.turnRepository = turnRepository;
        this.dynamicMemoryModel = dynamicMemoryModel;
        this.systemLogService = systemLogService;
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
            systemLogService.info(String.format("Emotion pre-analysis started (model: %s)", modelName), "EMOTION");
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
                        "Emotion pre-analysis done: emotion=%s, intensity=%.2f, trend=%s, needsComfort=%s",
                        result.getEmotion(),
                        result.getIntensity() != null ? result.getIntensity() : 0.0,
                        result.getTrend(),
                        result.getNeedsComfort()
                ), "EMOTION");

                systemLogService.endTimer("emotion_pre_analysis", "Emotion pre-analysis completed", "EMOTION");
            }

            return result;
        } catch (Exception e) {
            log.warn("Emotion pre-analysis failed: {}", e.getMessage(), e);
            systemLogService.error("Emotion pre-analysis failed: " + e.getMessage(), "EMOTION");
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
            log.warn("Simple emotion analysis failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildConversationHistory(Long sessionId) {
        if (sessionId == null) {
            return "No conversation history";
        }

        try {
            PageRequest pageRequest = PageRequest.of(0, HISTORY_LIMIT, Sort.by("id").descending());
            List<ChatTurn> turnsDesc = turnRepository.findBySessionIdOrderByIdDesc(sessionId, pageRequest);
            if (turnsDesc.isEmpty()) {
                return "No conversation history";
            }

            List<ChatTurn> turns = new ArrayList<>(turnsDesc);
            Collections.reverse(turns);

            StringBuilder sb = new StringBuilder();
            for (ChatTurn turn : turns) {
                String time = turn.getCreatedAt() != null ? turn.getCreatedAt().format(TIME_FORMATTER) : "";

                String userText = abbreviate(turn.getUserMessage());
                if (!userText.isBlank()) {
                    sb.append(String.format("[%s] User: %s\n", time, userText));
                }

                String assistantText = abbreviate(turn.getAssistantMessage());
                if (!assistantText.isBlank()) {
                    sb.append(String.format("[%s] Assistant: %s\n", time, assistantText));
                }
            }

            return sb.isEmpty() ? "No conversation history" : sb.toString();
        } catch (Exception e) {
            log.debug("Failed to fetch conversation history: {}", e.getMessage());
            return "Failed to load conversation history";
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 200) {
            return trimmed.substring(0, 200) + "...";
        }
        return trimmed;
    }

    private String buildPreviousEmotionState(String userId) {
        EmotionContext context = contextCache.getContext(userId);

        if (context == null || context.isEmpty()) {
            return "No previous emotion state";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Recent trend: %s\n", translateTrend(context.getEmotionTrend())));
        sb.append(String.format("Cumulative intensity: %.2f\n", context.getCumulativeIntensity()));
        sb.append("\nRecent emotion history:\n");
        sb.append(context.toHistoryString());

        return sb.toString();
    }

    private String translateTrend(String trend) {
        if (trend == null) return "stable";
        return switch (trend.toLowerCase()) {
            case "improving" -> "improving";
            case "declining" -> "declining";
            default -> "stable";
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
