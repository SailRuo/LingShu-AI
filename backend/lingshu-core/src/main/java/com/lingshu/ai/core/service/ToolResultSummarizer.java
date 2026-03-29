package com.lingshu.ai.core.service;

import java.util.List;

public interface ToolResultSummarizer {

    String THRESHOLD_CONFIG_KEY = "tool_result_summary_threshold";
    int DEFAULT_THRESHOLD = 2000;
    int MAX_SUMMARY_LENGTH = 2000;

    SummarizeResult summarize(String toolName, String originalResult, String userIntent);

    boolean shouldSummarize(String toolName, String result);

    record SummarizeResult(
            String content,
            boolean wasSummarized,
            int originalLength,
            int summarizedLength,
            boolean relevant,
            List<String> keyPoints,
            double confidence
    ) {
        public static SummarizeResult notSummarized(String content) {
            return new SummarizeResult(content, false, content.length(), content.length(), true, List.of(), 1.0);
        }

        public static SummarizeResult summarized(String summary, int originalLength, boolean relevant, 
                                                 List<String> keyPoints, double confidence) {
            return new SummarizeResult(summary, true, originalLength, summary.length(), relevant, keyPoints, confidence);
        }
    }
}
