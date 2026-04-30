package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryRetrievalContextSnapshotStore implements RetrievalContextSnapshotStore {

    private final Map<String, RetrievalContextSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public void save(RetrievalContextSnapshot snapshot) {
        if (snapshot == null || snapshot.getTurnId() == null) {
            return;
        }
        store.put(String.valueOf(snapshot.getTurnId()), snapshot);
    }

    @Override
    public Optional<RetrievalContextSnapshot> findByTurnId(Long turnId) {
        if (turnId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(String.valueOf(turnId)));
    }

    @Override
    public void remove(Long turnId) {
        if (turnId != null) {
            store.remove(String.valueOf(turnId));
        }
    }

    @Override
    public void removeBySessionId(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        store.entrySet().removeIf(entry -> 
            entry.getValue() != null && sessionId.equals(entry.getValue().getSessionId())
        );
    }
}
