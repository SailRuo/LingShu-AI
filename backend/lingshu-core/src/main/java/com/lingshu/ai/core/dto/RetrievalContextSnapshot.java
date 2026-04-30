package com.lingshu.ai.core.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RetrievalContextSnapshot {

    private String userId;
    private Long sessionId;
    private Long turnId;
    private String query;
    private String routingDecision;
    private Double gain;
    private List<RetrievalFactCandidate> retrievedFacts = new ArrayList<>();
    private List<RetrievalFactCandidate> contextFacts = new ArrayList<>();
    private LocalDateTime createdAt;

    public Long getTurnId() {
        return turnId;
    }

    public String getQuery() {
        return query;
    }

    public String getRoutingDecision() {
        return routingDecision;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public List<RetrievalFactCandidate> getContextFacts() {
        return contextFacts == null ? List.of() : List.copyOf(contextFacts);
    }

    public String getUserId() {
        return userId;
    }

    public static RetrievalContextSnapshotBuilder builder() {
        return new RetrievalContextSnapshotBuilder();
    }

    public static class RetrievalContextSnapshotBuilder {
        private String userId;
        private Long sessionId;
        private Long turnId;
        private String query;
        private String routingDecision;
        private Double gain;
        private List<RetrievalFactCandidate> retrievedFacts;
        private List<RetrievalFactCandidate> contextFacts;
        private LocalDateTime createdAt;

        public RetrievalContextSnapshotBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public RetrievalContextSnapshotBuilder sessionId(Long sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public RetrievalContextSnapshotBuilder turnId(Long turnId) {
            this.turnId = turnId;
            return this;
        }

        public RetrievalContextSnapshotBuilder query(String query) {
            this.query = query;
            return this;
        }

        public RetrievalContextSnapshotBuilder routingDecision(String routingDecision) {
            this.routingDecision = routingDecision;
            return this;
        }

        public RetrievalContextSnapshotBuilder gain(Double gain) {
            this.gain = gain;
            return this;
        }

        public RetrievalContextSnapshotBuilder retrievedFacts(List<RetrievalFactCandidate> retrievedFacts) {
            this.retrievedFacts = retrievedFacts;
            return this;
        }

        public RetrievalContextSnapshotBuilder contextFacts(List<RetrievalFactCandidate> contextFacts) {
            this.contextFacts = contextFacts;
            return this;
        }

        public RetrievalContextSnapshotBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public RetrievalContextSnapshot build() {
            return new RetrievalContextSnapshot(userId, sessionId, turnId, query, routingDecision, gain, retrievedFacts, contextFacts, createdAt);
        }
    }

    @Builder
    public RetrievalContextSnapshot(String userId,
                                    Long sessionId,
                                    Long turnId,
                                    String query,
                                    String routingDecision,
                                    Double gain,
                                    List<RetrievalFactCandidate> retrievedFacts,
                                    List<RetrievalFactCandidate> contextFacts,
                                    LocalDateTime createdAt) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.turnId = turnId;
        this.query = query;
        this.routingDecision = routingDecision;
        this.gain = gain;
        this.retrievedFacts = copyFacts(retrievedFacts);
        this.contextFacts = copyFacts(contextFacts);
        this.createdAt = createdAt;
    }

    public void setRetrievedFacts(List<RetrievalFactCandidate> retrievedFacts) {
        this.retrievedFacts = copyFacts(retrievedFacts);
    }

    public void setContextFacts(List<RetrievalFactCandidate> contextFacts) {
        this.contextFacts = copyFacts(contextFacts);
    }

    public List<RetrievalFactCandidate> getRetrievedFacts() {
        return retrievedFacts == null ? List.of() : List.copyOf(retrievedFacts);
    }

    public boolean hasContextFacts() {
        return !getContextFacts().isEmpty();
    }

    private static List<RetrievalFactCandidate> copyFacts(List<RetrievalFactCandidate> facts) {
        return facts == null ? new ArrayList<>() : new ArrayList<>(facts);
    }
}
