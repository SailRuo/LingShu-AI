package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_states")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String userId;

    @Builder.Default
    @Column(nullable = false)
    private Integer affinity = 50;

    @Builder.Default
    @Column(length = 20)
    private String relationshipStage = "初识";

    @Builder.Default
    @Column(nullable = false)
    private Integer positiveInteractions = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalInteractions = 0;

    private LocalDateTime lastActiveTime;

    private LocalDateTime lastGreetTime;

    @Builder.Default
    @Column(nullable = false)
    private Integer inactiveHours = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean needsGreeting = false;

    @Builder.Default
    @Column(length = 20)
    private String lastEmotion = "neutral";

    @Builder.Default
    private Double lastEmotionIntensity = 0.0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastActiveTime == null) {
            lastActiveTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateRelationshipStage() {
        if (affinity >= 81) {
            relationshipStage = "挚友";
        } else if (affinity >= 61) {
            relationshipStage = "亲密";
        } else if (affinity >= 31) {
            relationshipStage = "熟悉";
        } else {
            relationshipStage = "初识";
        }
    }

    public void increaseAffinity(int delta) {
        this.affinity = Math.min(100, this.affinity + delta);
        this.positiveInteractions++;
        this.totalInteractions++;
        updateRelationshipStage();
    }

    public void decreaseAffinity(int delta) {
        this.affinity = Math.max(0, this.affinity - delta);
        this.totalInteractions++;
        updateRelationshipStage();
    }

    public void recordInteraction() {
        this.totalInteractions++;
        this.lastActiveTime = LocalDateTime.now();
        this.inactiveHours = 0;
    }

    public void updateEmotion(String emotion, Double intensity) {
        this.lastEmotion = emotion;
        this.lastEmotionIntensity = intensity;
    }
}
