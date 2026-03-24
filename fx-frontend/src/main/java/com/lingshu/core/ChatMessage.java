package com.lingshu.core;

import java.time.LocalDateTime;

/**
 * 聊天消息数据模型。
 */
public record ChatMessage(
        Long id,
        String role, // "user" 或 "assistant"
        String content,
        LocalDateTime createdAt
) {
    public ChatMessage(String role, String content) {
        this(null, role, content, LocalDateTime.now());
    }
}
