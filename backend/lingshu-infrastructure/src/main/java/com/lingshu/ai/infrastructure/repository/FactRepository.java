package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.FactNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FactRepository extends Neo4jRepository<FactNode, Long> {
    
    @Query("MATCH (f:Fact) WHERE ANY(keyword IN $keywords WHERE toLower(f.content) CONTAINS toLower(keyword)) RETURN f LIMIT 5")
    List<FactNode> findFactsByKeywords(List<String> keywords);
    
    @Query("MATCH (f:Fact) RETURN f.content")
    List<String> findAllFactContents();
}
