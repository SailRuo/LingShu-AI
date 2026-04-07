package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.ChatTurn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {

    List<ChatTurn> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    List<ChatTurn> findBySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long beforeId, Pageable pageable);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ChatTurn t where t.session.id = :sessionId")
    void deleteBySessionId(Long sessionId);
}

