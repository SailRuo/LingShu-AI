package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;

import java.util.Optional;

public interface RetrievalContextSnapshotStore {

    void save(RetrievalContextSnapshot snapshot);

    Optional<RetrievalContextSnapshot> findByTurnId(Long turnId);

    void remove(Long turnId);

    void removeBySessionId(Long sessionId);
}
