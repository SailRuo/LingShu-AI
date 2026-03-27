package com.lingshu.ai.core.dto;

import java.util.List;

public class MemoryUpdate {
    private List<String> newFacts;
    private List<Long> deletedFactIds;
    private String analysis;

    public MemoryUpdate() {}
    public MemoryUpdate(List<String> newFacts, List<Long> deletedFactIds, String analysis) {
        this.newFacts = newFacts;
        this.deletedFactIds = deletedFactIds;
        this.analysis = analysis;
    }

    public List<String> getNewFacts() { return newFacts; }
    public void setNewFacts(List<String> newFacts) { this.newFacts = newFacts; }
    public java.util.List<Long> getDeletedFactIds() { return deletedFactIds; }
    public void setDeletedFactIds(java.util.List<Long> deletedFactIds) { this.deletedFactIds = deletedFactIds; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
}
