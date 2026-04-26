package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.service.MemoryStateProjector;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.MemoryStateRecord;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import com.lingshu.ai.infrastructure.repository.MemoryStateRecordRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryServiceImplStateBonusTest {

    @Test
    void stateBonus_shouldPromoteFactWithAlignedTaskVectorWhenReadEnabled() {
        MemoryStateRecordRepository stateRepository = mock(MemoryStateRecordRepository.class);
        when(stateRepository.findByFactId(61L)).thenReturn(Optional.of(MemoryStateRecord.builder()
                .factId(61L)
                .taskVector("[1.0,0.0]")
                .taskUncertainty(0.10)
                .build()));
        when(stateRepository.findByFactId(62L)).thenReturn(Optional.of(MemoryStateRecord.builder()
                .factId(62L)
                .taskVector("[-1.0,0.0]")
                .taskUncertainty(0.10)
                .build()));

        TestableMemoryServiceImpl service = createService(stateRepository, true);
        service.setStateContext(new double[]{1.0d, 0.0d});

        FactNode aligned = fact(61L, "用户在准备面试", 0.80, 0.80);
        FactNode antiAligned = fact(62L, "用户在准备面试", 0.80, 0.80);

        double alignedScore = service.computeAdaptiveGraphScoreForTest(aligned, 1, 1.0);
        double antiAlignedScore = service.computeAdaptiveGraphScoreForTest(antiAligned, 1, 1.0);
        List<Long> rankedIds = service.rankGraphHitIdsForTest(List.of(
                new MemoryServiceImpl.GraphRetrievalHit(antiAligned, 1, 1.0),
                new MemoryServiceImpl.GraphRetrievalHit(aligned, 1, 1.0)
        ));

        assertTrue(alignedScore > antiAlignedScore);
        assertEquals(List.of(61L, 62L), rankedIds);
        service.clearStateContext();
    }

    @Test
    void stateBonus_shouldBeIgnoredWhenReadDisabled() {
        MemoryStateRecordRepository stateRepository = mock(MemoryStateRecordRepository.class);
        when(stateRepository.findByFactId(71L)).thenReturn(Optional.of(MemoryStateRecord.builder()
                .factId(71L)
                .taskVector("[1.0,0.0]")
                .taskUncertainty(0.10)
                .build()));

        TestableMemoryServiceImpl service = createService(stateRepository, false);
        service.setStateContext(new double[]{1.0d, 0.0d});

        FactNode fact = fact(71L, "用户在准备面试", 0.80, 0.80);
        double score = service.computeAdaptiveGraphScoreForTest(fact, 1, 1.0);
        double expected = 0.80 * 0.55 + 0.80 * 0.30 + 1.0 * 0.15;

        assertEquals(expected, score, 1e-6);
        service.clearStateContext();
    }

    private static TestableMemoryServiceImpl createService(MemoryStateRecordRepository stateRepository, boolean readEnabled) {
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
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        MemoryStateProjector projector = new MemoryStateProjectorImpl();

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
                Runnable::run,
                snapshotStore,
                stateRepository,
                projector,
                readEnabled
        );
    }

    private static FactNode fact(Long id, String content, double importance, double confidence) {
        return FactNode.builder()
                .id(id)
                .content(content)
                .importance(importance)
                .confidence(confidence)
                .status("active")
                .observedAt(LocalDateTime.now())
                .build();
    }

    private static final class TestableMemoryServiceImpl extends MemoryServiceImpl {

        private TestableMemoryServiceImpl(UserRepository userRepository,
                                          EmbeddingModel embeddingModel,
                                          EmbeddingStore<TextSegment> embeddingStore,
                                          FactRepository factRepository,
                                          Neo4jClient neo4jClient,
                                          SystemLogService systemLogService,
                                          SettingService settingService,
                                          ChatModel chatLanguageModel,
                                          DynamicMemoryModel dynamicMemoryModel,
                                          java.util.concurrent.Executor taskExecutor,
                                          RetrievalContextSnapshotStore snapshotStore,
                                          MemoryStateRecordRepository stateRepository,
                                          MemoryStateProjector memoryStateProjector,
                                          boolean readEnabled) {
            super(userRepository, embeddingModel, embeddingStore, factRepository, neo4jClient, systemLogService,
                    settingService, chatLanguageModel, dynamicMemoryModel, taskExecutor, snapshotStore,
                    stateRepository, memoryStateProjector, readEnabled);
        }

        private void setStateContext(double[] queryVector) {
            setStateBonusContextForTest(queryVector);
        }

        private void clearStateContext() {
            clearStateBonusContextForTest();
        }

        private List<Long> rankGraphHitIdsForTest(List<MemoryServiceImpl.GraphRetrievalHit> hits) {
            return sortGraphHitsByAdaptiveScore(hits).stream()
                    .map(hit -> hit.getFact().getId())
                    .collect(Collectors.toList());
        }

        private double computeAdaptiveGraphScoreForTest(FactNode fact, int hop, double relationWeight) {
            return computeAdaptiveGraphScore(fact, hop, relationWeight);
        }
    }
}
