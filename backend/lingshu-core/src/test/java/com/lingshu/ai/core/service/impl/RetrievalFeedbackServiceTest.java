package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import com.lingshu.ai.infrastructure.repository.RetrievalFeedbackRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void analyzeTurn_shouldKeepSnapshotWhenPersistenceFails() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(104L)
                .sessionId(88L)
                .userId("user-88")
                .query("最近的学习进展")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(RetrievalFactCandidate.builder().factId(21L).content("用户在学 Kotlin").build()))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(104L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(104L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(21L).valid(Boolean.TRUE).build()
                ))
                .build());

        RetrievalFeedbackRecordRepository repository = mock(RetrievalFeedbackRecordRepository.class);
        when(repository.findByTurnIdAndFactIdIn(anyLong(), anyCollection())).thenReturn(List.of());
        doThrow(new IllegalStateException("db offline")).when(repository).saveAll(anyList());

        SystemLogService systemLogService = mock(SystemLogService.class);
        RetrievalFeedbackService service = new RetrievalFeedbackService(snapshotStore, analyzer, systemLogService, repository);

        service.analyzeTurn(104L, "assistant response");

        verify(systemLogService).warn(contains("db offline"), eq("MEMORY"));
        verify(snapshotStore, never()).remove(104L);
    }

    @Test
    void analyzeTurn_shouldKeepSnapshotWhenAdaptiveUpdateFailsAfterSaveAll() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(105L)
                .sessionId(99L)
                .userId("user-99")
                .query("最近在推进什么")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(RetrievalFactCandidate.builder().factId(31L).content("用户在做系统设计").build()))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(105L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(105L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder()
                                .factId(31L)
                                .valid(Boolean.TRUE)
                                .confidence(0.95)
                                .build()
                ))
                .build());

        RetrievalFeedbackRecordRepository recordRepository = mock(RetrievalFeedbackRecordRepository.class);
        when(recordRepository.findByTurnIdAndFactIdIn(anyLong(), anyCollection())).thenReturn(List.of());
        FactRepository factRepository = mock(FactRepository.class);
        when(factRepository.findById(31L)).thenReturn(Optional.of(FactNode.builder()
                .id(31L)
                .importance(0.70)
                .confidence(0.80)
                .build()));
        doThrow(new IllegalStateException("neo4j offline"))
                .when(factRepository)
                .updateFactAdaptiveScores(eq(31L), any(Double.class), any(Double.class), any());

        SystemLogService systemLogService = mock(SystemLogService.class);
        RetrievalFeedbackService service = new RetrievalFeedbackService(
                snapshotStore,
                analyzer,
                systemLogService,
                recordRepository,
                factRepository,
                new AdaptiveMemoryScorerImpl()
        );

        service.analyzeTurn(105L, "assistant response");

        verify(recordRepository).saveAll(anyList());
        verify(systemLogService).warn(contains("neo4j offline"), eq("MEMORY"));
        verify(snapshotStore, never()).remove(105L);
    }

    @Test
    void analyzeTurn_shouldSkipDuplicatePersistenceAndAdaptiveReplayWhenFeedbackAlreadyRecorded() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(106L)
                .sessionId(100L)
                .userId("user-100")
                .query("最近在推进什么")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(RetrievalFactCandidate.builder().factId(41L).content("用户在做系统设计").build()))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(106L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(106L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder()
                                .factId(41L)
                                .valid(Boolean.TRUE)
                                .confidence(0.95)
                                .reason("already recorded")
                                .build()
                ))
                .build());

        RetrievalFeedbackRecordRepository recordRepository = mock(RetrievalFeedbackRecordRepository.class);
        when(recordRepository.findByTurnIdAndFactIdIn(eq(106L), anyCollection())).thenReturn(List.of(
                RetrievalFeedbackRecord.builder()
                        .turnId(106L)
                        .factId(41L)
                        .build()
        ));

        FactRepository factRepository = mock(FactRepository.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        RetrievalFeedbackService service = new RetrievalFeedbackService(
                snapshotStore,
                analyzer,
                systemLogService,
                recordRepository,
                factRepository,
                new AdaptiveMemoryScorerImpl()
        );

        service.analyzeTurn(106L, "assistant response");

        verify(recordRepository, never()).saveAll(anyList());
        verify(factRepository, never()).findById(any());
        verify(factRepository, never()).updateFactAdaptiveScores(any(), any(Double.class), any(Double.class), any());
        verify(factRepository, never()).updateRelatedRelationWeight(any(), any(), any(Double.class), any());
        verify(snapshotStore).remove(106L);
    }

    @Test
    void analyzeTurn_shouldTreatUniqueConstraintConflictAsIdempotentSuccessWhenRecordsAlreadyExist() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(107L)
                .sessionId(101L)
                .userId("user-101")
                .query("最近在推进什么")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(RetrievalFactCandidate.builder().factId(51L).content("用户在做系统设计").build()))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(107L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(107L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder()
                                .factId(51L)
                                .valid(Boolean.TRUE)
                                .confidence(0.95)
                                .build()
                ))
                .build());

        RetrievalFeedbackRecordRepository recordRepository = mock(RetrievalFeedbackRecordRepository.class);
        when(recordRepository.findByTurnIdAndFactIdIn(eq(107L), anyCollection()))
                .thenReturn(List.of())
                .thenReturn(List.of(RetrievalFeedbackRecord.builder().turnId(107L).factId(51L).build()));
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(recordRepository)
                .saveAll(anyList());

        FactRepository factRepository = mock(FactRepository.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        RetrievalFeedbackService service = new RetrievalFeedbackService(
                snapshotStore,
                analyzer,
                systemLogService,
                recordRepository,
                factRepository,
                new AdaptiveMemoryScorerImpl()
        );

        service.analyzeTurn(107L, "assistant response");

        verify(factRepository, never()).findById(any());
        verify(snapshotStore).remove(107L);
    }
}
