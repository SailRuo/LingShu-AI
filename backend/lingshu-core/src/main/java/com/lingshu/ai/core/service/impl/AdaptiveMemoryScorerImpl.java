package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.AdaptiveMemoryScorer;
import org.springframework.stereotype.Service;

@Service
public class AdaptiveMemoryScorerImpl implements AdaptiveMemoryScorer {

    private static final double SUPPORTED_IMPORTANCE_DELTA = 0.03;
    private static final double SUPPORTED_CONFIDENCE_DELTA = 0.02;
    private static final double REJECTED_IMPORTANCE_DELTA = 0.02;
    private static final double REJECTED_CONFIDENCE_DELTA = 0.03;
    private static final double REJECTION_CONFIDENCE_THRESHOLD = 0.80;
    private static final double FACT_SCORE_MAX = 0.98;
    private static final double IMPORTANCE_MIN = 0.12;
    private static final double CONFIDENCE_MIN = 0.20;
    private static final double RELATION_WEIGHT_MIN = 0.20;
    private static final double RELATION_WEIGHT_MAX = 1.00;
    private static final double RELATION_SUPPORT_DELTA = 0.05;
    private static final double RELATION_UNSUPPORTED_DELTA = 0.03;

    @Override
    public FactDelta scoreFact(double currentImportance,
                               double currentConfidence,
                               RetrievalFeedbackResult.FactFeedback feedback) {
        if (feedback == null || feedback.getValid() == null || feedback.getConfidence() == null) {
            return new FactDelta(currentImportance, currentConfidence);
        }

        if (Boolean.TRUE.equals(feedback.getValid())) {
            return new FactDelta(
                    clamp(currentImportance + SUPPORTED_IMPORTANCE_DELTA, IMPORTANCE_MIN, FACT_SCORE_MAX),
                    clamp(currentConfidence + SUPPORTED_CONFIDENCE_DELTA, CONFIDENCE_MIN, FACT_SCORE_MAX)
            );
        }

        if (feedback.getConfidence() >= REJECTION_CONFIDENCE_THRESHOLD) {
            return new FactDelta(
                    clamp(currentImportance - REJECTED_IMPORTANCE_DELTA, IMPORTANCE_MIN, FACT_SCORE_MAX),
                    clamp(currentConfidence - REJECTED_CONFIDENCE_DELTA, CONFIDENCE_MIN, FACT_SCORE_MAX)
            );
        }

        return new FactDelta(currentImportance, currentConfidence);
    }

    @Override
    public double scoreRelationWeight(double currentWeight,
                                      long supportedCooccurrence,
                                      long unsupportedCooccurrence) {
        double next = currentWeight
                + supportedCooccurrence * RELATION_SUPPORT_DELTA
                - unsupportedCooccurrence * RELATION_UNSUPPORTED_DELTA;
        return clamp(next, RELATION_WEIGHT_MIN, RELATION_WEIGHT_MAX);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
