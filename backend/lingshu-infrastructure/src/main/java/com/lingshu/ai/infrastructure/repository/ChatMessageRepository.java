package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository("chatMessageRepository")
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    List<ChatMessage> findTop5ByOrderByCreatedAtDesc();
}
