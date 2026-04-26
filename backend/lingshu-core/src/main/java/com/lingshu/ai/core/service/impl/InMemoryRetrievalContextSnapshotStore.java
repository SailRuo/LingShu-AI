package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class InMemoryRetrievalContextSnapshotStore implements RetrievalContextSnapshotStore {

    static final int DEFAULT_MAX_SNAPSHOTS = 256;

    private final int maxSnapshots;
    private final Map<Long, RetrievalContextSnapshot> snapshots;

    public InMemoryRetrievalContextSnapshotStore() {
        this(DEFAULT_MAX_SNAPSHOTS);
    }

    InMemoryRetrievalContextSnapshotStore(int maxSnapshots) {
        this.maxSnapshots = Math.max(1, maxSnapshots);
        this.snapshots = new LinkedHashMap<>(16, 0.75f, false);
    }

    @Override
    public void save(RetrievalContextSnapshot snapshot) {
        if (snapshot != null && snapshot.getTurnId() != null) {
            synchronized (snapshots) {
                snapshots.put(snapshot.getTurnId(), snapshot);
                trimToMaxSize();
            }
        }
    }

    @Override
    public Optional<RetrievalContextSnapshot> findByTurnId(Long turnId) {
        if (turnId == null) {
            return Optional.empty();
        }
        synchronized (snapshots) {
            return Optional.ofNullable(snapshots.get(turnId));
        }
    }

    @Override
    public void remove(Long turnId) {
        if (turnId != null) {
            synchronized (snapshots) {
                snapshots.remove(turnId);
            }
        }
    }

    @Override
    public void removeBySessionId(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        synchronized (snapshots) {
            snapshots.entrySet().removeIf(entry -> sessionId.equals(entry.getValue().getSessionId()));
        }
    }

    private void trimToMaxSize() {
        while (snapshots.size() > maxSnapshots) {
            Long oldestTurnId = snapshots.keySet().iterator().next();
            snapshots.remove(oldestTurnId);
        }
    }
}
