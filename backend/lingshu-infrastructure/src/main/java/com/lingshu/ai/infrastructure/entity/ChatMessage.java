package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String role; // "user", "assistant", or "tool"

    private LocalDateTime createdAt;

    /**
     * 工具调用 ID，用于关联 ToolExecutionResultMessage 与对应的工具调用请求。
     */
    @Column(name = "tool_call_id")
    private String toolCallId;

    /**
     * 工具名称，ToolExecutionResultMessage 中携带的工具名。
     */
    @Column(name = "tool_name")
    private String toolName;

    /**
     * JSON 序列化的 toolExecutionRequests 列表。
     * 当 AiMessage 包含工具调用请求时，将其序列化存储在此字段。
     */
    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls;
}
