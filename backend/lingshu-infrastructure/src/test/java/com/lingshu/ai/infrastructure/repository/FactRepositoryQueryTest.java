package com.lingshu.ai.infrastructure.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.repository.query.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactRepositoryQueryTest {

    @Test
    void updateFactAdaptiveScores_shouldNotRefreshLastActivatedAt() throws NoSuchMethodException {
        Method method = FactRepository.class.getMethod(
                "updateFactAdaptiveScores",
                Long.class,
                double.class,
                double.class,
                java.time.LocalDateTime.class
        );

        String query = method.getAnnotation(Query.class).value();

        assertFalse(query.contains("lastActivatedAt"));
        assertTrue(query.contains("SET f.importance = $importance"));
        assertTrue(query.contains("f.confidence = $confidence"));
    }

    @Test
    void updateRelatedRelationWeight_shouldMergeRelationWhenMissing() throws NoSuchMethodException {
        Method method = FactRepository.class.getMethod(
                "updateRelatedRelationWeight",
                Long.class,
                Long.class,
                double.class,
                java.time.LocalDateTime.class
        );

        String query = method.getAnnotation(Query.class).value();

        assertTrue(query.contains("MERGE"));
        assertTrue(query.contains("[r:RELATED_TO]"));
        assertTrue(query.contains("r.weight = $weight"));
        assertTrue(query.contains("r.lastActivatedAt = $updatedAt"));
    }
}
