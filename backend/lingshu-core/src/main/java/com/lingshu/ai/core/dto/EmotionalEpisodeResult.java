package com.lingshu.ai.core.dto;

import java.util.List;

public class EmotionalEpisodeResult {

    private boolean shouldExtract;
    private String reason;
    private EpisodeData episode;

    public EmotionalEpisodeResult() {}

    public boolean isShouldExtract() { return shouldExtract; }
    public void setShouldExtract(boolean shouldExtract) { this.shouldExtract = shouldExtract; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public EpisodeData getEpisode() { return episode; }
    public void setEpisode(EpisodeData episode) { this.episode = episode; }

    public static class EpisodeData {
        private String triggerEvent;
        private String emotionType;
        private Double emotionIntensity;
        private List<String> triggerKeywords;
        private String userResponse;
        private String copingMechanism;
        private String outcomeEmotion;
        private Double outcomeIntensity;
        private String contextSummary;

        public EpisodeData() {}

        public String getTriggerEvent() { return triggerEvent; }
        public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
        public String getEmotionType() { return emotionType; }
        public void setEmotionType(String emotionType) { this.emotionType = emotionType; }
        public Double getEmotionIntensity() { return emotionIntensity; }
        public void setEmotionIntensity(Double emotionIntensity) { this.emotionIntensity = emotionIntensity; }
        public List<String> getTriggerKeywords() { return triggerKeywords; }
        public void setTriggerKeywords(List<String> triggerKeywords) { this.triggerKeywords = triggerKeywords; }
        public String getUserResponse() { return userResponse; }
        public void setUserResponse(String userResponse) { this.userResponse = userResponse; }
        public String getCopingMechanism() { return copingMechanism; }
        public void setCopingMechanism(String copingMechanism) { this.copingMechanism = copingMechanism; }
        public String getOutcomeEmotion() { return outcomeEmotion; }
        public void setOutcomeEmotion(String outcomeEmotion) { this.outcomeEmotion = outcomeEmotion; }
        public Double getOutcomeIntensity() { return outcomeIntensity; }
        public void setOutcomeIntensity(Double outcomeIntensity) { this.outcomeIntensity = outcomeIntensity; }
        public String getContextSummary() { return contextSummary; }
        public void setContextSummary(String contextSummary) { this.contextSummary = contextSummary; }
    }
}
