package com.lingshu.ai.core.dto;

import java.util.List;

public class MemoryUpdate {
    private List<String> newFacts;
    private List<Long> deletedFactIds;

    public MemoryUpdate() {}
    public MemoryUpdate(List<String> newFacts, List<Long> deletedFactIds) {
        this.newFacts = newFacts;
        this.deletedFactIds = deletedFactIds;
    }

    public List<String> getNewFacts() { return newFacts; }
    public void setNewFacts(List<String> newFacts) { this.newFacts = newFacts; }
    public java.util.List<Long> getDeletedFactIds() { return deletedFactIds; }
    public void setDeletedFactIds(java.util.List<Long> deletedFactIds) { this.deletedFactIds = deletedFactIds; }
}
