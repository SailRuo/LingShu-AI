package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.FactExtractor;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class MemoryServiceImpl implements MemoryService {

    private static final double GAIN_THRESHOLD = 0.3;
    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
        "的", "了", "是", "在", "我", "你", "他", "她", "它", "们",
        "这", "那", "有", "和", "与", "或", "但", "如果", "因为",
        "所以", "但是", "然后", "就", "都", "也", "还", "又", "很",
        "什么", "怎么", "为什么", "哪", "谁", "几", "多少", "怎样",
        "吗", "呢", "吧", "啊", "呀", "哦", "嗯", "哈", "嘿",
        "一个", "一些", "这个", "那个", "不是", "没有", "可以", "会",
        "能", "要", "想", "去", "来", "说", "看", "做", "给", "让"
    );

    private final UserRepository userRepository;
    private final ChatLanguageModel model;
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final dev.langchain4j.store.embedding.EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore;
    private final FactRepository factRepository;
    private final com.lingshu.ai.core.service.SystemLogService systemLogService;
    private FactExtractor factExtractor;

    public MemoryServiceImpl(UserRepository userRepository, 
                             ChatLanguageModel model,
                             dev.langchain4j.model.embedding.EmbeddingModel embeddingModel,
                             dev.langchain4j.store.embedding.EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore,
                             FactRepository factRepository,
                             com.lingshu.ai.core.service.SystemLogService systemLogService) {
        this.userRepository = userRepository;
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.factRepository = factRepository;
        this.systemLogService = systemLogService;
    }

    @PostConstruct
    public void init() {
        this.factExtractor = AiServices.create(FactExtractor.class, model);
    }

    @Async("taskExecutor")
    @Override
    public void extractFacts(String userId, String message) {
        log.debug("Memory pulse: Analyzing input for cognitive facts: {}", message);
        systemLogService.info("记忆脉冲: 启动认知事实分析...", "MEMORY");
        systemLogService.startTimer("fact_extraction");
        
        systemLogService.dbStart("neo4j_query", "User", "MEMORY");
        UserNode user = userRepository.findByName(userId).orElse(null);
        systemLogService.dbEnd("neo4j_query", "MEMORY");
        
        StringBuilder currentFactsBuilder = new StringBuilder();
        if (user != null && user.getFacts() != null) {
            systemLogService.debug("用户已有 " + user.getFacts().size() + " 条记忆事实", "MEMORY");
            user.getFacts().forEach(f -> 
                currentFactsBuilder.append(String.format("[%d] %s; ", f.getId(), f.getContent()))
            );
        } else {
            systemLogService.debug("用户无历史记忆，首次认知分析", "MEMORY");
        }

        systemLogService.info("正在调用 LLM 进行事实提取与冲突检测...", "FACT");
        systemLogService.llmStart("fact-extractor", "ollama", "FACT");
        
        com.lingshu.ai.core.dto.MemoryUpdate report;
        try {
            report = factExtractor.analyze(message, currentFactsBuilder.toString());
            systemLogService.llmEnd(0, "FACT");
        } catch (Exception e) {
            systemLogService.llmError(e.getMessage(), "FACT");
            return;
        }
        
        if (report == null) {
            log.debug("Memory pulse: No cognitive updates required for this message.");
            systemLogService.info("没有检测到新的认知更新。", "FACT");
            return;
        }

        log.debug("Cognitive report received: {} new facts, {} deletions requested", 
                report.getNewFacts() != null ? report.getNewFacts().size() : 0,
                report.getDeletedFactIds() != null ? report.getDeletedFactIds().size() : 0);
        
        systemLogService.info(String.format("事实提取分析完成: 新增 %d, 删除 %d", 
                report.getNewFacts() != null ? report.getNewFacts().size() : 0,
                report.getDeletedFactIds() != null ? report.getDeletedFactIds().size() : 0), "FACT");

        if (report.getDeletedFactIds() != null && !report.getDeletedFactIds().isEmpty()) {
            for (Long id : report.getDeletedFactIds()) {
                log.info("Memory corrected: Removing fact ID {}", id);
                systemLogService.info("记忆修正: 移除过时事实 ID " + id, "MEMORY");
                this.deleteFact(id);
            }
        }

        if (report.getNewFacts() != null && !report.getNewFacts().isEmpty()) {
            if (user == null) {
                systemLogService.info("创建新用户节点: " + userId, "MEMORY");
                user = UserNode.builder()
                        .name(userId)
                        .firstEncounter(LocalDateTime.now())
                        .build();
            }

            for (String fact : report.getNewFacts()) {
                if (fact == null || fact.trim().isEmpty() || fact.equals("[]")) {
                    log.debug("Memory pulse: Skipping invalid or empty fact candidate.");
                    continue;
                }
                
                log.info("Aha! New persistent fact: {}", fact);
                systemLogService.info("持久化新事实: " + (fact.length() > 30 ? fact.substring(0, 30) + "..." : fact), "MEMORY");
                
                FactNode factNode = FactNode.builder()
                        .content(fact)
                        .category("Memory")
                        .observedAt(LocalDateTime.now())
                        .importance(0.8)
                        .build();
                
                systemLogService.dbStart("neo4j_save", "FactNode", "MEMORY");
                user.addFact(factNode);
                user = userRepository.save(user);
                systemLogService.dbEnd("neo4j_save", "MEMORY");
                
                FactNode savedFact = user.getFacts().stream()
                        .filter(f -> f.getContent().equals(fact))
                        .findFirst()
                        .orElse(factNode);

                java.util.Map<String, String> metadata = new java.util.HashMap<>();
                if (savedFact.getId() != null) {
                    metadata.put("fact_id", savedFact.getId().toString());
                }
                
                dev.langchain4j.data.segment.TextSegment segment = dev.langchain4j.data.segment.TextSegment.from(fact, new dev.langchain4j.data.document.Metadata(metadata));
                
                systemLogService.embeddingStart(fact.length(), "MEMORY");
                try {
                    dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(segment).content();
                    embeddingStore.add(embedding, segment);
                    systemLogService.embeddingEnd("MEMORY");
                } catch (Exception e) {
                    systemLogService.error("Embedding向量化失败: " + e.getMessage(), "MEMORY");
                }
            }
            
            user.setLastSeen(LocalDateTime.now());
            systemLogService.dbStart("neo4j_update", "User.lastSeen", "MEMORY");
            userRepository.save(user);
            systemLogService.dbEnd("neo4j_update", "MEMORY");
        }
        
        systemLogService.endTimer("fact_extraction", "记忆脉冲处理完成", "MEMORY");
    }

    @Override
    public String retrieveContext(String userId, String message) {
        StringBuilder contextBuilder = new StringBuilder();

        systemLogService.dbStart("neo4j_query", "User.facts", "MEMORY");
        userRepository.findByName(userId).ifPresent(user -> {
            log.debug("Graph Retrieval: Found {} facts for user {}", user.getFacts() != null ? user.getFacts().size() : 0, userId);
            int factCount = user.getFacts() != null ? user.getFacts().size() : 0;
            systemLogService.debug("图谱检索: 用户 " + userId + " 有 " + factCount + " 条事实", "MEMORY");
            contextBuilder.append("Known facts from your profile: ");
            user.getFacts().forEach(f -> contextBuilder.append(f.getContent()).append("; "));
            contextBuilder.append("\n");
        });
        systemLogService.dbEnd("neo4j_query", "MEMORY");

        if (!needsSemanticRetrieval(message)) {
            return contextBuilder.toString();
        }

        log.debug("Semantic Retrieval: Querying vector store for: {}", message);
        systemLogService.embeddingStart(message.length(), "MEMORY");
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(message).content();
        systemLogService.embeddingEnd("MEMORY");
        
        systemLogService.dbStart("pgvector_query", "embeddingStore", "MEMORY");
        @SuppressWarnings("deprecation")
        List<dev.langchain4j.store.embedding.EmbeddingMatch<dev.langchain4j.data.segment.TextSegment>> matches = 
                embeddingStore.findRelevant(queryEmbedding, 5, 0.6);
        systemLogService.dbEnd("pgvector_query", "MEMORY");
        
        if (!matches.isEmpty()) {
            log.debug("Semantic Retrieval: Found {} relevant segments", matches.size());
            systemLogService.info("语义检索完成，匹配到 " + matches.size() + " 个相关记忆片段:", "MEMORY");
            contextBuilder.append("Related memories found: ");
            for (int i = 0; i < matches.size(); i++) {
                var match = matches.get(i);
                String matchText = match.embedded().text();
                double score = match.score();
                log.trace("Match text: {} (score: {})", matchText, score);
                contextBuilder.append(matchText).append(" (relevant); ");
                String displayText = matchText.length() > 40 ? matchText.substring(0, 40) + "..." : matchText;
                systemLogService.info(String.format("  [%d] 相似度: %.3f | 内容: %s", i + 1, score, displayText), "MEMORY");
            }
        } else {
            log.debug("Semantic Retrieval: No relevant segments above threshold.");
            systemLogService.debug("语义检索: 未找到相关度超过阈值(0.6)的内容", "MEMORY");
        }

        return contextBuilder.toString();
    }

    private boolean needsSemanticRetrieval(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        List<String> entities = extractEntities(message);
        if (entities.isEmpty()) {
            systemLogService.debug("GAM-RAG: 未提取到实体，增益=0，跳过语义检索", "MEMORY");
            return false;
        }
        
        systemLogService.dbStart("neo4j_query", "Fact.activation", "MEMORY");
        List<FactNode> activatedFacts = factRepository.findFactsByKeywords(entities);
        systemLogService.dbEnd("neo4j_query", "MEMORY");
        
        if (activatedFacts.isEmpty()) {
            systemLogService.debug("GAM-RAG: 实体未激活任何记忆路径，增益=0，跳过语义检索", "MEMORY");
            return false;
        }
        
        double gain = calculateGain(entities, activatedFacts);
        systemLogService.info(String.format("GAM-RAG: 激活 %d 个实体，命中 %d 条事实，增益=%.2f", 
            entities.size(), activatedFacts.size(), gain), "MEMORY");
        
        return gain >= GAIN_THRESHOLD;
    }
    
    private List<String> extractEntities(String message) {
        List<String> entities = new java.util.ArrayList<>();
        String[] words = message.replaceAll("[\\p{Punct}\\s+]", " ").split("\\s+");
        
        for (String word : words) {
            if (word.length() >= 2 && !STOP_WORDS.contains(word.toLowerCase())) {
                entities.add(word);
            }
        }
        
        return entities.stream().distinct().limit(10).collect(java.util.stream.Collectors.toList());
    }
    
    private double calculateGain(List<String> entities, List<FactNode> activatedFacts) {
        if (entities.isEmpty() || activatedFacts.isEmpty()) {
            return 0.0;
        }
        
        int totalMatches = 0;
        double totalImportance = 0.0;
        
        for (FactNode fact : activatedFacts) {
            String content = fact.getContent().toLowerCase();
            for (String entity : entities) {
                if (content.contains(entity.toLowerCase())) {
                    totalMatches++;
                    totalImportance += fact.getImportance();
                    break;
                }
            }
        }
        
        double entityRatio = (double) totalMatches / entities.size();
        double avgImportance = totalImportance / activatedFacts.size();
        
        return entityRatio * 0.6 + avgImportance * 0.4;
    }

    @Override
    public Object getGraphData(String userId) {
        log.info("Fetching graph data for visualization...");
        systemLogService.info("获取图谱可视化数据...", "MEMORY");
        systemLogService.startTimer("graph_data");
        
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
        
        systemLogService.endTimer("graph_data", "图谱数据获取完成", "MEMORY");
        systemLogService.info("图谱包含 " + nodes.size() + " 个节点, " + links.size() + " 条边", "MEMORY");
        
        return graph;
    }

    @Override
    public void deleteFact(Long factId) {
        log.info("Cognitive cleanup: Removing fact node #{}", factId);
        systemLogService.info("认知清理: 删除事实节点 #" + factId, "MEMORY");
        
        systemLogService.dbStart("neo4j_delete", "FactNode#" + factId, "MEMORY");
        try {
            factRepository.deleteById(factId);
            log.debug("Neo4j node removal successful: {}", factId);
            systemLogService.dbEnd("neo4j_delete", "MEMORY");
        } catch (Exception e) {
            log.error("Neo4j cleanup failed for factId {}: {}", factId, e.getMessage());
            systemLogService.error("Neo4j删除失败: " + e.getMessage(), "MEMORY");
        }
        
        systemLogService.dbStart("pgvector_delete", "fact_id=" + factId, "MEMORY");
        try {
            embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("fact_id").isEqualTo(factId.toString()));
            log.debug("Vector store cleanup successful for factId: {}", factId);
            systemLogService.dbEnd("pgvector_delete", "MEMORY");
        } catch (Exception e) {
            log.warn("Semantic cleanup skipped or failed for factId {}: Vector store may not support automated removal", factId);
            systemLogService.warn("向量库清理失败: " + e.getMessage(), "MEMORY");
        }
    }
}
