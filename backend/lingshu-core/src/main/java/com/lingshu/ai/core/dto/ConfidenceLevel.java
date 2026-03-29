package com.lingshu.ai.core.dto;

public enum ConfidenceLevel {
    HIGH(0.9, "高置信度", "平静状态下的明确陈述"),
    MEDIUM(0.7, "中等置信度", "一般情况下的陈述"),
    LOW(0.5, "低置信度", "情绪激动或模糊的陈述"),
    VOLATILE(0.3, "待确认", "情绪激动时的极端表述，需要后续确认");

    private final double value;
    private final String displayName;
    private final String description;

    ConfidenceLevel(double value, String displayName, String description) {
        this.value = value;
        this.displayName = displayName;
        this.description = description;
    }

    public double getValue() { return value; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public static ConfidenceLevel fromEmotionState(String emotionType, Double intensity) {
        if (emotionType == null || "neutral".equalsIgnoreCase(emotionType)) {
            return HIGH;
        }
        
        if ("negative".equalsIgnoreCase(emotionType) && intensity != null && intensity > 0.7) {
            return VOLATILE;
        }
        
        if (intensity != null && intensity > 0.6) {
            return LOW;
        }
        
        return MEDIUM;
    }

    public static ConfidenceLevel fromValue(double value) {
        if (value >= 0.85) return HIGH;
        if (value >= 0.65) return MEDIUM;
        if (value >= 0.45) return LOW;
        return VOLATILE;
    }
}
