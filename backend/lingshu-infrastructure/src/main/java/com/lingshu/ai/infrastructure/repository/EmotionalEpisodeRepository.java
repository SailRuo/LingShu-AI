package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.EmotionalEpisode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmotionalEpisodeRepository extends Neo4jRepository<EmotionalEpisode, Long> {

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "RETURN e ORDER BY e.occurredAt DESC LIMIT $limit")
    List<EmotionalEpisode> findRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "WHERE e.emotionType = $emotionType " +
           "RETURN e ORDER BY e.occurredAt DESC LIMIT $limit")
    List<EmotionalEpisode> findByUserIdAndEmotionType(
            @Param("userId") String userId, 
            @Param("emotionType") String emotionType, 
            @Param("limit") int limit);

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "WHERE ANY(keyword IN e.triggerKeywords WHERE keyword IN $keywords) " +
           "RETURN e ORDER BY e.importance DESC, e.occurredAt DESC LIMIT $limit")
    List<EmotionalEpisode> findByKeywords(
            @Param("userId") String userId,
            @Param("keywords") List<String> keywords,
            @Param("limit") int limit);

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "WHERE e.emotionIntensity >= $minIntensity " +
           "RETURN e ORDER BY e.occurredAt DESC LIMIT $limit")
    List<EmotionalEpisode> findByMinIntensity(
            @Param("userId") String userId,
            @Param("minIntensity") double minIntensity,
            @Param("limit") int limit);

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "WHERE e.occurredAt >= $since " +
           "RETURN e ORDER BY e.occurredAt DESC")
    List<EmotionalEpisode> findSince(@Param("userId") String userId, @Param("since") LocalDateTime since);

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "WHERE e.copingMechanism IS NOT NULL AND e.copingMechanism <> '' " +
           "RETURN e ORDER BY e.recallCount DESC, e.occurredAt DESC LIMIT $limit")
    List<EmotionalEpisode> findWithCopingMechanism(@Param("userId") String userId, @Param("limit") int limit);

    @Query("MATCH (e:EmotionalEpisode) WHERE id(e) = $id " +
           "SET e.recallCount = e.recallCount + 1, e.lastRecalledAt = datetime() " +
           "RETURN e")
    EmotionalEpisode incrementRecall(@Param("id") Long id);

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "WHERE e.status = $status " +
           "RETURN e ORDER BY e.occurredAt DESC")
    List<EmotionalEpisode> findByStatus(
            @Param("userId") String userId, 
            @Param("status") String status);

    @Query("MATCH (e:EmotionalEpisode)-[:EXPERIENCED_BY]->(u:User {name: $userId}) " +
           "WHERE e.emotionType = $emotionType AND e.emotionIntensity >= $minIntensity " +
           "RETURN e ORDER BY e.importance DESC, e.occurredAt DESC LIMIT $limit")
    List<EmotionalEpisode> findSimilarEpisodes(
            @Param("userId") String userId,
            @Param("emotionType") String emotionType,
            @Param("minIntensity") double minIntensity,
            @Param("limit") int limit);
}
