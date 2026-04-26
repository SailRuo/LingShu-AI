package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
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

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MemoryServiceImplRetrievalSnapshotTest {

    @Test
    void retrieveContext_shouldStoreSnapshotForInteractiveTurn() {
        UserRepository userRepository = mock(UserRepository.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        FactRepository factRepository = mock(FactRepository.class);
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        SettingService settingService = mock(SettingService.class);
        ChatModel chatLanguageModel = mock(ChatModel.class);
        DynamicMemoryModel dynamicMemoryModel = mock(DynamicMemoryModel.class);
        Executor executor = Runnable::run;
        RetrievalContextSnapshotStore snapshotStore = new InMemoryRetrievalContextSnapshotStore();

        FactNode graphFact = FactNode.builder()
                .id(101L)
                .content("Alice plans Kyoto trip")
                .observedAt(LocalDateTime.of(2026, 4, 1, 9, 30))
                .importance(0.9)
                .status("active")
                .build();

        MemoryServiceImpl memoryService = new TestMemoryServiceImpl(
                userRepository,
                embeddingModel,
                embeddingStore,
                factRepository,
                neo4jClient,
                systemLogService,
                settingService,
                chatLanguageModel,
                dynamicMemoryModel,
                executor,
                snapshotStore,
                List.of(new MemoryServiceImpl.GraphRetrievalHit(graphFact, 1, 1.0)),
                List.of(
                        createRetrievedFact(101L, "Alice plans Kyoto trip", "vector", 1, null),
                        createRetrievedFact(202L, "She booked a tea ceremony", "vector", 2, null)
                ),
                0.6
        );

        String context = memoryService.retrieveContext("alice", 1L, 99L, "我最近在忙什么");

        RetrievalContextSnapshot snapshot = snapshotStore.findByTurnId(99L).orElseThrow();
        assertTrue(context.contains("Alice plans Kyoto trip"));
        assertTrue(context.contains("[记录于:"));
        assertTrue(context.contains("She booked a tea ceremony"));
        assertEquals("alice", snapshot.getUserId());
        assertEquals(1L, snapshot.getSessionId());
        assertEquals(99L, snapshot.getTurnId());
        assertEquals("我最近在忙什么", snapshot.getQuery());
        assertEquals(3, snapshot.getRetrievedFacts().size());
        assertEquals(List.of(101L, 101L, 202L), snapshot.getRetrievedFacts().stream()
                .map(RetrievalFactCandidate::getFactId)
                .toList());
        assertEquals(List.of("graph", "vector", "vector"), snapshot.getRetrievedFacts().stream()
                .map(RetrievalFactCandidate::getSource)
                .toList());
        assertEquals(2, snapshot.getContextFacts().size());
        assertEquals(101L, snapshot.getContextFacts().get(0).getFactId());
        assertEquals("graph", snapshot.getContextFacts().get(0).getSource());
        assertEquals(1, snapshot.getContextFacts().get(0).getRank());
        assertTrue(snapshot.getContextFacts().get(0).getContent().contains("[记录于:"));
        assertEquals(202L, snapshot.getContextFacts().get(1).getFactId());
        assertEquals("vector", snapshot.getContextFacts().get(1).getSource());
        assertEquals(2, snapshot.getContextFacts().get(1).getRank());
        assertEquals("She booked a tea ceremony", snapshot.getContextFacts().get(1).getContent());
        assertTrue(snapshot.getCreatedAt() != null);
    }

    @Test
    void snapshotStore_shouldEvictOldestTurnsWhenCapacityExceeded() {
        InMemoryRetrievalContextSnapshotStore snapshotStore = new InMemoryRetrievalContextSnapshotStore(2);

        snapshotStore.save(snapshotForTurn(1L));
        snapshotStore.save(snapshotForTurn(2L));
        snapshotStore.save(snapshotForTurn(3L));

        assertTrue(snapshotStore.findByTurnId(1L).isEmpty());
        assertTrue(snapshotStore.findByTurnId(2L).isPresent());
        assertTrue(snapshotStore.findByTurnId(3L).isPresent());
        assertFalse(snapshotStore.findByTurnId(null).isPresent());
    }

    private static RetrievalContextSnapshot snapshotForTurn(Long turnId) {
        return RetrievalContextSnapshot.builder()
                .turnId(turnId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static MemoryServiceImpl.RetrievedFact createRetrievedFact(Long factId,
                                                                       String content,
                                                                       String source,
                                                                       int rank,
                                                                       FactNode graphFact) {
        try {
            Constructor<MemoryServiceImpl.RetrievedFact> constructor = MemoryServiceImpl.RetrievedFact.class
                    .getDeclaredConstructor(Long.class, String.class, String.class, int.class, FactNode.class);
            constructor.setAccessible(true);
            return constructor.newInstance(factId, content, source, rank, graphFact);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TestMemoryServiceImpl extends MemoryServiceImpl {

        private final List<GraphRetrievalHit> graphHits;
        private final List<RetrievedFact> vectorHits;
        private final double gain;

        private TestMemoryServiceImpl(UserRepository userRepository,
                                      EmbeddingModel embeddingModel,
                                      EmbeddingStore<TextSegment> embeddingStore,
                                      FactRepository factRepository,
                                      Neo4jClient neo4jClient,
                                      SystemLogService systemLogService,
                                      SettingService settingService,
                                      ChatModel chatLanguageModel,
                                      DynamicMemoryModel dynamicMemoryModel,
                                      Executor executor,
                                      RetrievalContextSnapshotStore snapshotStore,
                                      List<GraphRetrievalHit> graphHits,
                                      List<RetrievedFact> vectorHits,
                                      double gain) {
            super(userRepository, embeddingModel, embeddingStore, factRepository, neo4jClient, systemLogService,
                    settingService, chatLanguageModel, dynamicMemoryModel, executor, snapshotStore);
            this.graphHits = graphHits;
            this.vectorHits = vectorHits;
            this.gain = gain;
        }

        @Override
        protected List<String> extractEntities(String message) {
            return List.of("alice", "kyoto");
        }

        @Override
        protected List<GraphRetrievalHit> performGraphRetrievalV2(String userId,
                                                                  String message,
                                                                  List<String> entities,
                                                                  com.lingshu.ai.core.dto.MemoryRetrievalEvent.MemoryRetrievalEventBuilder eventBuilder) {
            return graphHits;
        }

        @Override
        protected List<RetrievedFact> performVectorRetrievalV2(String userId,
                                                               String message,
                                                               com.lingshu.ai.core.dto.MemoryRetrievalEvent.MemoryRetrievalEventBuilder eventBuilder) {
            return vectorHits;
        }

        @Override
        protected double calculateGainV2(List<String> entities, List<GraphRetrievalHit> activatedFacts) {
            return gain;
        }

        @Override
        public void updateRelationshipsFromRetrievalEvent(com.lingshu.ai.core.dto.MemoryRetrievalEvent event) {
            // No-op for this focused retrieval snapshot test.
        }
    }
}
