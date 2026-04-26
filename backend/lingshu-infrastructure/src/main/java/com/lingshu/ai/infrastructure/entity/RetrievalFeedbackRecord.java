package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "retrieval_feedback_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_retrieval_feedback_turn_fact",
                columnNames = {"turn_id", "fact_id"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalFeedbackRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turn_id", nullable = false)
    private Long turnId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "fact_id", nullable = false)
    private Long factId;

    @Column(name = "query", columnDefinition = "TEXT")
    private String query;

    @Column(name = "routing_decision")
    private String routingDecision;

    @Column(name = "is_valid")
    private Boolean valid;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
