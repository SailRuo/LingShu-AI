package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(length = 50)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(columnDefinition = "TEXT")
    private String factExtractionPrompt;

    @Column(columnDefinition = "TEXT")
    private String behaviorPrinciples;

    @Column(columnDefinition = "TEXT")
    private String decisionMechanism;

    @Column(columnDefinition = "TEXT")
    private String toolCallRules;

    @Column(columnDefinition = "TEXT")
    private String emotionalStrategy;

    @Column(columnDefinition = "TEXT")
    private String greetingTriggers;

    @Column(columnDefinition = "TEXT")
    private String hiddenRules;

    @Column(columnDefinition = "TEXT")
    private String avatar;

    private String color;

    @Builder.Default
    private Boolean isDefault = false;

    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
