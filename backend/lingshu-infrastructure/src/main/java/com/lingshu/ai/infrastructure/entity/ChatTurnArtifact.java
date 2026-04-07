package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_turn_artifacts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurnArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private ChatTurnEvent event;

    @Column(name = "tool_call_id")
    private String toolCallId;

    @Column(name = "artifact_type", nullable = false)
    private String artifactType;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "base64_data", columnDefinition = "TEXT")
    private String base64Data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

