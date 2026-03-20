package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.FactNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FactRepository extends Neo4jRepository<FactNode, Long> {
}
