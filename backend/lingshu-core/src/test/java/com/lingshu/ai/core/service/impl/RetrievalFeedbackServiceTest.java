package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import com.lingshu.ai.core.service.SystemLogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalFeedbackServiceTest {

    @Test
    void analyzeTurn_shouldSkipWhenSnapshotMissing() {
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(101L)).thenReturn(Optional.empty());

        RetrievalFeedbackService service = new RetrievalFeedbackService(
                snapshotStore,
                mock(RetrievalFeedbackAnalyzer.class),
                mock(SystemLogService.class)
        );

        service.analyzeTurn(101L, "assistant response");

        verify(snapshotStore, never()).remove(101L);
    }

    @Test
    void analyzeTurn_shouldLogSummaryAndRemoveSnapshot() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(102L)
                .routingDecision("GRAPH_PRIORITIZED_VECTOR_SUPPLEMENT")
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(1L).content("用户喜欢咖啡").build(),
                        RetrievalFactCandidate.builder().factId(2L).content("用户正在备考").build(),
                        RetrievalFactCandidate.builder().factId(3L).content("用户住在上海").build()
                ))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(102L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(102L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(1L).valid(Boolean.TRUE).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(2L).valid(Boolean.FALSE).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(3L).valid(null).build()
                ))
                .build());

        SystemLogService systemLogService = mock(SystemLogService.class);
        RetrievalFeedbackService service = new RetrievalFeedbackService(snapshotStore, analyzer, systemLogService);

        service.analyzeTurn(102L, "assistant response");

        verify(analyzer).analyze(snapshot, "assistant response");
        verify(systemLogService).info(contains("supportedFacts=1"), eq("MEMORY"));
        verify(systemLogService).info(contains("unsupportedFacts=1"), eq("MEMORY"));
        verify(systemLogService).info(contains("uncertainFacts=1"), eq("MEMORY"));
        verify(snapshotStore).remove(102L);
    }

    @Test
    void analyzeTurn_shouldRemoveSnapshotWhenAnalyzerFails() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(103L)
                .contextFacts(List.of(RetrievalFactCandidate.builder().factId(9L).content("用户学 Rust").build()))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(103L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenThrow(new IllegalStateException("judge offline"));

        SystemLogService systemLogService = mock(SystemLogService.class);
        RetrievalFeedbackService service = new RetrievalFeedbackService(snapshotStore, analyzer, systemLogService);

        service.analyzeTurn(103L, "assistant response");

        verify(systemLogService).warn(contains("judge offline"), eq("MEMORY"));
        verify(snapshotStore).remove(103L);
    }
}
