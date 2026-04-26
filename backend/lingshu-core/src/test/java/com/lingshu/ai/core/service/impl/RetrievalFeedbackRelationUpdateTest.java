package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalFeedbackRelationUpdateTest {

    @Test
    void applyAdaptiveUpdates_shouldStrengthenOnlySupportedFactPairs() {
        FactRepository factRepository = mock(FactRepository.class);
        mockFact(factRepository, 1L);
        mockFact(factRepository, 2L);
        mockFact(factRepository, 3L);
        when(factRepository.findRelatedRelationWeight(1L, 2L)).thenReturn(null);

        RetrievalFeedbackService service = createService(factRepository);
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(301L)
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(1L).content("用户在准备面试").build(),
                        RetrievalFactCandidate.builder().factId(2L).content("用户在整理项目经历").build(),
                        RetrievalFactCandidate.builder().factId(3L).content("用户喜欢蓝色").build()
                ))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(301L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(1L).valid(Boolean.TRUE).confidence(0.95).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(2L).valid(Boolean.TRUE).confidence(0.88).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(3L).valid(Boolean.FALSE).confidence(0.60).build()
                ))
                .build();

        service.applyAdaptiveUpdates(snapshot, result);

        verify(factRepository).updateRelatedRelationWeight(eq(1L), eq(2L), eq(0.55), any());
        verify(factRepository, never()).updateRelatedRelationWeight(eq(1L), eq(3L), any(Double.class), any());
        verify(factRepository, never()).updateRelatedRelationWeight(eq(2L), eq(3L), any(Double.class), any());
    }

    @Test
    void applyAdaptiveUpdates_shouldCreateRelatedUpdateWhenSupportedPairHasNoExistingEdge() {
        FactRepository factRepository = mock(FactRepository.class);
        mockFact(factRepository, 11L);
        mockFact(factRepository, 12L);
        when(factRepository.findRelatedRelationWeight(11L, 12L)).thenReturn(null);

        RetrievalFeedbackService service = createService(factRepository);
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(302L)
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(11L).content("用户准备秋招").build(),
                        RetrievalFactCandidate.builder().factId(12L).content("用户在复盘项目").build()
                ))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(302L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(11L).valid(Boolean.TRUE).confidence(0.92).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(12L).valid(Boolean.TRUE).confidence(0.91).build()
                ))
                .build();

        service.applyAdaptiveUpdates(snapshot, result);

        verify(factRepository).updateRelatedRelationWeight(eq(11L), eq(12L), eq(0.55), any());
    }

    @Test
    void applyAdaptiveUpdates_shouldNotDowngradeRelationForMixedSupportedUnsupportedPair() {
        FactRepository factRepository = mock(FactRepository.class);
        mockFact(factRepository, 21L);
        mockFact(factRepository, 22L);
        when(factRepository.findRelatedRelationWeight(21L, 22L)).thenReturn(0.60);

        RetrievalFeedbackService service = createService(factRepository);
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(303L)
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(21L).content("用户要转岗做后端").build(),
                        RetrievalFactCandidate.builder().factId(22L).content("用户正在准备后端面试").build()
                ))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(303L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(21L).valid(Boolean.TRUE).confidence(0.94).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(22L).valid(Boolean.FALSE).confidence(0.93).build()
                ))
                .build();

        service.applyAdaptiveUpdates(snapshot, result);

        verify(factRepository, never()).updateRelatedRelationWeight(eq(21L), eq(22L), any(Double.class), any());
    }

    @Test
    void applyAdaptiveUpdates_shouldConservativelyDowngradeExistingRelationForUnsupportedPair() {
        FactRepository factRepository = mock(FactRepository.class);
        mockFact(factRepository, 31L);
        mockFact(factRepository, 32L);
        when(factRepository.findRelatedRelationWeight(31L, 32L)).thenReturn(0.60);

        RetrievalFeedbackService service = createService(factRepository);
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(304L)
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(31L).content("用户长期目标是后端转型").build(),
                        RetrievalFactCandidate.builder().factId(32L).content("用户近期重点在算法刷题").build()
                ))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(304L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(31L).valid(Boolean.FALSE).confidence(0.93).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(32L).valid(Boolean.FALSE).confidence(0.91).build()
                ))
                .build();

        service.applyAdaptiveUpdates(snapshot, result);

        verify(factRepository).updateRelatedRelationWeight(eq(31L), eq(32L), eq(0.57), any());
    }

    @Test
    void applyAdaptiveUpdates_shouldNotCreateRelationForUnsupportedPairWhenMissingEdge() {
        FactRepository factRepository = mock(FactRepository.class);
        mockFact(factRepository, 41L);
        mockFact(factRepository, 42L);
        when(factRepository.findRelatedRelationWeight(41L, 42L)).thenReturn(null);

        RetrievalFeedbackService service = createService(factRepository);
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(305L)
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(41L).content("用户在准备春招").build(),
                        RetrievalFactCandidate.builder().factId(42L).content("用户在准备留学").build()
                ))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(305L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(41L).valid(Boolean.FALSE).confidence(0.95).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(42L).valid(Boolean.FALSE).confidence(0.94).build()
                ))
                .build();

        service.applyAdaptiveUpdates(snapshot, result);

        verify(factRepository, never()).updateRelatedRelationWeight(eq(41L), eq(42L), any(Double.class), any());
    }

    private RetrievalFeedbackService createService(FactRepository factRepository) {
        return new RetrievalFeedbackService(
                mock(RetrievalContextSnapshotStore.class),
                mock(RetrievalFeedbackAnalyzer.class),
                mock(SystemLogService.class),
                null,
                factRepository,
                new AdaptiveMemoryScorerImpl()
        );
    }

    private void mockFact(FactRepository factRepository, Long factId) {
        when(factRepository.findById(factId)).thenReturn(Optional.of(FactNode.builder()
                .id(factId)
                .importance(0.70)
                .confidence(0.80)
                .build()));
    }
}
