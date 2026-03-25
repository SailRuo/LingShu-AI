package com.lingshu.ai.core.dto;

public class FactSemanticClassification {
    private String topicKey;
    private String subType;
    private double confidence;

    public FactSemanticClassification() {
    }

    public FactSemanticClassification(String topicKey, String subType, double confidence) {
        this.topicKey = topicKey;
        this.subType = subType;
        this.confidence = confidence;
    }

    public String getTopicKey() {
        return topicKey;
    }

    public void setTopicKey(String topicKey) {
        this.topicKey = topicKey;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
