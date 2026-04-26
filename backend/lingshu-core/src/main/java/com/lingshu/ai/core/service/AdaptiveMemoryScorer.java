package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.RetrievalFeedbackResult;

public interface AdaptiveMemoryScorer {

    FactDelta scoreFact(double currentImportance,
                        double currentConfidence,
                        RetrievalFeedbackResult.FactFeedback feedback);

    double scoreRelationWeight(double currentWeight,
                               long supportedCooccurrence,
                               long unsupportedCooccurrence);

    record FactDelta(double newImportance, double newConfidence) {
    }
}
