package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("chatSessionRepository")
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}
