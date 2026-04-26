package com.lingshu.ai.core.service;

public interface MemoryStateUpdater {

    MemoryStateDelta applySupported(String serializedVector,
                                    Double currentUncertainty,
                                    Integer currentUpdateCount,
                                    double[] queryVector,
                                    Double supportConfidence);

    double applyUnsupportedUncertainty(Double currentUncertainty, Double confidence);

    record MemoryStateDelta(String serializedVector,
                            double uncertainty,
                            int updateCount,
                            double gain) {
    }
}
