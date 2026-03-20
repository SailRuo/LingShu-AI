package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.FactExtractor;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class MemoryServiceImpl implements MemoryService {

    private final UserRepository userRepository;
    private final ChatLanguageModel model;
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final dev.langchain4j.store.embedding.EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore;
    private final FactRepository factRepository;
    private FactExtractor factExtractor;

    public MemoryServiceImpl(UserRepository userRepository, 
                             ChatLanguageModel model,
                             dev.langchain4j.model.embedding.EmbeddingModel embeddingModel,
                             dev.langchain4j.store.embedding.EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore,
                             FactRepository factRepository) {
        this.userRepository = userRepository;
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.factRepository = factRepository;
    }

    @PostConstruct
    public void init() {
        this.factExtractor = AiServices.create(FactExtractor.class, model);
    }

    @Override
    public void extractFacts(String userId, String message) {
        log.debug("Memory pulse: Analyzing input for cognitive facts: {}", message);
        
        // 1. 获取当前用户及所有已知事实作为背景
        UserNode user = userRepository.findByName(userId).orElse(null);
        StringBuilder currentFactsBuilder = new StringBuilder();
        if (user != null && user.getFacts() != null) {
            user.getFacts().forEach(f -> 
                currentFactsBuilder.append(String.format("[%d] %s; ", f.getId(), f.getContent()))
            );
        }

        // 2. 调用 AI 分析新事实与冲突
        com.lingshu.ai.core.dto.MemoryUpdate report = factExtractor.analyze(message, currentFactsBuilder.toString());
        
        if (report == null) return;

        // 3. 处理过期/错误的记忆 (删除)
        if (report.getDeletedFactIds() != null && !report.getDeletedFactIds().isEmpty()) {
            for (Long id : report.getDeletedFactIds()) {
                log.info("Memory corrected: Removing fact ID {}", id);
                this.deleteFact(id); // 同时清理 Neo4j 和 pgvector
            }
        }

        // 4. 处理新提取的记忆
        if (report.getNewFacts() != null && !report.getNewFacts().isEmpty()) {
            if (user == null) {
                user = UserNode.builder()
                        .name(userId)
                        .firstEncounter(LocalDateTime.now())
                        .build();
            }

            for (String fact : report.getNewFacts()) {
                log.info("Aha! New persistent fact: {}", fact);
                
                // Save to Neo4j
                FactNode factNode = FactNode.builder()
                        .content(fact)
                        .category("Memory")
                        .observedAt(LocalDateTime.now())
                        .importance(0.8)
                        .build();
                user.addFact(factNode);

                // Save to pgvector
                dev.langchain4j.data.segment.TextSegment segment = dev.langchain4j.data.segment.TextSegment.from(fact);
                dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
            }
            
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    @Override
    public String retrieveContext(String userId, String message) {
        StringBuilder contextBuilder = new StringBuilder();

        // 1. Graph Retrieval (Neo4j)
        userRepository.findByName(userId).ifPresent(user -> {
            contextBuilder.append("Known facts from your profile: ");
            user.getFacts().forEach(f -> contextBuilder.append(f.getContent()).append("; "));
            contextBuilder.append("\n");
        });

        // 2. Semantic Retrieval (pgvector)
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(message).content();
        List<dev.langchain4j.store.embedding.EmbeddingMatch<dev.langchain4j.data.segment.TextSegment>> matches = 
                embeddingStore.findRelevant(queryEmbedding, 5, 0.6);
        
        if (!matches.isEmpty()) {
            contextBuilder.append("Related memories found: ");
            matches.forEach(match -> contextBuilder.append(match.embedded().text()).append(" (relevant); "));
        }

        return contextBuilder.toString();
    }

    @Override
    public Object getGraphData(String userId) {
        log.info("Fetching graph data for visualization...");
        java.util.Map<String, Object> graph = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> nodes = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Object>> links = new java.util.ArrayList<>();

        userRepository.findAll().forEach(user -> {
            java.util.Map<String, Object> userNode = new java.util.HashMap<>();
            userNode.put("id", "user_" + user.getName());
            userNode.put("label", user.getName());
            userNode.put("type", "User");
            nodes.add(userNode);

            user.getFacts().forEach(fact -> {
                java.util.Map<String, Object> factNode = new java.util.HashMap<>();
                factNode.put("id", "fact_" + fact.getId());
                factNode.put("label", fact.getContent());
                factNode.put("type", "Fact");
                factNode.put("importance", fact.getImportance());
                nodes.add(factNode);

                java.util.Map<String, Object> link = new java.util.HashMap<>();
                link.put("source", "user_" + user.getName());
                link.put("target", "fact_" + fact.getId());
                link.put("type", "HAS_FACT");
                links.add(link);
            });
        });

        graph.put("nodes", nodes);
        graph.put("links", links);
        return graph;
    }

    @Override
    public void deleteFact(Long factId) {
        log.info("Deleting fact node with ID: {}", factId);
        // Delete from Neo4j
        factRepository.deleteById(factId);
        
        // Potential TODO: Semantic cleanup if metadata was stored
    }
}
