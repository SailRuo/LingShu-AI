package com.lingshu.ai.core.dto;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;

public class EmotionContext {

    private String userId;
    private Deque<EmotionSnapshot> recentEmotions;
    private Double cumulativeIntensity;
    private String emotionTrend;
    private LocalDateTime lastUpdate;
    private static final int WINDOW_SIZE = 5;

    public EmotionContext() {
        this.recentEmotions = new ArrayDeque<>(WINDOW_SIZE);
        this.cumulativeIntensity = 0.0;
        this.emotionTrend = "stable";
        this.lastUpdate = LocalDateTime.now();
    }

    public EmotionContext(String userId) {
        this();
        this.userId = userId;
    }

    public void addSnapshot(EmotionSnapshot snapshot) {
        if (recentEmotions.size() >= WINDOW_SIZE) {
            recentEmotions.removeFirst();
        }
        recentEmotions.addLast(snapshot);
        recalculateTrend();
        lastUpdate = LocalDateTime.now();
    }

    private void recalculateTrend() {
        if (recentEmotions.size() < 2) {
            this.emotionTrend = "stable";
            this.cumulativeIntensity = recentEmotions.isEmpty() ? 0.0 : 
                recentEmotions.peekLast().intensity;
            return;
        }

        List<EmotionSnapshot> list = new ArrayList<>(recentEmotions);
        int size = list.size();
        
        double recentAvg = 0.0;
        double earlierAvg = 0.0;
        int recentCount = Math.min(3, size / 2 + 1);
        
        for (int i = size - recentCount; i < size; i++) {
            recentAvg += list.get(i).intensity;
        }
        recentAvg /= recentCount;
        
        for (int i = 0; i < size - recentCount; i++) {
            earlierAvg += list.get(i).intensity;
        }
        if (size > recentCount) {
            earlierAvg /= (size - recentCount);
        }

        String latestEmotion = list.get(size - 1).emotion;
        String earlierEmotion = list.get(0).emotion;
        
        double intensityChange = recentAvg - earlierAvg;
        boolean emotionImproved = "positive".equals(latestEmotion) && 
            ("negative".equals(earlierEmotion) || "neutral".equals(earlierEmotion));
        boolean emotionDeclined = "negative".equals(latestEmotion) && 
            ("positive".equals(earlierEmotion) || "neutral".equals(earlierEmotion));

        if (emotionImproved || (intensityChange < -0.15 && "negative".equals(earlierEmotion))) {
            this.emotionTrend = "improving";
        } else if (emotionDeclined || (intensityChange > 0.15 && "negative".equals(latestEmotion))) {
            this.emotionTrend = "declining";
        } else {
            this.emotionTrend = "stable";
        }

        this.cumulativeIntensity = recentAvg;
    }

    public String toHistoryString() {
        if (recentEmotions.isEmpty()) {
            return "无历史情感记录";
        }
        
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (EmotionSnapshot snapshot : recentEmotions) {
            sb.append(String.format("[%d] %s (强度: %.1f) %s - %s\n",
                index++,
                translateEmotion(snapshot.emotion),
                snapshot.intensity,
                snapshot.triggerKeyword != null ? "触发词: " + snapshot.triggerKeyword : "",
                snapshot.timestamp != null ? snapshot.timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : ""
            ));
        }
        return sb.toString();
    }

    private String translateEmotion(String emotion) {
        if (emotion == null) return "中性";
        return switch (emotion.toLowerCase()) {
            case "positive" -> "积极";
            case "negative" -> "消极";
            default -> "中性";
        };
    }

    public EmotionSnapshot getLatestSnapshot() {
        return recentEmotions.isEmpty() ? null : recentEmotions.peekLast();
    }

    public boolean isEmpty() {
        return recentEmotions.isEmpty();
    }

    public int size() {
        return recentEmotions.size();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Deque<EmotionSnapshot> getRecentEmotions() { return recentEmotions; }
    public Double getCumulativeIntensity() { return cumulativeIntensity; }
    public String getEmotionTrend() { return emotionTrend; }
    public LocalDateTime getLastUpdate() { return lastUpdate; }

    public static class EmotionSnapshot {
        private String emotion;
        private Double intensity;
        private String triggerKeyword;
        private LocalDateTime timestamp;
        private Boolean needsComfort;
        private List<String> keywords;

        public EmotionSnapshot() {
            this.timestamp = LocalDateTime.now();
        }

        public EmotionSnapshot(String emotion, Double intensity, String triggerKeyword) {
            this.emotion = emotion;
            this.intensity = intensity;
            this.triggerKeyword = triggerKeyword;
            this.timestamp = LocalDateTime.now();
        }

        public static EmotionSnapshot from(EmotionAnalysis analysis) {
            EmotionSnapshot snapshot = new EmotionSnapshot();
            snapshot.emotion = analysis.getEmotion();
            snapshot.intensity = analysis.getIntensity() != null ? analysis.getIntensity() : 0.0;
            snapshot.needsComfort = analysis.getNeedsComfort();
            snapshot.keywords = analysis.getKeywords();
            if (analysis.getKeywords() != null && !analysis.getKeywords().isEmpty()) {
                snapshot.triggerKeyword = String.join(", ", analysis.getKeywords().subList(0, Math.min(3, analysis.getKeywords().size())));
            }
            return snapshot;
        }

        public String getEmotion() { return emotion; }
        public void setEmotion(String emotion) { this.emotion = emotion; }
        public Double getIntensity() { return intensity; }
        public void setIntensity(Double intensity) { this.intensity = intensity; }
        public String getTriggerKeyword() { return triggerKeyword; }
        public void setTriggerKeyword(String triggerKeyword) { this.triggerKeyword = triggerKeyword; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Boolean getNeedsComfort() { return needsComfort; }
        public void setNeedsComfort(Boolean needsComfort) { this.needsComfort = needsComfort; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    }
}
