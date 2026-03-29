package com.lingshu.ai.infrastructure.repository;

import java.time.LocalDateTime;

public interface FactRelationProjection {
    Long getSourceId();
    Long getTargetId();
    String getType();
    Double getWeight();
    LocalDateTime getLastActivatedAt();
}
