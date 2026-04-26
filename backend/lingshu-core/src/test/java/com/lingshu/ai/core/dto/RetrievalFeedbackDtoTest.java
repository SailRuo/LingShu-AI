package com.lingshu.ai.core.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalFeedbackDtoTest {

    @Test
    void snapshot_shouldTreatContextFactsAsSingleMembershipAuthority() {
        RetrievalFactCandidate retrievedFact = RetrievalFactCandidate.builder()
                .factId(101L)
                .content("用户最近在准备面试")
                .source("GRAPH")
                .rank(1)
                .build();

        RetrievalFactCandidate contextFact = RetrievalFactCandidate.builder()
                .factId(102L)
                .content("用户在整理系统设计题")
                .source("GRAPH")
                .rank(2)
                .build();

        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .userId("alice")
                .sessionId(11L)
                .turnId(22L)
                .query("我最近在忙什么")
                .routingDecision("GRAPH_PRIORITIZED_VECTOR_SUPPLEMENT")
                .gain(0.41)
                .retrievedFacts(List.of(retrievedFact, contextFact))
                .contextFacts(List.of(contextFact))
                .createdAt(LocalDateTime.of(2026, 4, 26, 10, 30))
                .build();

        assertEquals(List.of(retrievedFact, contextFact), snapshot.getRetrievedFacts());
        assertEquals(22L, snapshot.getTurnId());
        assertTrue(snapshot.hasContextFacts());
        assertEquals(102L, snapshot.getContextFacts().get(0).getFactId());
        assertSame(contextFact, snapshot.getContextFacts().get(0));
        assertFalse(snapshot.getContextFacts().contains(retrievedFact));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getRetrievedFacts().add(contextFact));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getContextFacts().add(retrievedFact));
    }

    @Test
    void feedbackResult_shouldExposeNeutralFactFeedbackIncludingInvalidEntries() {
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(22L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder()
                                .factId(102L)
                                .valid(true)
                                .confidence(0.92)
                                .reason("答案直接引用了该事实")
                                .build(),
                        RetrievalFeedbackResult.FactFeedback.builder()
                                .factId(103L)
                                .valid(false)
                                .confidence(0.27)
                                .reason("答案与该事实不一致")
                                .build()
                ))
                .build();

        assertEquals(2, result.getFactFeedback().size());
        assertEquals(Boolean.TRUE, result.getFactFeedback().get(0).getValid());
        assertEquals(Boolean.FALSE, result.getFactFeedback().get(1).getValid());
        assertEquals(103L, result.getFactFeedback().get(1).getFactId());
        assertThrows(UnsupportedOperationException.class, () -> result.getFactFeedback().add(
                RetrievalFeedbackResult.FactFeedback.builder().factId(104L).build()
        ));
    }

    @Test
    void dtoBuilders_shouldNormalizeNullCollectionsAndFeedbackFields() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(22L)
                .retrievedFacts(null)
                .contextFacts(null)
                .build();

        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(22L)
                .factFeedback(null)
                .build();

        RetrievalFeedbackResult.FactFeedback feedback = RetrievalFeedbackResult.FactFeedback.builder()
                .factId(104L)
                .reason("未提供判断结果")
                .build();

        assertEquals(List.of(), snapshot.getRetrievedFacts());
        assertEquals(List.of(), snapshot.getContextFacts());
        assertFalse(snapshot.hasContextFacts());
        assertEquals(List.of(), result.getFactFeedback());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getRetrievedFacts().add(
                RetrievalFactCandidate.builder().factId(105L).build()
        ));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getContextFacts().add(
                RetrievalFactCandidate.builder().factId(106L).build()
        ));
        assertThrows(UnsupportedOperationException.class, () -> result.getFactFeedback().add(
                RetrievalFeedbackResult.FactFeedback.builder().factId(107L).build()
        ));
        assertNull(feedback.getValid());
        assertNull(feedback.getConfidence());
    }

    @Test
    void collectionGetters_shouldExposeReadOnlyStateDetachedFromSourceLists() {
        RetrievalFactCandidate fact = RetrievalFactCandidate.builder()
                .factId(201L)
                .content("用户在准备复盘")
                .source("GRAPH")
                .rank(1)
                .build();
        RetrievalFeedbackResult.FactFeedback feedback = RetrievalFeedbackResult.FactFeedback.builder()
                .factId(201L)
                .valid(Boolean.TRUE)
                .confidence(0.88)
                .reason("答案提到了该事实")
                .build();

        List<RetrievalFactCandidate> retrievedFacts = new ArrayList<>(List.of(fact));
        List<RetrievalFactCandidate> contextFacts = new ArrayList<>(List.of(fact));
        List<RetrievalFeedbackResult.FactFeedback> factFeedback = new ArrayList<>(List.of(feedback));

        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(23L)
                .retrievedFacts(retrievedFacts)
                .contextFacts(contextFacts)
                .createdAt(LocalDateTime.of(2026, 4, 26, 10, 31))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(23L)
                .factFeedback(factFeedback)
                .build();

        retrievedFacts.add(RetrievalFactCandidate.builder().factId(202L).build());
        contextFacts.clear();
        factFeedback.clear();

        assertEquals(1, snapshot.getRetrievedFacts().size());
        assertEquals(1, snapshot.getContextFacts().size());
        assertEquals(1, result.getFactFeedback().size());
        assertEquals(List.of(fact), snapshot.getRetrievedFacts());
        assertEquals(List.of(fact), snapshot.getContextFacts());
        assertEquals(List.of(feedback), result.getFactFeedback());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getRetrievedFacts().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getContextFacts().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getFactFeedback().clear());
    }

    @Test
    void snapshotStore_shouldUseOptionalForAbsenceSemantics() throws NoSuchMethodException {
        Method method = RetrievalContextSnapshotStore.class.getMethod("findByTurnId", Long.class);
        assertEquals(Optional.class, method.getReturnType());
    }

    @Test
    void dtos_shouldSupportJacksonRoundTripWhilePreservingValueLikeCollections() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        RetrievalFactCandidate retrievedFact = RetrievalFactCandidate.builder()
                .factId(301L)
                .content("用户在准备季度总结")
                .source("GRAPH")
                .rank(1)
                .build();
        RetrievalFactCandidate contextFact = RetrievalFactCandidate.builder()
                .factId(302L)
                .content("用户想复习系统设计")
                .source("GRAPH")
                .rank(2)
                .build();
        RetrievalFeedbackResult.FactFeedback feedback = RetrievalFeedbackResult.FactFeedback.builder()
                .factId(302L)
                .valid(Boolean.TRUE)
                .confidence(0.91)
                .reason("答案明确使用了该事实")
                .build();

        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .userId("alice")
                .sessionId(11L)
                .turnId(24L)
                .query("我最近在忙什么")
                .routingDecision("GRAPH_PRIORITIZED_VECTOR_SUPPLEMENT")
                .gain(0.42)
                .retrievedFacts(List.of(retrievedFact, contextFact))
                .contextFacts(List.of(contextFact))
                .createdAt(LocalDateTime.of(2026, 4, 26, 10, 32))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(24L)
                .factFeedback(List.of(feedback))
                .build();

        RetrievalContextSnapshot restoredSnapshot = objectMapper.readValue(
                objectMapper.writeValueAsString(snapshot),
                RetrievalContextSnapshot.class
        );
        RetrievalFeedbackResult restoredResult = objectMapper.readValue(
                objectMapper.writeValueAsString(result),
                RetrievalFeedbackResult.class
        );

        assertEquals(snapshot.getTurnId(), restoredSnapshot.getTurnId());
        assertEquals(snapshot.getRetrievedFacts(), restoredSnapshot.getRetrievedFacts());
        assertEquals(snapshot.getContextFacts(), restoredSnapshot.getContextFacts());
        assertTrue(restoredSnapshot.hasContextFacts());
        assertEquals(result.getFactFeedback(), restoredResult.getFactFeedback());
        assertEquals(Boolean.TRUE, restoredResult.getFactFeedback().get(0).getValid());
        assertThrows(UnsupportedOperationException.class, () -> restoredSnapshot.getRetrievedFacts().add(retrievedFact));
        assertThrows(UnsupportedOperationException.class, () -> restoredSnapshot.getContextFacts().clear());
        assertThrows(UnsupportedOperationException.class, () -> restoredResult.getFactFeedback().add(feedback));
    }
}
