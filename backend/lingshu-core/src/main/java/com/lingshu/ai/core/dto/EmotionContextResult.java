package com.lingshu.ai.core.dto;

import java.util.List;

public class EmotionContextResult {

    private String emotion;
    private Double intensity;
    private String trend;
    private List<String> triggerKeywords;
    private String suggestedResponseTone;
    private Boolean needsComfort;
    private String analysis;

    public EmotionContextResult() {}

    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public Double getIntensity() { return intensity; }
    public void setIntensity(Double intensity) { this.intensity = intensity; }
    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }
    public List<String> getTriggerKeywords() { return triggerKeywords; }
    public void setTriggerKeywords(List<String> triggerKeywords) { this.triggerKeywords = triggerKeywords; }
    public String getSuggestedResponseTone() { return suggestedResponseTone; }
    public void setSuggestedResponseTone(String suggestedResponseTone) { this.suggestedResponseTone = suggestedResponseTone; }
    public Boolean getNeedsComfort() { return needsComfort; }
    public void setNeedsComfort(Boolean needsComfort) { this.needsComfort = needsComfort; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }

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

    public boolean isHighIntensity() {
        return intensity != null && intensity > 0.7;
    }

    public EmotionAnalysis toEmotionAnalysis() {
        EmotionAnalysis analysis = new EmotionAnalysis();
        analysis.setEmotion(this.emotion);
        analysis.setIntensity(this.intensity);
        analysis.setNeedsComfort(this.needsComfort);
        analysis.setKeywords(this.triggerKeywords);
        return analysis;
    }

    public EmotionContext.EmotionSnapshot toSnapshot() {
        EmotionContext.EmotionSnapshot snapshot = new EmotionContext.EmotionSnapshot();
        snapshot.setEmotion(this.emotion);
        snapshot.setIntensity(this.intensity != null ? this.intensity : 0.0);
        snapshot.setNeedsComfort(this.needsComfort);
        snapshot.setKeywords(this.triggerKeywords);
        if (this.triggerKeywords != null && !this.triggerKeywords.isEmpty()) {
            snapshot.setTriggerKeyword(String.join(", ", 
                this.triggerKeywords.subList(0, Math.min(3, this.triggerKeywords.size()))));
        }
        return snapshot;
    }
}
