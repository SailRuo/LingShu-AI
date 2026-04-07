package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.ChatTurnArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ChatTurnArtifactRepository extends JpaRepository<ChatTurnArtifact, Long> {

    List<ChatTurnArtifact> findByEventIdInOrderByIdAsc(Collection<Long> eventIds);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "delete from chat_turn_artifacts where event_id in (" +
                    "select e.id from chat_turn_events e " +
                    "join chat_turns t on e.turn_id = t.id " +
                    "where t.session_id = :sessionId" +
                    ")",
            nativeQuery = true
    )
    void deleteBySessionId(Long sessionId);
}
