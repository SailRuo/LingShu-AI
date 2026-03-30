package com.lingshu.ai.infrastructure.repository;

public interface FactRelationProjection {
    Long getSourceId();
    Long getTargetId();
    String getRelationType();
    Double getWeight();
    String getLastActivatedAt();
}
