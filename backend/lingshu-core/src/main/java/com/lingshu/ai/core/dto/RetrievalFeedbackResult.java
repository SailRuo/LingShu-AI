package com.lingshu.ai.core.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class RetrievalFeedbackResult {

    private Long turnId;
    private List<FactFeedback> factFeedback = new ArrayList<>();

    @Builder
    public RetrievalFeedbackResult(Long turnId, List<FactFeedback> factFeedback) {
        this.turnId = turnId;
        this.factFeedback = copyFeedback(factFeedback);
    }

    public void setFactFeedback(List<FactFeedback> factFeedback) {
        this.factFeedback = copyFeedback(factFeedback);
    }

    public List<FactFeedback> getFactFeedback() {
        return factFeedback == null ? List.of() : List.copyOf(factFeedback);
    }

    private static List<FactFeedback> copyFeedback(List<FactFeedback> factFeedback) {
        return factFeedback == null ? new ArrayList<>() : new ArrayList<>(factFeedback);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FactFeedback {

        private Long factId;
        private Boolean valid;
        private Double confidence;
        private String reason;
    }
}
