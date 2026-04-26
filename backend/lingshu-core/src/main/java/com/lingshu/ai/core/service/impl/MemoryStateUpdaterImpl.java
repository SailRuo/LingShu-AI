package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.MemoryStateUpdater;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MemoryStateUpdaterImpl implements MemoryStateUpdater {

    private static final double UNCERTAINTY_MIN = 0.08d;
    private static final double UNCERTAINTY_MAX = 1.20d;
    private static final double SUPPORTED_UNCERTAINTY_DECAY = 0.92d;
    private static final double UNSUPPORTED_UNCERTAINTY_BOOST = 0.06d;
    private static final double GAIN_MIN = 0.05d;
    private static final double GAIN_MAX = 0.35d;

    @Override
    public MemoryStateDelta applySupported(String serializedVector,
                                           Double currentUncertainty,
                                           Integer currentUpdateCount,
                                           double[] queryVector,
                                           Double supportConfidence) {
        if (queryVector == null || queryVector.length == 0) {
            return new MemoryStateDelta(serializedVector, safeUncertainty(currentUncertainty), safeUpdateCount(currentUpdateCount), 0.0d);
        }

        double[] normalizedQuery = normalize(queryVector);
        double[] baseVector = parseOrDefault(serializedVector, normalizedQuery.length);
        double uncertainty = safeUncertainty(currentUncertainty);
        double confidence = clamp(supportConfidence == null ? 0.0d : supportConfidence, 0.0d, 1.0d);
        double gain = clamp(0.08d + 0.22d * confidence + 0.20d * uncertainty, GAIN_MIN, GAIN_MAX);

        double[] merged = new double[normalizedQuery.length];
        for (int i = 0; i < normalizedQuery.length; i++) {
            merged[i] = (1.0d - gain) * baseVector[i] + gain * normalizedQuery[i];
        }
        double[] normalizedMerged = normalize(merged);
        double nextUncertainty = Math.max(UNCERTAINTY_MIN, uncertainty * SUPPORTED_UNCERTAINTY_DECAY);
        int nextUpdateCount = safeUpdateCount(currentUpdateCount) + 1;
        return new MemoryStateDelta(serialize(normalizedMerged), nextUncertainty, nextUpdateCount, gain);
    }

    @Override
    public double applyUnsupportedUncertainty(Double currentUncertainty, Double confidence) {
        double uncertainty = safeUncertainty(currentUncertainty);
        double confidenceFactor = clamp(confidence == null ? 0.0d : confidence, 0.0d, 1.0d);
        double boosted = uncertainty + UNSUPPORTED_UNCERTAINTY_BOOST * confidenceFactor;
        return Math.min(UNCERTAINTY_MAX, boosted);
    }

    private double[] parseOrDefault(String serializedVector, int expectedLength) {
        if (serializedVector == null || serializedVector.isBlank()) {
            return new double[expectedLength];
        }

        String cleaned = serializedVector.replace("[", "").replace("]", "").trim();
        if (cleaned.isEmpty()) {
            return new double[expectedLength];
        }

        String[] parts = cleaned.split(",");
        if (parts.length != expectedLength) {
            return new double[expectedLength];
        }

        double[] values = new double[expectedLength];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException exception) {
                return new double[expectedLength];
            }
        }
        return values;
    }

    private String serialize(double[] vector) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        builder.append("]");
        return builder.toString();
    }

    private double[] normalize(double[] vector) {
        double norm = 0.0d;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm <= 1e-12) {
            return vector.clone();
        }
        double scale = Math.sqrt(norm);
        double[] normalized = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / scale;
        }
        return normalized;
    }

    private int safeUpdateCount(Integer currentUpdateCount) {
        return currentUpdateCount == null ? 0 : Math.max(currentUpdateCount, 0);
    }

    private double safeUncertainty(Double currentUncertainty) {
        if (currentUncertainty == null) {
            return 1.0d;
        }
        return clamp(currentUncertainty, UNCERTAINTY_MIN, UNCERTAINTY_MAX);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
