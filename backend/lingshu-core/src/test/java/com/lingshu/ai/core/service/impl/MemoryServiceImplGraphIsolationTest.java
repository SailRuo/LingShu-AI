package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryServiceImplGraphIsolationTest {

    @Test
    void getGraphData_shouldOnlyContainRequestedUser_whenUserIdProvided() {
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

        MemoryServiceImpl memoryService = new MemoryServiceImpl(
                userRepository,
                embeddingModel,
                embeddingStore,
                factRepository,
                neo4jClient,
                systemLogService,
                settingService,
                chatLanguageModel,
                dynamicMemoryModel,
                executor
        );

        UserNode alice = UserNode.builder().name("alice").build();
        UserNode bob = UserNode.builder().name("bob").build();
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(userRepository.findByName("alice")).thenReturn(Optional.of(alice));

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) memoryService.getGraphData("alice");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");

        boolean hasAlice = nodes.stream().anyMatch(node -> "user_alice".equals(node.get("id")));
        boolean hasBob = nodes.stream().anyMatch(node -> "user_bob".equals(node.get("id")));

        assertTrue(hasAlice);
        assertFalse(hasBob);
    }
}
