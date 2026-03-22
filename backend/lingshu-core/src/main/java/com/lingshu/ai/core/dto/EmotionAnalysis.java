package com.lingshu.ai.core.dto;

import java.util.List;

public class EmotionAnalysis {

    private String emotion;
    private Double intensity;
    private Boolean needsComfort;
    private List<String> keywords;

    public EmotionAnalysis() {}

    public EmotionAnalysis(String emotion, Double intensity, Boolean needsComfort, List<String> keywords) {
        this.emotion = emotion;
        this.intensity = intensity;
        this.needsComfort = needsComfort;
        this.keywords = keywords;
    }

    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public Double getIntensity() { return intensity; }
    public void setIntensity(Double intensity) { this.intensity = intensity; }
    public Boolean getNeedsComfort() { return needsComfort; }
    public void setNeedsComfort(Boolean needsComfort) { this.needsComfort = needsComfort; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public boolean isNegative() {
        return "negative".equalsIgnoreCase(emotion);
    }

    public boolean isPositive() {
        return "positive".equalsIgnoreCase(emotion);
    }

    public boolean isNeutral() {
        return "neutral".equalsIgnoreCase(emotion) || emotion == null;
    }

    public boolean needsAttention() {
        return Boolean.TRUE.equals(needsComfort) || 
               (isNegative() && intensity != null && intensity > 0.6);
    }
}
