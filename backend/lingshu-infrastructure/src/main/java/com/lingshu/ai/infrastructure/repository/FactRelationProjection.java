package com.lingshu.ai.infrastructure.repository;

import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDateTime;

public interface FactRelationProjection {
    Long getSourceId();
    Long getTargetId();
    String getType();
    Double getWeight();
    LocalDateTime getLastActivatedAt();
}