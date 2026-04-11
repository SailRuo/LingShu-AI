package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_turn_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurnEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false)
    private ChatTurn turn;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "tool_call_id")
    private String toolCallId;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "skill_name")
    private String skillName;

    @Column(name = "arguments", columnDefinition = "TEXT")
    private String arguments;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_error")
    private Boolean isError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

