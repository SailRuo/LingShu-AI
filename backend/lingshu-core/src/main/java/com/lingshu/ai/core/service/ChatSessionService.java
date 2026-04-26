package com.lingshu.ai.core.service;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionService {

    record ChatSessionView(Long id, String userId, String title, Long agentId, String lastMessage, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    List<ChatSessionView> listSessions(String userId);

    ChatSessionView createSession(String userId, String title);

    Long resolveSessionId(String userId, Long sessionId);

    void touchSession(Long sessionId);

    void bindAgent(Long sessionId, Long agentId);

    void updateSessionTitle(Long sessionId, String title);

    void deleteSession(Long sessionId);
}
