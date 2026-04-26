package com.lingshu.ai.core.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
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

    public List<RetrievalFactCandidate> getContextFacts() {
        return contextFacts == null ? List.of() : List.copyOf(contextFacts);
    }

    public boolean hasContextFacts() {
        return !getContextFacts().isEmpty();
    }

    private static List<RetrievalFactCandidate> copyFacts(List<RetrievalFactCandidate> facts) {
        return facts == null ? new ArrayList<>() : new ArrayList<>(facts);
    }
}
