package com.lingshu.ai.core.dto;

public class FactRelationshipResult {
    private String type;
    private double confidence;
    private String reasoning;

    public FactRelationshipResult() {
    }

    public FactRelationshipResult(String type, double confidence, String reasoning) {
        this.type = type;
        this.confidence = confidence;
        this.reasoning = reasoning;
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
}
