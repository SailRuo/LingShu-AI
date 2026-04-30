package com.lingshu.ai.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RetrievalFeedbackResult {

    private Long turnId;
    private List<FactFeedback> factFeedback = new ArrayList<>();

    public Long getTurnId() {
        return turnId;
    }

    public void setTurnId(Long turnId) {
        this.turnId = turnId;
    }

    public static RetrievalFeedbackResultBuilder builder() {
        return new RetrievalFeedbackResultBuilder();
    }

    public static class RetrievalFeedbackResultBuilder {
        private Long turnId;
        private List<FactFeedback> factFeedback;

        public RetrievalFeedbackResultBuilder turnId(Long turnId) {
            this.turnId = turnId;
            return this;
        }

        public RetrievalFeedbackResultBuilder factFeedback(List<FactFeedback> factFeedback) {
            this.factFeedback = factFeedback;
            return this;
        }

        public RetrievalFeedbackResult build() {
            return new RetrievalFeedbackResult(turnId, factFeedback);
        }
    }

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

    @Getter
    @Setter
    @ToString
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactFeedback {

        private Long factId;
        private Boolean valid;
        private Double confidence;
        private String reason;

        public Long getFactId() {
            return factId;
        }

        public Boolean getValid() {
            return valid;
        }

        public Double getConfidence() {
            return confidence;
        }

        public String getReason() {
            return reason;
        }

        public static FactFeedbackBuilder builder() {
            return new FactFeedbackBuilder();
        }

        public static class FactFeedbackBuilder {
            private Long factId;
            private Boolean valid;
            private Double confidence;
            private String reason;

            public FactFeedbackBuilder factId(Long factId) {
                this.factId = factId;
                return this;
            }

            public FactFeedbackBuilder valid(Boolean valid) {
                this.valid = valid;
                return this;
            }

            public FactFeedbackBuilder confidence(Double confidence) {
                this.confidence = confidence;
                return this;
            }

            public FactFeedbackBuilder reason(String reason) {
                this.reason = reason;
                return this;
            }

            public FactFeedback build() {
                return new FactFeedback(factId, valid, confidence, reason);
            }
        }
    }
}
