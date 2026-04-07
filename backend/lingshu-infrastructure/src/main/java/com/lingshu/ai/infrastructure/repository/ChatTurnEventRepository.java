package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.ChatTurnEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ChatTurnEventRepository extends JpaRepository<ChatTurnEvent, Long> {

    List<ChatTurnEvent> findByTurnIdInOrderByTurnIdAscSequenceNoAsc(Collection<Long> turnIds);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "delete from chat_turn_events where turn_id in (" +
                    "select id from chat_turns where session_id = :sessionId" +
                    ")",
            nativeQuery = true
    )
    void deleteBySessionId(Long sessionId);
}
