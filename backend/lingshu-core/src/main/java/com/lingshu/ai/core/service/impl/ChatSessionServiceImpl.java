package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.ChatSessionService;
import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public List<ChatSessionView> listSessions(String userId) {
        String normalizedUserId = normalizeUserId(userId);
        List<ChatSession> sessions = new ArrayList<>(chatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc(normalizedUserId));
        if (sessions.isEmpty() && "User".equals(normalizedUserId)) {
            loadLegacySessions(sessions, "web-ws:User");
            loadLegacySessions(sessions, "web-http:User");
        }
        if (sessions.isEmpty()) {
            sessions.add(createSessionEntity(normalizedUserId, null));
        }
        sessions.sort((left, right) -> {
            LocalDateTime leftTime = left.getUpdatedAt() != null ? left.getUpdatedAt() : left.getCreatedAt();
            LocalDateTime rightTime = right.getUpdatedAt() != null ? right.getUpdatedAt() : right.getCreatedAt();
            if (leftTime == null && rightTime == null) {
                return Long.compare(right.getId(), left.getId());
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            int byTime = rightTime.compareTo(leftTime);
            return byTime != 0 ? byTime : Long.compare(right.getId(), left.getId());
        });
        return sessions.stream().map(this::toView).toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public ChatSessionView createSession(String userId, String title) {
        return toView(createSessionEntity(normalizeUserId(userId), title));
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public Long resolveSessionId(String userId, Long sessionId) {
        if (sessionId != null) {
            Optional<ChatSession> explicit = chatSessionRepository.findById(sessionId);
            if (explicit.isPresent()) {
                return explicit.get().getId();
            }
        }

        String normalizedUserId = normalizeUserId(userId);
        Optional<ChatSession> byUser = chatSessionRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc(normalizedUserId);
        if (byUser.isPresent()) {
            return byUser.get().getId();
        }
        if ("User".equals(normalizedUserId)) {
            Optional<ChatSession> legacyWs = chatSessionRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc("web-ws:User");
            if (legacyWs.isPresent()) {
                return legacyWs.get().getId();
            }
            Optional<ChatSession> legacyHttp = chatSessionRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc("web-http:User");
            if (legacyHttp.isPresent()) {
                return legacyHttp.get().getId();
            }
        }
        return createSessionEntity(normalizedUserId, null).getId();
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public void touchSession(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(session);
        });
    }

    private ChatSession createSessionEntity(String userId, String title) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title(normalizeTitle(userId, title))
                .createdAt(now)
                .updatedAt(now)
                .build();
        return chatSessionRepository.save(session);
    }

    private String normalizeTitle(String userId, String title) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        int sequence = chatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId).size() + 1;
        return "新对话 " + sequence;
    }

    private void loadLegacySessions(List<ChatSession> sessions, String userId) {
        sessions.addAll(chatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId));
    }

    private ChatSessionView toView(ChatSession session) {
        return new ChatSessionView(
                session.getId(),
                session.getUserId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private String normalizeUserId(String userId) {
        return userId == null || userId.isBlank() ? "User" : userId.trim();
    }
}
