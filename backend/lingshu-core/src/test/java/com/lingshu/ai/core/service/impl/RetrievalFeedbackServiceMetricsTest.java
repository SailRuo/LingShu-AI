package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.SystemLogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RetrievalFeedbackServiceMetricsTest {

    @Test
    void logAdaptiveSummary_shouldIncludeCoreStageTwoCounters() {
        SystemLogService systemLogService = mock(SystemLogService.class);
        RetrievalFeedbackService service = new RetrievalFeedbackService(
                mock(com.lingshu.ai.core.service.RetrievalContextSnapshotStore.class),
                mock(com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer.class),
                systemLogService
        );

        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(401L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(1L).valid(Boolean.TRUE).confidence(0.92).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(2L).valid(Boolean.TRUE).confidence(0.81).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(3L).valid(Boolean.FALSE).confidence(0.95).build()
                ))
                .build();

        service.logAdaptiveSummary(
                401L,
                result,
                new RetrievalFeedbackService.AdaptiveUpdateSummary(2, 1),
                RetrievalFeedbackService.FeedbackPersistenceState.NEWLY_PERSISTED
        );

        verify(systemLogService).info(contains("supportedFacts=2"), eq("MEMORY"));
        verify(systemLogService).info(contains("factScoreUpdates=2"), eq("MEMORY"));
        verify(systemLogService).info(contains("relatedUpdates=1"), eq("MEMORY"));
    }
}
