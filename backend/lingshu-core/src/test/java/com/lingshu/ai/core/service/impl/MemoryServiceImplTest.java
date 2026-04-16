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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
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
}
