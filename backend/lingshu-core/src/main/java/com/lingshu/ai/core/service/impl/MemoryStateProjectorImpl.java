package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.MemoryStateProjector;
import org.springframework.stereotype.Service;

@Service
public class MemoryStateProjectorImpl implements MemoryStateProjector {

    @Override
    public double computeStateBonus(String serializedTaskVector,
                                    Double uncertainty,
                                    double[] queryVector,
                                    double alpha) {
        if (queryVector == null || queryVector.length == 0 || serializedTaskVector == null || serializedTaskVector.isBlank()) {
            return 0.0d;
        }
        double[] taskVector = parseVector(serializedTaskVector, queryVector.length);
        if (taskVector == null) {
            return 0.0d;
        }
        double similarity = cosine(taskVector, queryVector);
        if (similarity <= 0) {
            return 0.0d;
        }
        double safeUncertainty = clamp(uncertainty == null ? 1.0d : uncertainty, 0.0d, 1.2d);
        return similarity * (1.0d - Math.min(1.0d, safeUncertainty)) * Math.max(0.0d, alpha);
    }

    private double[] parseVector(String serializedTaskVector, int expectedLength) {
        String cleaned = serializedTaskVector.replace("[", "").replace("]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        String[] tokens = cleaned.split(",");
        if (tokens.length != expectedLength) {
            return null;
        }
        double[] parsed = new double[expectedLength];
        for (int i = 0; i < tokens.length; i++) {
            try {
                parsed[i] = Double.parseDouble(tokens[i].trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return parsed;
    }

    private double cosine(double[] left, double[] right) {
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm <= 1e-12 || rightNorm <= 1e-12) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
