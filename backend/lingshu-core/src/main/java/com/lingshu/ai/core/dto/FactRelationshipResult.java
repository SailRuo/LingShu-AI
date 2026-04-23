package com.lingshu.ai.core.dto;

public class FactRelationshipResult {
    private String type;
    private double confidence;
    private String reasoning;
    private Long matchedFactId;

    public FactRelationshipResult() {
    }

    public FactRelationshipResult(String type, double confidence, String reasoning) {
        this.type = type;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }

    public FactRelationshipResult(String type, double confidence, String reasoning, Long matchedFactId) {
        this.type = type;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.matchedFactId = matchedFactId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public Long getMatchedFactId() {
        return matchedFactId;
    }

    public void setMatchedFactId(Long matchedFactId) {
        this.matchedFactId = matchedFactId;
    }
}
