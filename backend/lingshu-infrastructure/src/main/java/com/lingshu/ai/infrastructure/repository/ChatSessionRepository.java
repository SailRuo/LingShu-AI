package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("chatSessionRepository")
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findFirstByUserIdOrderByIdAsc(String userId);

    Optional<ChatSession> findFirstByUserIdOrderByUpdatedAtDescIdDesc(String userId);

    List<ChatSession> findByUserIdOrderByUpdatedAtDescIdDesc(String userId);
}
