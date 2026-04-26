package com.lingshu.ai.core.service;

public interface MemoryStateProjector {

    double computeStateBonus(String serializedTaskVector,
                             Double uncertainty,
                             double[] queryVector,
                             double alpha);
}
