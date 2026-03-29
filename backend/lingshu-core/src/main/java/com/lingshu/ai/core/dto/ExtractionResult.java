package com.lingshu.ai.core.dto;

import java.util.List;

public class ExtractionResult {

    private List<ExtractedFact> newFacts;
    private List<Long> deletedFactIds;
    private String analysis;
    private boolean emotionGatePassed;
    private String emotionGateReason;

    public ExtractionResult() {}

    public List<ExtractedFact> getNewFacts() { return newFacts; }
    public void setNewFacts(List<ExtractedFact> newFacts) { this.newFacts = newFacts; }
    public List<Long> getDeletedFactIds() { return deletedFactIds; }
    public void setDeletedFactIds(List<Long> deletedFactIds) { this.deletedFactIds = deletedFactIds; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public boolean isEmotionGatePassed() { return emotionGatePassed; }
    public void setEmotionGatePassed(boolean emotionGatePassed) { this.emotionGatePassed = emotionGatePassed; }
    public String getEmotionGateReason() { return emotionGateReason; }
    public void setEmotionGateReason(String emotionGateReason) { this.emotionGateReason = emotionGateReason; }

    public static class ExtractedFact {
        private String content;
        private FactType type;
        private ConfidenceLevel confidence;
        private boolean volatile_;
        private String emotionalContext;
        private List<String> triggerKeywords;

        public ExtractedFact() {}

        public ExtractedFact(String content, FactType type, ConfidenceLevel confidence) {
            this.content = content;
            this.type = type;
            this.confidence = confidence;
            this.volatile_ = (confidence == ConfidenceLevel.VOLATILE);
        }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public FactType getType() { return type; }
        public void setType(FactType type) { this.type = type; }
        public ConfidenceLevel getConfidence() { return confidence; }
        public void setConfidence(ConfidenceLevel confidence) { this.confidence = confidence; }
        public boolean isVolatile() { return volatile_; }
        public void setVolatile(boolean volatile_) { this.volatile_ = volatile_; }
        public String getEmotionalContext() { return emotionalContext; }
        public void setEmotionalContext(String emotionalContext) { this.emotionalContext = emotionalContext; }
        public List<String> getTriggerKeywords() { return triggerKeywords; }
        public void setTriggerKeywords(List<String> triggerKeywords) { this.triggerKeywords = triggerKeywords; }
    }
}
