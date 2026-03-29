package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.dto.EmotionContext;
import com.lingshu.ai.core.dto.EmotionContext.EmotionSnapshot;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class EmotionContextCache {

    private final Map<String, EmotionContext> contextStore = new ConcurrentHashMap<>();
    private static final long CONTEXT_EXPIRY_MINUTES = 30;

    public EmotionContext getOrCreateContext(String userId) {
        return contextStore.computeIfAbsent(userId, EmotionContext::new);
    }

    public EmotionContext getContext(String userId) {
        EmotionContext context = contextStore.get(userId);
        if (context == null) {
            return null;
        }
        
        if (isExpired(context)) {
            contextStore.remove(userId);
            return null;
        }
        return context;
    }

    public void updateContext(String userId, EmotionAnalysis analysis) {
        if (analysis == null) {
            return;
        }
        
        EmotionContext context = getOrCreateContext(userId);
        EmotionSnapshot snapshot = EmotionSnapshot.from(analysis);
        context.addSnapshot(snapshot);
    }

    public void updateContext(String userId, EmotionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        
        EmotionContext context = getOrCreateContext(userId);
        context.addSnapshot(snapshot);
    }

    public String getEmotionPromptInjection(String userId) {
        EmotionContext context = getContext(userId);
        if (context == null || context.isEmpty()) {
            return "";
        }
        
        EmotionSnapshot latest = context.getLatestSnapshot();
        if (latest == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【当前情感状态感知】\n");
        sb.append(String.format("用户当前情绪: %s (强度: %.1f/1.0)\n", 
            translateEmotion(latest.getEmotion()), 
            latest.getIntensity()));
        sb.append(String.format("情绪趋势: %s\n", translateTrend(context.getEmotionTrend())));
        
        if (latest.getTriggerKeyword() != null && !latest.getTriggerKeyword().isEmpty()) {
            sb.append(String.format("情绪触发词: %s\n", latest.getTriggerKeyword()));
        }
        
        if (Boolean.TRUE.equals(latest.getNeedsComfort())) {
            sb.append("用户状态提示: 用户可能需要关怀或安慰\n");
        }
        
        sb.append("\n【建议回应风格】\n");
        sb.append(suggestResponseTone(latest, context.getEmotionTrend()));
        sb.append("\n");
        
        return sb.toString();
    }

    private String translateEmotion(String emotion) {
        if (emotion == null) return "平静";
        return switch (emotion.toLowerCase()) {
            case "positive" -> "积极/开心";
            case "negative" -> "消极/低落";
            default -> "平静/中性";
        };
    }

    private String translateTrend(String trend) {
        if (trend == null) return "稳定";
        return switch (trend.toLowerCase()) {
            case "improving" -> "好转中";
            case "declining" -> "有所下降";
            default -> "稳定";
        };
    }

    private String suggestResponseTone(EmotionSnapshot latest, String trend) {
        if ("negative".equalsIgnoreCase(latest.getEmotion())) {
            if (latest.getIntensity() > 0.6) {
                return "用户情绪较为低落，建议采用温柔关怀的语气，表达理解和支持。";
            }
            if ("declining".equals(trend)) {
                return "用户情绪有下降趋势，建议主动询问是否需要帮助，给予情感支持。";
            }
            return "用户有些许负面情绪，建议保持耐心和关怀，适度表达理解。";
        }
        
        if ("positive".equalsIgnoreCase(latest.getEmotion())) {
            if (latest.getIntensity() > 0.6) {
                return "用户情绪很好，可以轻松愉快地交流，适度分享喜悦。";
            }
            return "用户情绪平稳积极，可以正常交流，保持友好态度。";
        }
        
        return "用户情绪平静，可以正常交流，根据话题灵活调整语气。";
    }

    private boolean isExpired(EmotionContext context) {
        if (context.getLastUpdate() == null) {
            return true;
        }
        return context.getLastUpdate().isBefore(
            LocalDateTime.now().minusMinutes(CONTEXT_EXPIRY_MINUTES)
        );
    }

    public void clearContext(String userId) {
        contextStore.remove(userId);
    }

    public void clearAll() {
        contextStore.clear();
    }

    public int size() {
        return contextStore.size();
    }
}
