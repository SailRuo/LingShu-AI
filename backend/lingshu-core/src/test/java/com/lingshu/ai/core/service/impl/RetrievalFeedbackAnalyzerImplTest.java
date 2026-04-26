package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RetrievalFeedbackAnalyzerImplTest {

    @Test
    void productionConstructor_shouldBeExplicitlyMarkedForSpringSelection() throws NoSuchMethodException {
        Constructor<RetrievalFeedbackAnalyzerImpl> constructor =
                RetrievalFeedbackAnalyzerImpl.class.getConstructor(DynamicMemoryModel.class);

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void constructor_shouldBuildFallbackJudgeFromProvidedProductionDependencies() {
        AtomicBoolean factoryInvoked = new AtomicBoolean(false);
        DynamicMemoryModel dynamicMemoryModel = new DynamicMemoryModel(mock(SettingService.class));

        RetrievalFeedbackAnalyzerImpl analyzer = new RetrievalFeedbackAnalyzerImpl(dynamicMemoryModel, model -> {
            factoryInvoked.set(true);
            assertEquals(dynamicMemoryModel, model);
            return (fact, response) -> RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .valid(Boolean.FALSE)
                    .confidence(0.42)
                    .reason("factory-backed fallback")
                    .build();
        });

        RetrievalFeedbackResult result = analyzer.analyze(
                snapshot(410L, List.of(fact(4101L, "用户准备季度复盘", 1))),
                "这周的安排需要更稳一些。"
        );

        assertTrue(factoryInvoked.get());
        assertEquals(410L, result.getTurnId());
        assertEquals(1, result.getFactFeedback().size());
        assertEquals("factory-backed fallback", result.getFactFeedback().get(0).getReason());
        assertEquals(Boolean.FALSE, result.getFactFeedback().get(0).getValid());
    }

    @Test
    void analyze_shouldHandleNullSnapshotSafely() {
        RetrievalFeedbackAnalyzerImpl analyzer = new RetrievalFeedbackAnalyzerImpl((fact, response) -> {
            throw new AssertionError("fallback should not be invoked");
        });

        RetrievalFeedbackResult result = analyzer.analyze(null, "任意回复");

        assertNull(result.getTurnId());
        assertTrue(result.getFactFeedback().isEmpty());
    }

    @Test
    void analyze_shouldHandleEmptyOrMissingContextFactsWithoutFallback() {
        List<Long> judgedFactIds = new ArrayList<>();
        RetrievalFeedbackAnalyzerImpl analyzer = new RetrievalFeedbackAnalyzerImpl((fact, response) -> {
            judgedFactIds.add(fact.getFactId());
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .build();
        });

        RetrievalFeedbackResult nullContextResult = analyzer.analyze(
                RetrievalContextSnapshot.builder()
                        .turnId(420L)
                        .contextFacts(null)
                        .build(),
                "任意回复"
        );
        RetrievalFeedbackResult emptyContextResult = analyzer.analyze(
                snapshot(421L, List.of()),
                "任意回复"
        );

        assertEquals(420L, nullContextResult.getTurnId());
        assertTrue(nullContextResult.getFactFeedback().isEmpty());
        assertEquals(421L, emptyContextResult.getTurnId());
        assertTrue(emptyContextResult.getFactFeedback().isEmpty());
        assertTrue(judgedFactIds.isEmpty());
    }

    @Test
    void analyze_shouldMarkDirectlyReferencedContextFactAsValidWithoutFallbackJudge() {
        List<Long> judgedFactIds = new ArrayList<>();
        RetrievalFeedbackAnalyzerImpl analyzer = new RetrievalFeedbackAnalyzerImpl((fact, response) -> {
            judgedFactIds.add(fact.getFactId());
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .valid(false)
                    .confidence(0.11)
                    .reason("fallback should not be invoked")
                    .build();
        });

        RetrievalContextSnapshot snapshot = snapshot(501L, List.of(
                fact(101L, "用户喜欢抹茶拿铁", 1),
                fact(102L, "用户准备季度复盘", 2)
        ));

        RetrievalFeedbackResult result = analyzer.analyze(
                snapshot,
                "你之前提到自己喜欢抹茶拿铁，也在准备季度复盘。"
        );

        assertEquals(501L, result.getTurnId());
        assertEquals(2, result.getFactFeedback().size());
        assertTrue(result.getFactFeedback().stream().allMatch(item -> Boolean.TRUE.equals(item.getValid())));
        assertTrue(result.getFactFeedback().stream().allMatch(item -> item.getConfidence() != null && item.getConfidence() >= 0.9d));
        assertTrue(result.getFactFeedback().stream().allMatch(item -> item.getReason().contains("direct")));
        assertTrue(judgedFactIds.isEmpty());
    }

    @Test
    void analyze_shouldUseFallbackJudgeForNonObviousContextFact() {
        List<Long> judgedFactIds = new ArrayList<>();
        RetrievalFeedbackAnalyzerImpl analyzer = new RetrievalFeedbackAnalyzerImpl((fact, response) -> {
            judgedFactIds.add(fact.getFactId());
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .valid(Boolean.FALSE)
                    .confidence(0.37)
                    .reason("fallback judged unsupported")
                    .build();
        });

        RetrievalContextSnapshot snapshot = snapshot(601L, List.of(
                fact(201L, "用户准备季度复盘", 1)
        ));

        RetrievalFeedbackResult result = analyzer.analyze(
                snapshot,
                "把最近这段经历整理成汇报会更稳妥。"
        );

        assertEquals(List.of(201L), judgedFactIds);
        assertEquals(1, result.getFactFeedback().size());
        RetrievalFeedbackResult.FactFeedback feedback = result.getFactFeedback().get(0);
        assertEquals(201L, feedback.getFactId());
        assertEquals(Boolean.FALSE, feedback.getValid());
        assertEquals(0.37, feedback.getConfidence());
        assertEquals("fallback judged unsupported", feedback.getReason());
    }

    @Test
    void analyze_shouldKeepTurnAndFactAlignmentForMixedDirectAndFallbackOutcomes() {
        RetrievalFeedbackAnalyzerImpl analyzer = new RetrievalFeedbackAnalyzerImpl((fact, response) -> {
            if (Long.valueOf(302L).equals(fact.getFactId())) {
                return RetrievalFeedbackResult.FactFeedback.builder()
                        .factId(fact.getFactId())
                        .valid(null)
                        .confidence(null)
                        .reason("judge unable to determine")
                        .build();
            }
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .valid(Boolean.FALSE)
                    .confidence(0.21)
                    .reason("judge says not supported")
                    .build();
        });

        RetrievalContextSnapshot snapshot = snapshot(701L, List.of(
                fact(301L, "用户最近在看系统设计", 1),
                fact(302L, "用户想把旅行计划改到五月", 2)
        ));

        RetrievalFeedbackResult result = analyzer.analyze(
                snapshot,
                "你最近在看系统设计，这件事我记得很清楚。"
        );

        assertEquals(701L, result.getTurnId());
        assertEquals(List.of(301L, 302L), result.getFactFeedback().stream().map(RetrievalFeedbackResult.FactFeedback::getFactId).toList());

        RetrievalFeedbackResult.FactFeedback directFeedback = result.getFactFeedback().get(0);
        assertEquals(Boolean.TRUE, directFeedback.getValid());
        assertTrue(directFeedback.getConfidence() != null && directFeedback.getConfidence() >= 0.9d);

        RetrievalFeedbackResult.FactFeedback judgedFeedback = result.getFactFeedback().get(1);
        assertNull(judgedFeedback.getValid());
        assertNull(judgedFeedback.getConfidence());
        assertFalse(Boolean.TRUE.equals(judgedFeedback.getValid()));
        assertEquals("judge unable to determine", judgedFeedback.getReason());
    }

    private static RetrievalContextSnapshot snapshot(Long turnId, List<RetrievalFactCandidate> contextFacts) {
        return RetrievalContextSnapshot.builder()
                .userId("alice")
                .sessionId(88L)
                .turnId(turnId)
                .query("最近在忙什么")
                .contextFacts(contextFacts)
                .createdAt(LocalDateTime.of(2026, 4, 26, 11, 0))
                .build();
    }

    private static RetrievalFactCandidate fact(Long factId, String content, int rank) {
        return RetrievalFactCandidate.builder()
                .factId(factId)
                .content(content)
                .source("GRAPH")
                .rank(rank)
                .build();
    }
}
