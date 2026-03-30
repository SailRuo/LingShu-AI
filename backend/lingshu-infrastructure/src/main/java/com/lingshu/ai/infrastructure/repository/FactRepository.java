package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.FactNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface FactRepository extends Neo4jRepository<FactNode, Long> {

    @Query("CREATE INDEX IF NOT EXISTS FOR (f:Fact) ON (f.originalMessage)")
    void createOriginalMessageIndex();

    @Query("CREATE INDEX IF NOT EXISTS FOR (f:Fact) ON (f.emotionalTone)")
    void createEmotionalToneIndex();

    @Query("MATCH (f:Fact) WHERE ANY(keyword IN $keywords WHERE toLower(f.content) CONTAINS toLower(keyword)) RETURN f LIMIT 5")
    List<FactNode> findFactsByKeywords(List<String> keywords);

    @Query("MATCH (f:Fact) RETURN f.content")
    List<String> findAllFactContents();

    @Query("MATCH (f:Fact) WHERE f.normalizedContent = $normalizedContent RETURN f LIMIT 5")
    List<FactNode> findByNormalizedContent(String normalizedContent);

    @Query("MATCH (a:Fact)-[r:RELATED_TO|SUPERSEDES|CONTRADICTS]->(b:Fact) " +
           "WHERE id(a) IN $factIds OR id(b) IN $factIds DELETE r")
    void deleteFactRelationsByFactIds(List<Long> factIds);

    @Query("MATCH (f:Fact) WHERE id(f) = $id DETACH DELETE f")
    void detachDeleteById(Long id);

    @Query("UNWIND $relations AS rel " +
           "MATCH (a:Fact) WHERE id(a) = rel.sourceId " +
           "MATCH (b:Fact) WHERE id(b) = rel.targetId " +
           "MERGE (a)-[r:RELATED_TO]->(b) " +
           "SET r.weight = rel.weight, r.lastActivatedAt = rel.lastActivatedAt")
    void saveRelatedRelations(List<Map<String, Object>> relations);

    @Query("UNWIND $relations AS rel " +
           "MATCH (a:Fact) WHERE id(a) = rel.sourceId " +
           "MATCH (b:Fact) WHERE id(b) = rel.targetId " +
           "MERGE (a)-[r:SUPERSEDES]->(b) " +
           "SET r.weight = rel.weight, r.lastActivatedAt = rel.lastActivatedAt")
    void saveSupersedesRelations(List<Map<String, Object>> relations);

    @Query("UNWIND $relations AS rel " +
           "MATCH (a:Fact) WHERE id(a) = rel.sourceId " +
           "MATCH (b:Fact) WHERE id(b) = rel.targetId " +
           "MERGE (a)-[r:CONTRADICTS]->(b) " +
           "SET r.weight = rel.weight, r.lastActivatedAt = rel.lastActivatedAt")
    void saveContradictsRelations(List<Map<String, Object>> relations);

    @Query("MATCH (a:Fact)-[r:RELATED_TO|SUPERSEDES|CONTRADICTS]->(b:Fact) " +
           "WHERE id(a) IN $factIds AND id(b) IN $factIds " +
           "RETURN id(a) AS sourceId, id(b) AS targetId, type(r) AS relationType, r.weight AS weight, toString(r.lastActivatedAt) AS lastActivatedAt")
    List<FactRelationProjection> findFactRelationsByFactIds(List<Long> factIds);

    Page<FactNode> findAllByStatus(String status, Pageable pageable);
}
