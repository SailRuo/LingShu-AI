package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.ChatSessionService;
import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    @Test
    void createSessionShouldPersistNormalizedUserAndDefaultTitle() {
        when(chatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc("web:test")).thenReturn(List.of());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(101L);
            return session;
        });

        ChatSessionService.ChatSessionView created = chatSessionService.createSession("web:test", null);

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(captor.capture());
        ChatSession saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("web:test");
        assertThat(saved.getTitle()).isEqualTo("新对话 1");
        assertThat(created.id()).isEqualTo(101L);
    }

    @Test
    void listSessionsShouldCreateDefaultWhenUserHasNoSession() {
        when(chatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc("web:new")).thenReturn(List.of());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(200L);
            return session;
        });

        List<ChatSessionService.ChatSessionView> sessions = chatSessionService.listSessions("web:new");

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().id()).isEqualTo(200L);
        assertThat(sessions.getFirst().title()).isEqualTo("新对话 1");
    }

    @Test
    void resolveSessionIdShouldPreferExplicitSession() {
        when(chatSessionRepository.findById(88L)).thenReturn(Optional.of(ChatSession.builder()
                .id(88L)
                .userId("web:test")
                .title("Existing")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()));

        Long resolved = chatSessionService.resolveSessionId("web:test", 88L);

        assertThat(resolved).isEqualTo(88L);
    }

    @Test
    void resolveSessionIdShouldFallbackToLegacyUserForDefaultUser() {
        when(chatSessionRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc("User")).thenReturn(Optional.empty());
        when(chatSessionRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc("web-ws:User")).thenReturn(Optional.of(
                ChatSession.builder()
                        .id(66L)
                        .userId("web-ws:User")
                        .title("Legacy")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        ));

        Long resolved = chatSessionService.resolveSessionId("User", null);

        assertThat(resolved).isEqualTo(66L);
    }

    @Test
    void touchSessionShouldRefreshUpdatedAt() {
        ChatSession existing = ChatSession.builder()
                .id(77L)
                .userId("web:test")
                .title("Session")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(chatSessionRepository.findById(77L)).thenReturn(Optional.of(existing));

        chatSessionService.touchSession(77L);

        verify(chatSessionRepository).save(existing);
        assertThat(existing.getUpdatedAt()).isAfter(existing.getCreatedAt());
    }
}
