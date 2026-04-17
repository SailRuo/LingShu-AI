package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;
    @Mock
    private FactRepository factRepository;
    @Mock
    private Neo4jClient neo4jClient;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private ChatModel chatModel;
    @Mock
    private java.util.concurrent.Executor taskExecutor;

    @InjectMocks
    private MemoryServiceImpl memoryService;

    @Test
    void retrieveContext_GraphOnlyPath_WhenGainIsLow() {
        // Setup user with facts
        UserNode user = UserNode.builder().name("user1").build();
        FactNode fact = FactNode.builder().id(1L).content("User lives in Beijing").importance(1.0).build();
        user.setFacts(new HashSet<>(Collections.singletonList(fact)));
        when(userRepository.findByName("user1")).thenReturn(Optional.of(user));

        // Query that doesn't trigger high gain
        String context = memoryService.retrieveContext("user1", "Hello");

        assertThat(context).contains("User lives in Beijing");
        // Should not call vector search since graph exists and gain < threshold
        verify(embeddingStore, never()).search(any());
    }

    @Test
    void retrieveContext_VectorBackupPath_WhenGraphIsEmpty() {
        // Setup user with NO facts
        UserNode user = UserNode.builder().name("user2").build();
        user.setFacts(new HashSet<>());
        when(userRepository.findByName("user2")).thenReturn(Optional.of(user));

        // Mock Vector Search
        when(embeddingModel.embed(anyString())).thenReturn(dev.langchain4j.model.output.Response.from(
                dev.langchain4j.data.embedding.Embedding.from(new float[]{0.1f})));
        
        EmbeddingSearchResult<TextSegment> mockResult = new EmbeddingSearchResult<>(Collections.emptyList());
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(mockResult);

        memoryService.retrieveContext("user2", "Some query");

        // Should call vector search as backup
        verify(embeddingStore).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void retrieveContext_VectorSupplementPath_WhenGainIsHigh() {
        // Setup user with facts that will trigger high gain
        UserNode user = UserNode.builder().name("user3").build();
        FactNode fact = FactNode.builder().id(1L).content("User loves java programming").importance(1.0).build();
        user.setFacts(new HashSet<>(Collections.singletonList(fact)));
        when(userRepository.findByName("user3")).thenReturn(Optional.of(user));

        // Mock Vector Search
        when(embeddingModel.embed(anyString())).thenReturn(dev.langchain4j.model.output.Response.from(
                dev.langchain4j.data.embedding.Embedding.from(new float[]{0.1f})));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(Collections.emptyList()));

        // Query "java" should trigger entity "java" which matches the fact
        memoryService.retrieveContext("user3", "Tell me about my java skills");

        // Should call vector search as supplement
        verify(embeddingStore).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void rebuildAllEmbeddings_ShouldCallRemoveBeforeAdd() {
        // Setup users and facts
        UserNode user = UserNode.builder().name("user_rebuild").build();
        FactNode fact = FactNode.builder().id(99L).content("Rebuild test fact").status("active").build();
        user.setFacts(new HashSet<>(Collections.singletonList(fact)));
        when(userRepository.findAll()).thenReturn(Collections.singletonList(user));
        when(embeddingModel.embed(any(TextSegment.class))).thenReturn(dev.langchain4j.model.output.Response.from(
                dev.langchain4j.data.embedding.Embedding.from(new float[]{0.1f})));

        memoryService.rebuildAllEmbeddings();

        // Should remove old embedding for this fact first
        verify(embeddingStore).removeAll(any(dev.langchain4j.store.embedding.filter.Filter.class));
        // Should then add the new one
        verify(embeddingStore).add(any(dev.langchain4j.data.embedding.Embedding.class), any(dev.langchain4j.data.segment.TextSegment.class));
    }

    @Test
    void updateRelationshipsFromRetrievalEvent_ShouldCreateRelatedRelationAfterTwoCooccurrences() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
        when(factRepository.findRelatedRelationWeight(1L, 2L)).thenReturn(null);

        seedRetrievalEvents(2L, Arrays.asList(1L, 2L));

        memoryService.updateRelationshipsFromRetrievalEvent(eventWithFacts(1L, 2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(factRepository).saveRelatedRelations(captor.capture());
        Map<String, Object> relation = captor.getValue().get(0);
        assertThat(relation.get("sourceId")).isEqualTo(1L);
        assertThat(relation.get("targetId")).isEqualTo(2L);
        assertThat(((Number) relation.get("weight")).doubleValue()).isEqualTo(0.5d);
    }

    @Test
    void updateRelationshipsFromRetrievalEvent_ShouldStrengthenExistingRelation() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
        when(factRepository.findRelatedRelationWeight(1L, 2L)).thenReturn(0.6d);

        seedRetrievalEvents(2L, Arrays.asList(1L, 2L));

        memoryService.updateRelationshipsFromRetrievalEvent(eventWithFacts(1L, 2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(factRepository).saveRelatedRelations(captor.capture());
        Map<String, Object> relation = captor.getValue().get(0);
        assertThat(((Number) relation.get("weight")).doubleValue()).isEqualTo(0.7d);
    }

    @Test
    void calculateGainV2_ShouldApplyHopStatusAndAgePenalties() throws Exception {
        FactNode fact = FactNode.builder()
                .id(7L)
                .content("user likes tea")
                .importance(1.0)
                .status("superseded")
                .observedAt(LocalDateTime.now().minusDays(120))
                .build();
        Object hit = newGraphRetrievalHit(fact, 2, 1.0d);

        Method method = MemoryServiceImpl.class.getDeclaredMethod("calculateGainV2", List.class, List.class);
        method.setAccessible(true);
        double gain = (double) method.invoke(memoryService, List.of("tea"), List.of(hit));

        assertThat(gain).isCloseTo(0.6588d, within(0.0001d));
    }

    @Test
    void deduplicateGraphHits_ShouldPreferOneHopFactOverSecondHopDuplicate() throws Exception {
        FactNode fact = FactNode.builder().id(11L).content("shared fact").importance(0.9).build();
        Object oneHop = newGraphRetrievalHit(fact, 1, 1.0d);
        Object secondHop = newGraphRetrievalHit(fact, 2, 0.5d);

        Method method = MemoryServiceImpl.class.getDeclaredMethod("deduplicateGraphHits", List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> deduped = (List<Object>) method.invoke(memoryService, List.of(oneHop, secondHop));

        assertThat(deduped).hasSize(1);
        Field hopField = deduped.get(0).getClass().getDeclaredField("hop");
        hopField.setAccessible(true);
        assertThat(hopField.getInt(deduped.get(0))).isEqualTo(1);
    }

    private void seedRetrievalEvents(Long... factIds) throws Exception {
        Field field = MemoryServiceImpl.class.getDeclaredField("recentRetrievalEvents");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Queue<com.lingshu.ai.core.dto.MemoryRetrievalEvent> queue =
                (Queue<com.lingshu.ai.core.dto.MemoryRetrievalEvent>) field.get(memoryService);
        queue.clear();
        queue.add(eventWithFacts(factIds));
        queue.add(eventWithFacts(factIds));
    }

    private com.lingshu.ai.core.dto.MemoryRetrievalEvent eventWithFacts(Long... factIds) {
        return com.lingshu.ai.core.dto.MemoryRetrievalEvent.builder()
                .userId("user1")
                .query("test")
                .timestamp(LocalDateTime.now())
                .adoptedFactIds(Arrays.asList(factIds))
                .build();
    }

    private Object newGraphRetrievalHit(FactNode fact, int hop, double relationWeight) throws Exception {
        Class<?> clazz = Class.forName("com.lingshu.ai.core.service.impl.MemoryServiceImpl$GraphRetrievalHit");
        Constructor<?> constructor = clazz.getDeclaredConstructor(FactNode.class, int.class, double.class);
        constructor.setAccessible(true);
        return constructor.newInstance(fact, hop, relationWeight);
    }
}
