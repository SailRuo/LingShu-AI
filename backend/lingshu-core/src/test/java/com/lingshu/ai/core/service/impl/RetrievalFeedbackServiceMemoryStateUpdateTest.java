package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.MemoryStateUpdater;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.MemoryStateRecord;
import com.lingshu.ai.infrastructure.repository.MemoryStateRecordRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalFeedbackServiceMemoryStateUpdateTest {

    @Test
    void analyzeTurn_shouldWriteMemoryStateWhenFeatureEnabled() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(301L)
                .query("用户最近在准备什么")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(RetrievalFactCandidate.builder().factId(51L).content("用户在准备面试").build()))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(301L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(301L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(51L).valid(Boolean.TRUE).confidence(0.92).build()
                ))
                .build());

        MemoryStateRecordRepository stateRepository = mock(MemoryStateRecordRepository.class);
        when(stateRepository.findByFactId(51L)).thenReturn(Optional.empty());
        when(stateRepository.save(any(MemoryStateRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemoryStateUpdater updater = mock(MemoryStateUpdater.class);
        when(updater.applySupported(any(), any(), any(), any(), any())).thenReturn(
                new MemoryStateUpdater.MemoryStateDelta("[0.10,0.20]", 0.88, 1, 0.30)
        );

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.3f, 0.4f})));

        RetrievalFeedbackService service = new RetrievalFeedbackService(
                snapshotStore,
                analyzer,
                mock(SystemLogService.class),
                null,
                null,
                null,
                stateRepository,
                updater,
                embeddingModel,
                true
        );

        service.analyzeTurn(301L, "assistant response");

        verify(stateRepository).save(any(MemoryStateRecord.class));
        verify(snapshotStore).remove(301L);
    }

    @Test
    void analyzeTurn_shouldSkipMemoryStateWriteWhenFeatureDisabled() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(302L)
                .query("用户最近在准备什么")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(RetrievalFactCandidate.builder().factId(52L).content("用户在准备面试").build()))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(302L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(302L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(52L).valid(Boolean.TRUE).confidence(0.95).build()
                ))
                .build());

        MemoryStateRecordRepository stateRepository = mock(MemoryStateRecordRepository.class);
        MemoryStateUpdater updater = mock(MemoryStateUpdater.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        RetrievalFeedbackService service = new RetrievalFeedbackService(
                snapshotStore,
                analyzer,
                mock(SystemLogService.class),
                null,
                null,
                null,
                stateRepository,
                updater,
                embeddingModel,
                false
        );

        service.analyzeTurn(302L, "assistant response");

        verify(stateRepository, never()).save(any());
        verify(embeddingModel, never()).embed(anyString());
        verify(snapshotStore).remove(302L);
    }
}
