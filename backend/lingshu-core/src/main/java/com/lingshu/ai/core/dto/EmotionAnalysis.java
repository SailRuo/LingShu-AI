package com.lingshu.ai.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnalysis {

    private String emotion;

    private Double intensity;

    private Boolean needsComfort;

    private List<String> keywords;

    public boolean isNegative() {
        return "negative".equalsIgnoreCase(emotion);
    }

    public boolean isPositive() {
        return "positive".equalsIgnoreCase(emotion);
    }

    public boolean isNeutral() {
        return "neutral".equalsIgnoreCase(emotion) || emotion == null;
    }

    public boolean needsAttention() {
        return Boolean.TRUE.equals(needsComfort) || 
               (isNegative() && intensity != null && intensity > 0.6);
    }
}
