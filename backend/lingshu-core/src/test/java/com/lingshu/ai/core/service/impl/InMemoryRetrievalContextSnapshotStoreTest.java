package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRetrievalContextSnapshotStoreTest {

    @Test
    void removeBySessionId_shouldOnlyRemoveSnapshotsForMatchingSession() {
        InMemoryRetrievalContextSnapshotStore snapshotStore = new InMemoryRetrievalContextSnapshotStore();
        snapshotStore.save(snapshot(11L, 101L));
        snapshotStore.save(snapshot(11L, 102L));
        snapshotStore.save(snapshot(12L, 201L));

        snapshotStore.removeBySessionId(11L);

        assertTrue(snapshotStore.findByTurnId(101L).isEmpty());
        assertTrue(snapshotStore.findByTurnId(102L).isEmpty());
        assertTrue(snapshotStore.findByTurnId(201L).isPresent());
    }

    private static RetrievalContextSnapshot snapshot(Long sessionId, Long turnId) {
        return RetrievalContextSnapshot.builder()
                .sessionId(sessionId)
                .turnId(turnId)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
