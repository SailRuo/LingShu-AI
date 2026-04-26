package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.MemoryRetrievalEvent;
import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryServiceImplAdaptiveRankingTest {

    @Test
    void rankGraphFactsForTest_shouldPreferHigherConfidenceWhenImportanceIsClose() {
        FactNode higherConfidence = fact(11L, "用户最近在准备面试", 0.84, 0.92, "active");
        FactNode lowerConfidence = fact(12L, "用户最近在准备面试", 0.86, 0.52, "active");

        TestableMemoryServiceImpl service = createService();

        List<Long> rankedIds = service.rankGraphFactIdsForTest(List.of(lowerConfidence, higherConfidence));

        assertEquals(List.of(11L, 12L), rankedIds);
    }

    @Test
    void rankGraphFactsForTest_shouldUseSecondHopRelationWeightInOrdering() {
        FactNode strongerRelation = fact(21L, "用户和面试项目相关", 0.74, 0.74, "active");
        FactNode weakerRelation = fact(22L, "用户和面试项目相关", 0.74, 0.74, "active");

        TestableMemoryServiceImpl service = createService();
        double strongerScore = service.computeAdaptiveGraphScoreForTest(strongerRelation, 2, 0.95);
        double weakerScore = service.computeAdaptiveGraphScoreForTest(weakerRelation, 2, 0.25);

        List<Long> rankedIds = service.rankGraphHitIdsForTest(List.of(
                new MemoryServiceImpl.GraphRetrievalHit(weakerRelation, 2, 0.25),
                new MemoryServiceImpl.GraphRetrievalHit(strongerRelation, 2, 0.95)
        ));

        assertTrue(strongerScore > weakerScore);
        assertEquals(List.of(21L, 22L), rankedIds);
    }

    @Test
    void computeAdaptiveGraphScore_shouldKeepSupersededAndHopPenaltyEffective() {
        FactNode activeOneHop = fact(31L, "用户最近在准备面试", 0.78, 0.82, "active");
        FactNode superseded = fact(32L, "用户最近在准备面试", 0.96, 0.96, "superseded");
        FactNode secondHop = fact(33L, "用户最近在准备面试", 0.90, 0.90, "active");

        TestableMemoryServiceImpl service = createService();

        double activeScore = service.computeAdaptiveGraphScoreForTest(activeOneHop, 1, 1.0);
        double supersededScore = service.computeAdaptiveGraphScoreForTest(superseded, 1, 1.0);
        double secondHopScore = service.computeAdaptiveGraphScoreForTest(secondHop, 2, 1.0);

        assertTrue(activeScore > supersededScore);
        assertTrue(activeScore > secondHopScore);
    }

    @Test
    void retrieveContext_withNullTurnId_shouldKeepLegacyRelationLearningFallback() {
        UserRepository userRepository = mock(UserRepository.class);
        FactRepository factRepository = mock(FactRepository.class);
        when(factRepository.findRelatedRelationWeight(41L, 42L)).thenReturn(null);

        FactNode left = fact(41L, "用户最近在准备面试", 0.85, 0.88, "active");
        FactNode right = fact(42L, "用户最近在刷算法题", 0.84, 0.87, "active");
        FactNode extra = fact(43L, "用户最近在整理面试项目", 0.83, 0.86, "active");
        var user = com.lingshu.ai.infrastructure.entity.UserNode.builder()
                .name("alice")
                .facts(new java.util.LinkedHashSet<>(List.of(left, right, extra)))
                .build();
        when(userRepository.findByName("alice")).thenReturn(Optional.of(user));

        TestableMemoryServiceImpl service = createService(userRepository, factRepository);
        service.forceGraphHits(List.of(
                new MemoryServiceImpl.GraphRetrievalHit(left, 1, 1.0),
                new MemoryServiceImpl.GraphRetrievalHit(right, 1, 1.0),
                new MemoryServiceImpl.GraphRetrievalHit(extra, 1, 1.0)
        ));

        service.retrieveContext("alice", null, null, "面试");
        service.retrieveContext("alice", null, null, "面试");

        verify(factRepository, atLeastOnce()).saveRelatedRelations(anyList());
    }

    @Test
    void computeAdaptiveGraphScore_shouldUseReadTimeConfidenceFallbackForLegacyFacts() {
        FactNode legacyZeroConfidence = fact(51L, "用户最近在准备面试", 0.84, 0.0, "active");
        FactNode explicitLowConfidence = fact(52L, "用户最近在准备面试", 0.83, 0.55, "active");

        TestableMemoryServiceImpl service = createService();

        double legacyScore = service.computeAdaptiveGraphScoreForTest(legacyZeroConfidence, 1, 1.0);
        double lowConfidenceScore = service.computeAdaptiveGraphScoreForTest(explicitLowConfidence, 1, 1.0);
        List<Long> rankedIds = service.rankGraphFactIdsForTest(List.of(explicitLowConfidence, legacyZeroConfidence));

        assertTrue(legacyScore > lowConfidenceScore);
        assertEquals(List.of(51L, 52L), rankedIds);
    }

    private static TestableMemoryServiceImpl createService() {
        return createService(mock(UserRepository.class), mock(FactRepository.class));
    }

    private static TestableMemoryServiceImpl createService(UserRepository userRepository, FactRepository factRepository) {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        SettingService settingService = mock(SettingService.class);
        ChatModel chatLanguageModel = mock(ChatModel.class);
        DynamicMemoryModel dynamicMemoryModel = mock(DynamicMemoryModel.class);

        return new TestableMemoryServiceImpl(
                userRepository,
                embeddingModel,
                embeddingStore,
                factRepository,
                neo4jClient,
                systemLogService,
                settingService,
                chatLanguageModel,
                dynamicMemoryModel,
                Runnable::run
        );
    }

    private static FactNode fact(Long id, String content, double importance, double confidence, String status) {
        return FactNode.builder()
                .id(id)
                .content(content)
                .importance(importance)
                .confidence(confidence)
                .status(status)
                .observedAt(LocalDateTime.now())
                .build();
    }

    private static final class TestableMemoryServiceImpl extends MemoryServiceImpl {
        private List<MemoryServiceImpl.GraphRetrievalHit> forcedGraphHits;

        private TestableMemoryServiceImpl(UserRepository userRepository,
                                          EmbeddingModel embeddingModel,
                                          EmbeddingStore<TextSegment> embeddingStore,
                                          FactRepository factRepository,
                                          Neo4jClient neo4jClient,
                                          SystemLogService systemLogService,
                                          SettingService settingService,
                                          ChatModel chatLanguageModel,
                                          DynamicMemoryModel dynamicMemoryModel,
                                          java.util.concurrent.Executor taskExecutor) {
            super(userRepository, embeddingModel, embeddingStore, factRepository, neo4jClient, systemLogService,
                    settingService, chatLanguageModel, dynamicMemoryModel, taskExecutor);
        }

        private void forceGraphHits(List<MemoryServiceImpl.GraphRetrievalHit> hits) {
            this.forcedGraphHits = hits;
        }

        @Override
        protected List<MemoryServiceImpl.GraphRetrievalHit> performGraphRetrievalV2(String userId, String message,
                                                                                    List<String> entities,
                                                                                    MemoryRetrievalEvent.MemoryRetrievalEventBuilder eventBuilder) {
            if (forcedGraphHits != null) {
                eventBuilder.graphMatchedIds(forcedGraphHits.stream()
                        .map(hit -> hit.getFact().getId())
                        .collect(Collectors.toList()));
                eventBuilder.graphMatchedContent(forcedGraphHits.stream()
                        .map(hit -> hit.getFact().getContent())
                        .collect(Collectors.toList()));
                return forcedGraphHits;
            }
            return super.performGraphRetrievalV2(userId, message, entities, eventBuilder);
        }

        private List<Long> rankGraphFactIdsForTest(List<FactNode> facts) {
            return rankGraphHitIdsForTest(facts.stream()
                    .map(fact -> new MemoryServiceImpl.GraphRetrievalHit(fact, 1, 1.0))
                    .collect(Collectors.toList()));
        }

        private List<Long> rankGraphHitIdsForTest(List<MemoryServiceImpl.GraphRetrievalHit> hits) {
            return sortGraphHitsByAdaptiveScore(hits).stream()
                    .map(hit -> hit.getFact().getId())
                    .collect(Collectors.toList());
        }

        private double computeAdaptiveGraphScoreForTest(FactNode fact, int hop, double relationWeight) {
            return computeAdaptiveGraphScore(fact, hop, relationWeight);
        }

        @SuppressWarnings("unused")
        private double calculateGainForTest(List<String> entities, List<MemoryServiceImpl.GraphRetrievalHit> hits) {
            return calculateGainV2(entities, hits);
        }

        @SuppressWarnings("unused")
        private MemoryRetrievalEvent.MemoryRetrievalEventBuilder eventBuilder() {
            return MemoryRetrievalEvent.builder();
        }
    }
}
