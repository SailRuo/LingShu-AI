package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.AdaptiveMemoryScorer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveMemoryScorerImplTest {

    @Test
    void scoreFact_shouldSlightlyRewardSupportedFact() {
        AdaptiveMemoryScorer scorer = new AdaptiveMemoryScorerImpl();

        AdaptiveMemoryScorer.FactDelta delta = scorer.scoreFact(
                0.82,
                0.88,
                RetrievalFeedbackResult.FactFeedback.builder()
                        .factId(1L)
                        .valid(Boolean.TRUE)
                        .confidence(0.92)
                        .build()
        );

        assertEquals(0.85, delta.newImportance(), 0.0001);
        assertEquals(0.90, delta.newConfidence(), 0.0001);
    }

    @Test
    void scoreFact_shouldSlightlyPenalizeHighlyConfidentUnsupportedFact() {
        AdaptiveMemoryScorer scorer = new AdaptiveMemoryScorerImpl();

        AdaptiveMemoryScorer.FactDelta delta = scorer.scoreFact(
                0.82,
                0.88,
                RetrievalFeedbackResult.FactFeedback.builder()
                        .factId(2L)
                        .valid(Boolean.FALSE)
                        .confidence(0.91)
                        .build()
        );

        assertEquals(0.80, delta.newImportance(), 0.0001);
        assertEquals(0.85, delta.newConfidence(), 0.0001);
    }

    @Test
    void scoreFact_shouldKeepLowConfidenceUnsupportedOrUnknownFactUnchanged() {
        AdaptiveMemoryScorer scorer = new AdaptiveMemoryScorerImpl();

        AdaptiveMemoryScorer.FactDelta lowConfidenceRejected = scorer.scoreFact(
                0.46,
                0.32,
                RetrievalFeedbackResult.FactFeedback.builder()
                        .factId(3L)
                        .valid(Boolean.FALSE)
                        .confidence(0.45)
                        .build()
        );
        AdaptiveMemoryScorer.FactDelta unknown = scorer.scoreFact(
                0.46,
                0.32,
                RetrievalFeedbackResult.FactFeedback.builder()
                        .factId(4L)
                        .valid(null)
                        .confidence(0.93)
                        .build()
        );

        assertEquals(0.46, lowConfidenceRejected.newImportance(), 0.0001);
        assertEquals(0.32, lowConfidenceRejected.newConfidence(), 0.0001);
        assertEquals(0.46, unknown.newImportance(), 0.0001);
        assertEquals(0.32, unknown.newConfidence(), 0.0001);
    }

    @Test
    void scoreRelationWeight_shouldRespectLowerAndUpperBounds() {
        AdaptiveMemoryScorer scorer = new AdaptiveMemoryScorerImpl();

        assertEquals(1.0, scorer.scoreRelationWeight(0.96, 2, 0), 0.0001);
        assertEquals(0.20, scorer.scoreRelationWeight(0.22, 0, 3), 0.0001);
    }
}
