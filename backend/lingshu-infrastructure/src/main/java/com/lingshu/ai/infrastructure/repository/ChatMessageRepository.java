package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("chatMessageRepository")
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    List<ChatMessage> findTop5BySessionOrderByCreatedAtDesc(com.lingshu.ai.infrastructure.entity.ChatSession session);
    List<ChatMessage> findTop5ByOrderByCreatedAtDesc();
    
    Page<ChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);
    
    List<ChatMessage> findBySessionIdAndIdLessThanOrderByCreatedAtDesc(Long sessionId, Long beforeId, Pageable pageable);

    Page<ChatMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    List<ChatMessage> findBySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long beforeId, Pageable pageable);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ChatMessage m where m.session.id = :sessionId")
    void deleteBySessionId(Long sessionId);
}

