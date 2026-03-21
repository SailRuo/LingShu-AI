package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.FactExtractor;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.filter.Filter;
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
        
        if (report == null) {
            log.debug("Memory pulse: No cognitive updates required for this message.");
            return;
        }

        log.debug("Cognitive report received: {} new facts, {} deletions requested", 
                report.getNewFacts() != null ? report.getNewFacts().size() : 0,
                report.getDeletedFactIds() != null ? report.getDeletedFactIds().size() : 0);

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
                // Defensive check: filter out "[]" or empty/blank facts
                if (fact == null || fact.trim().isEmpty() || fact.equals("[]")) {
                    log.debug("Memory pulse: Skipping invalid or empty fact candidate.");
                    continue;
                }
                
                log.info("Aha! New persistent fact: {}", fact);
                
                // 1. Save to Neo4j first to get the ID
                FactNode factNode = FactNode.builder()
                        .content(fact)
                        .category("Memory")
                        .observedAt(LocalDateTime.now())
                        .importance(0.8)
                        .build();
                
                // We need to save the user to get the relationship persisted and IDs generated
                user.addFact(factNode);
                user = userRepository.save(user);
                
                // Find the just-added fact node to get its ID
                FactNode savedFact = user.getFacts().stream()
                        .filter(f -> f.getContent().equals(fact))
                        .findFirst()
                        .orElse(factNode);

                // 2. Save to pgvector with the Neo4j ID as metadata
                java.util.Map<String, String> metadata = new java.util.HashMap<>();
                if (savedFact.getId() != null) {
                    metadata.put("fact_id", savedFact.getId().toString());
                }
                
                dev.langchain4j.data.segment.TextSegment segment = dev.langchain4j.data.segment.TextSegment.from(fact, new dev.langchain4j.data.document.Metadata(metadata));
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
            log.debug("Graph Retrieval: Found {} facts for user {}", user.getFacts() != null ? user.getFacts().size() : 0, userId);
            contextBuilder.append("Known facts from your profile: ");
            user.getFacts().forEach(f -> contextBuilder.append(f.getContent()).append("; "));
            contextBuilder.append("\n");
        });

        // 2. Semantic Retrieval (pgvector)
        log.debug("Semantic Retrieval: Querying vector store for: {}", message);
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(message).content();
        List<dev.langchain4j.store.embedding.EmbeddingMatch<dev.langchain4j.data.segment.TextSegment>> matches = 
                embeddingStore.findRelevant(queryEmbedding, 5, 0.6);
        
        if (!matches.isEmpty()) {
            log.debug("Semantic Retrieval: Found {} relevant segments", matches.size());
            contextBuilder.append("Related memories found: ");
            matches.forEach(match -> {
                log.trace("Match text: {} (score: {})", match.embedded().text(), match.score());
                contextBuilder.append(match.embedded().text()).append(" (relevant); ");
            });
        } else {
            log.debug("Semantic Retrieval: No relevant segments above threshold.");
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
        log.info("Cognitive cleanup: Removing fact node #{}", factId);
        
        // 1. Delete from Neo4j (SDN automatically handles relationship detachment)
        try {
            factRepository.deleteById(factId);
            log.debug("Neo4j node removal successful: {}", factId);
        } catch (Exception e) {
            log.error("Neo4j cleanup failed for factId {}: {}", factId, e.getMessage());
            // Optional: Fallback to manual detach delete if SDN fails
        }
        
        // 2. Delete from pgvector (memory_segments table)
        try {
            // matches metadata fact_id set in extractFacts (line 118)
            embeddingStore.removeAll(Filter.metadataKey("fact_id").isEqualTo(factId.toString()));
            log.debug("Vector store cleanup successful for factId: {}", factId);
        } catch (Exception e) {
            log.warn("Semantic cleanup skipped or failed for factId {}: Vector store may not support automated removal", factId);
        }
    }
}
