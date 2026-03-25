package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.dto.FactSemanticClassification;
import com.lingshu.ai.core.service.FactExtractor;
import com.lingshu.ai.core.service.FactSemanticClassifier;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class MemoryServiceImpl implements MemoryService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MemoryServiceImpl.class);

    private static final double GAIN_THRESHOLD = 0.3;
    private static final Set<String> TOPIC_KEYS = Set.of("interest", "growth", "goal", "emotion", "relationship", "event", "timeline", "memory");
    private static final Set<String> GENERIC_TOPIC_KEYS = Set.of("memory", "timeline");
    private static final Set<String> SUB_TYPES = Set.of("Preference", "EmotionState", "Person", "Project", "Goal", "Event", "TimeAnchor", "Memory");
    private static final Set<String> GENERIC_SUB_TYPES = Set.of("Memory", "TimeAnchor");
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
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final dev.langchain4j.store.embedding.EmbeddingStore<TextSegment> embeddingStore;
    private final FactRepository factRepository;
    private final com.lingshu.ai.core.service.SystemLogService systemLogService;
    private final ChatModel chatLanguageModel;
    private final com.lingshu.ai.core.service.SettingService settingService;
    private final ObjectMapper objectMapper;
    private FactExtractor factExtractor;
    private FactSemanticClassifier factSemanticClassifier;
    private volatile Map<String, Object> lastMaintenanceSummary = new LinkedHashMap<>();

    public MemoryServiceImpl(UserRepository userRepository, 
                             dev.langchain4j.model.embedding.EmbeddingModel embeddingModel,
                             dev.langchain4j.store.embedding.EmbeddingStore<TextSegment> embeddingStore,
                             FactRepository factRepository,
                             com.lingshu.ai.core.service.SystemLogService systemLogService,
                             com.lingshu.ai.core.service.SettingService settingService,
                             ChatModel chatLanguageModel,
                             ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.factRepository = factRepository;
        this.systemLogService = systemLogService;
        this.settingService = settingService;
        this.chatLanguageModel = chatLanguageModel;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initAiServices() {
        this.factExtractor = AiServices.builder(FactExtractor.class)
                .chatModel(chatLanguageModel)
                .build();
        this.factSemanticClassifier = AiServices.builder(FactSemanticClassifier.class)
                .chatModel(chatLanguageModel)
                .build();
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
        
        com.lingshu.ai.core.dto.MemoryUpdate report = null;
        try {
            String rawJson = factExtractor.analyze(message, currentFactsBuilder.toString());
            // 清理可能存在的 markdown 标签（防御性处理）
            rawJson = rawJson.replaceAll("(?s)```(json)?", "").trim();
            report = objectMapper.readValue(rawJson, com.lingshu.ai.core.dto.MemoryUpdate.class);
            systemLogService.llmEnd(0, "FACT");
        } catch (Exception e) {
            log.error("Failed to parse cognitive report: {}", e.getMessage());
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
                String factContent = "Unknown";
                if (user != null && user.getFacts() != null) {
                    factContent = user.getFacts().stream()
                            .filter(f -> f.getId() != null && f.getId().equals(id))
                            .map(f -> f.getContent())
                            .findFirst()
                            .orElse("Unknown");
                    
                    // 必须从当前内存对象中移除，否则 save(user) 时可能重新创建关系
                    user.getFacts().removeIf(f -> f.getId() != null && f.getId().equals(id));
                }
                log.info("Memory corrected: Removing outdated fact [{}] (ID: {})", factContent, id);
                systemLogService.info("记忆修正: 移除过时事实 [" + (factContent.length() > 30 ? factContent.substring(0, 30) + "..." : factContent) + "]", "MEMORY");
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

                log.info("Aha! New persistent fact candidate: {}", fact);
                FactWriteResult writeResult = persistFactCandidate(user, fact.trim());
                user = writeResult.user;

                if (!writeResult.createdNewFact || writeResult.savedFact == null) {
                    continue;
                }

                java.util.Map<String, String> metadata = new java.util.HashMap<>();
                if (writeResult.savedFact.getId() != null) {
                    metadata.put("fact_id", writeResult.savedFact.getId().toString());
                }

                TextSegment segment = TextSegment.from(writeResult.savedFact.getContent(), new dev.langchain4j.data.document.Metadata(metadata));

                systemLogService.embeddingStart(writeResult.savedFact.getContent().length(), "MEMORY");
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
        userRepository.findByName(userId).ifPresentOrElse(user -> {
            log.info("Graph Retrieval: Found {} facts for user {}", user.getFacts() != null ? user.getFacts().size() : 0, userId);
            int factCount = user.getFacts() != null ? user.getFacts().size() : 0;
            systemLogService.info("图谱检索: 找到用户 " + userId + " 的 " + factCount + " 条既定事实", "MEMORY");
            contextBuilder.append("关于用户的已知事实：\n");
            if (user.getFacts() != null) {
                user.getFacts().forEach(f -> {
                    log.debug("  Fact: {}", f.getContent());
                    contextBuilder.append("- ").append(f.getContent()).append("\n");
                });
            }
        }, () -> {
            log.warn("Graph Retrieval: User {} not found in Neo4j", userId);
            systemLogService.warn("图谱检索: 未在 Neo4j 中找到用户 " + userId + " 的节点", "MEMORY");
        });
        systemLogService.dbEnd("neo4j_query", "MEMORY");

        if (!needsSemanticRetrieval(message)) {
            // 针对“我是谁”、“我的名字”等意图，即使没有提取到复杂实体，也尝试返回基本事实
            if (message.contains("我是谁") || message.contains("我的名字") || message.contains("叫什么")) {
                log.debug("Memory pulse: Detected identity query, returning basic facts anyway.");
                return contextBuilder.toString();
            }
            return contextBuilder.toString();
        }

        log.debug("Semantic Retrieval: Querying vector store for: {}", message);
        systemLogService.embeddingStart(message.length(), "MEMORY");
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(message).content();
        systemLogService.embeddingEnd("MEMORY");
        
        systemLogService.dbStart("pgvector_query", "embeddingStore", "MEMORY");
        
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .minScore(0.6)
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        
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
            // 放宽实体长度限制，支持单字识别（如 “我”、“书” 等核心意向）
            if (word.length() >= 1 && !STOP_WORDS.contains(word.toLowerCase())) {
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

        long startTime = System.currentTimeMillis();

        Map<String, Object> graph = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        List<FactContext> factContexts = new ArrayList<>();

        List<UserNode> users = new ArrayList<>();
        userRepository.findAll().forEach(users::add);

        Map<String, TopicAggregate> topics = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        int factCount = 0;
        int activeFactCount = 0;

        for (UserNode user : users) {
            String userNodeId = "user_" + user.getName();
            nodes.add(buildUserNode(user, userNodeId, now));

            Set<FactNode> factSet = user.getFacts() == null ? Set.of() : user.getFacts();
            for (FactNode fact : factSet) {
                if ("archived".equals(fact.getStatus())) {
                    continue;
                }
                factCount++;
                double activityScore = calculateActivityScore(fact.getObservedAt(), now);
                if (activityScore >= 0.7) {
                    activeFactCount++;
                }

                String topicKey = deriveTopicKey(fact);
                TopicAggregate topic = topics.computeIfAbsent(topicKey, key -> new TopicAggregate(key, displayTopicName(key)));
                topic.include(fact, activityScore);
                topic.userIds.add(userNodeId);
                factContexts.add(new FactContext(userNodeId, topicKey, fact));
            }
        }

        for (TopicAggregate topic : topics.values()) {
            nodes.add(buildTopicNode(topic, now));
            for (String userNodeId : topic.userIds) {
                links.add(buildLink(userNodeId, topic.id, "HAS_TOPIC", topic.importance, topic.lastActivatedAt));
            }
        }

        for (UserNode user : users) {
            Set<FactNode> factSet = user.getFacts() == null ? Set.of() : user.getFacts();
            for (FactNode fact : factSet) {
                if ("archived".equals(fact.getStatus())) {
                    continue;
                }
                String topicKey = deriveTopicKey(fact);
                TopicAggregate topic = topics.get(topicKey);
                nodes.add(buildFactNode(fact, topicKey, topic != null ? topic.orbitLevel : 2, now));
                if (topic != null) {
                    links.add(buildLink(topic.id, "fact_" + fact.getId(), "BELONGS_TO_TOPIC", fact.getImportance(), fact.getObservedAt()));
                } else {
                    links.add(buildLink("user_" + user.getName(), "fact_" + fact.getId(), "HAS_FACT", fact.getImportance(), fact.getObservedAt()));
                }
            }
        }

        links.addAll(loadPersistedFactLinks(factContexts));

        long latency = Math.max(1, System.currentTimeMillis() - startTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("nodes", nodes.size());
        stats.put("edges", links.size());
        stats.put("topics", topics.size());
        stats.put("activeFacts", activeFactCount);
        stats.put("latency", latency);
        stats.put("density", calculateDensity(nodes.size(), links.size()));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generatedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        meta.put("version", "galaxy-v1");
        meta.put("source", "/api/memory/graph");

        graph.put("nodes", nodes);
        graph.put("links", links);
        graph.put("stats", stats);
        graph.put("meta", meta);

        systemLogService.endTimer("graph_data", "图谱数据获取完成", "MEMORY");
        systemLogService.info("图谱包含 " + nodes.size() + " 个节点, " + links.size() + " 条边", "MEMORY");

        return graph;
    }

    private Map<String, Object> buildUserNode(UserNode user, String nodeId, LocalDateTime now) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", nodeId);
        node.put("label", user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getName());
        node.put("shortLabel", user.getName());
        node.put("type", "User");
        node.put("subType", "Core");
        node.put("importance", 1.0);
        node.put("confidence", 1.0);
        node.put("activityScore", calculateActivityScore(user.getLastSeen(), now));
        node.put("cluster", "core");
        node.put("orbitLevel", 0);
        node.put("createdAt", formatDateTime(user.getFirstEncounter()));
        node.put("lastActivatedAt", formatDateTime(user.getLastSeen()));
        node.put("status", "core");
        return node;
    }

    private Map<String, Object> buildTopicNode(TopicAggregate topic, LocalDateTime now) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", topic.id);
        node.put("label", topic.label);
        node.put("shortLabel", topic.label);
        node.put("type", "Topic");
        node.put("subType", "Derived");
        node.put("importance", round(topic.importance));
        node.put("confidence", round(Math.min(0.98, 0.62 + topic.factCount * 0.06)));
        node.put("activityScore", round(topic.activityScore));
        node.put("cluster", topic.key);
        node.put("orbitLevel", topic.orbitLevel);
        node.put("createdAt", formatDateTime(topic.createdAt));
        node.put("lastActivatedAt", formatDateTime(topic.lastActivatedAt != null ? topic.lastActivatedAt : now));
        node.put("status", topic.activityScore >= 0.7 ? "active" : "stable");
        node.put("factCount", topic.factCount);
        return node;
    }

    private Map<String, Object> buildFactNode(FactNode fact, String topicKey, int orbitLevel, LocalDateTime now) {
        double activityScore = fact.getActivityScore() > 0 ? fact.getActivityScore() : calculateActivityScore(fact.getLastActivatedAt() != null ? fact.getLastActivatedAt() : fact.getObservedAt(), now);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "fact_" + fact.getId());
        node.put("label", fact.getContent());
        node.put("shortLabel", shortenLabel(fact.getContent(), 18));
        node.put("type", "Fact");
        node.put("subType", fact.getSubType() != null && !fact.getSubType().isBlank() ? fact.getSubType() : inferFactSubType(fact));
        node.put("importance", round(fact.getImportance()));
        node.put("confidence", round(fact.getConfidence() > 0 ? fact.getConfidence() : Math.min(0.95, Math.max(0.55, fact.getImportance() + 0.08))));
        node.put("activityScore", round(activityScore));
        node.put("cluster", fact.getClusterKey() != null && !fact.getClusterKey().isBlank() ? fact.getClusterKey() : topicKey);
        node.put("orbitLevel", orbitLevel > 0 ? orbitLevel : calculateOrbitLevel(fact.getImportance(), activityScore));
        node.put("createdAt", formatDateTime(fact.getObservedAt()));
        node.put("lastActivatedAt", formatDateTime(fact.getLastActivatedAt() != null ? fact.getLastActivatedAt() : fact.getObservedAt()));
        node.put("status", fact.getStatus() != null && !fact.getStatus().isBlank() ? fact.getStatus() : (activityScore >= 0.75 ? "active" : activityScore >= 0.38 ? "stable" : "cool"));
        node.put("version", fact.getVersion());
        node.put("supersedesFactId", fact.getSupersedesFactId());
        node.put("contradictsFactId", fact.getContradictsFactId());
        return node;
    }

    private Map<String, Object> buildLink(String source, String target, String type, double weight, LocalDateTime lastActivatedAt) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("source", source);
        link.put("target", target);
        link.put("type", type);
        link.put("weight", round(Math.max(0.18, weight)));
        link.put("lastActivatedAt", formatDateTime(lastActivatedAt));
        return link;
    }

    private List<Map<String, Object>> buildInferredFactLinks(List<FactContext> factContexts) {
        List<Map<String, Object>> links = new ArrayList<>();
        Map<String, List<FactContext>> grouped = factContexts.stream()
                .collect(Collectors.groupingBy(ctx -> ctx.userNodeId + "::" + ctx.topicKey, LinkedHashMap::new, Collectors.toList()));

        for (List<FactContext> group : grouped.values()) {
            group.sort(Comparator.comparing(ctx -> ctx.fact.getObservedAt(), Comparator.nullsLast(Comparator.naturalOrder())));
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < Math.min(group.size(), i + 4); j++) {
                    FactContext older = group.get(i);
                    FactContext newer = group.get(j);
                    RelationshipGuess guess = guessFactRelationship(older.fact, newer.fact);
                    if (guess == null) {
                        continue;
                    }
                    String source = guess.type.equals("SUPERSEDES") ? "fact_" + newer.fact.getId() : "fact_" + older.fact.getId();
                    String target = guess.type.equals("SUPERSEDES") ? "fact_" + older.fact.getId() : "fact_" + newer.fact.getId();
                    LocalDateTime activatedAt = newer.fact.getObservedAt() != null ? newer.fact.getObservedAt() : older.fact.getObservedAt();
                    links.add(buildLink(source, target, guess.type, guess.weight, activatedAt));
                }
            }
        }

        return links;
    }

    private void synchronizeFactRelations(List<FactContext> factContexts) {
        List<Long> factIds = factContexts.stream()
                .map(ctx -> ctx.fact.getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (factIds.size() < 2) {
            return;
        }

        List<Map<String, Object>> inferred = buildInferredFactLinks(factContexts);
        factRepository.deleteFactRelationsByFactIds(factIds);

        List<Map<String, Object>> related = inferred.stream().filter(link -> "RELATED_TO".equals(link.get("type"))).collect(Collectors.toList());
        List<Map<String, Object>> supersedes = inferred.stream().filter(link -> "SUPERSEDES".equals(link.get("type"))).collect(Collectors.toList());
        List<Map<String, Object>> contradicts = inferred.stream().filter(link -> "CONTRADICTS".equals(link.get("type"))).collect(Collectors.toList());

        if (!related.isEmpty()) {
            factRepository.saveRelatedRelations(prepareRelationRows(related));
        }
        if (!supersedes.isEmpty()) {
            factRepository.saveSupersedesRelations(prepareRelationRows(supersedes));
        }
        if (!contradicts.isEmpty()) {
            factRepository.saveContradictsRelations(prepareRelationRows(contradicts));
        }
    }

    private List<Map<String, Object>> loadPersistedFactLinks(List<FactContext> factContexts) {
        List<Long> factIds = factContexts.stream()
                .map(ctx -> ctx.fact.getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (factIds.isEmpty()) {
            return List.of();
        }

        return factRepository.findFactRelationsByFactIds(factIds).stream()
                .map(row -> {
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("source", "fact_" + castLong(row.get("sourceId")));
                    link.put("target", "fact_" + castLong(row.get("targetId")));
                    link.put("type", String.valueOf(row.get("type")));
                    link.put("weight", row.get("weight"));
                    link.put("lastActivatedAt", row.get("lastActivatedAt"));
                    return link;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> prepareRelationRows(List<Map<String, Object>> links) {
        return links.stream().map(link -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceId", castLong(String.valueOf(link.get("source")).replace("fact_", "")));
            row.put("targetId", castLong(String.valueOf(link.get("target")).replace("fact_", "")));
            row.put("weight", link.get("weight"));
            row.put("lastActivatedAt", link.get("lastActivatedAt"));
            return row;
        }).collect(Collectors.toList());
    }

    private void synchronizeAllFactRelations() {
        List<FactContext> allFactContexts = new ArrayList<>();
        userRepository.findAll().forEach(user -> allFactContexts.addAll(buildFactContextsForUser(user)));
        synchronizeFactRelations(allFactContexts);
    }

    private Long castLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private RelationshipGuess guessFactRelationship(FactNode left, FactNode right) {
        String leftContent = left.getContent() == null ? "" : left.getContent().trim();
        String rightContent = right.getContent() == null ? "" : right.getContent().trim();
        if (leftContent.isBlank() || rightContent.isBlank()) {
            return null;
        }

        String normalizedLeft = normalizeSemanticText(leftContent);
        String normalizedRight = normalizeSemanticText(rightContent);
        double similarity = lexicalSimilarity(normalizedLeft, normalizedRight);
        boolean contradiction = isContradictory(leftContent, rightContent);

        if (contradiction && similarity >= 0.22) {
            return new RelationshipGuess("CONTRADICTS", 0.82);
        }
        if (similarity >= 0.68) {
            return new RelationshipGuess("SUPERSEDES", 0.88);
        }
        if (similarity >= 0.38) {
            return new RelationshipGuess("RELATED_TO", 0.52);
        }
        return null;
    }

    private String normalizeSemanticText(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double lexicalSimilarity(String left, String right) {
        Set<String> leftTokens = tokenizeForSimilarity(left);
        Set<String> rightTokens = tokenizeForSimilarity(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return left.equals(right) ? 1.0 : 0.0;
        }

        Set<String> intersection = new java.util.HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new java.util.HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> tokenizeForSimilarity(String text) {
        Set<String> tokens = new java.util.LinkedHashSet<>();
        for (String token : text.split("\\s+")) {
            String cleaned = token.trim();
            if (cleaned.length() >= 2 && !STOP_WORDS.contains(cleaned)) {
                tokens.add(cleaned);
            }
        }
        if (tokens.isEmpty() && text.length() >= 2) {
            for (int i = 0; i < text.length() - 1; i++) {
                tokens.add(text.substring(i, i + 2));
            }
        }
        return tokens;
    }

    private boolean isContradictory(String left, String right) {
        boolean leftPositive = containsAny(left, "喜欢", "想要", "会", "正在", "有", "是");
        boolean rightNegative = containsAny(right, "不喜欢", "不想", "不会", "没有", "不是", "停止");
        boolean rightPositive = containsAny(right, "喜欢", "想要", "会", "正在", "有", "是");
        boolean leftNegative = containsAny(left, "不喜欢", "不想", "不会", "没有", "不是", "停止");
        return (leftPositive && rightNegative) || (rightPositive && leftNegative);
    }

    private FactWriteResult persistFactCandidate(UserNode user, String factText) {
        LocalDateTime now = LocalDateTime.now();
        String normalized = normalizeSemanticText(factText);
        SemanticProfile semanticProfile = classifyFactSemantics(factText, user);
        String clusterKey = semanticProfile.topicKey;
        String subType = semanticProfile.subType;

        FactNode exactMatch = findExactFact(user, normalized);
        if (exactMatch != null) {
            refreshExistingFact(exactMatch, now);
            factRepository.save(exactMatch);
            synchronizeFactRelations(buildFactContextsForUser(user));
            systemLogService.debug("事实命中精确去重，刷新活跃度: " + shortenLabel(factText, 24), "MEMORY");
            return new FactWriteResult(user, exactMatch, false);
        }

        FactNode bestSimilar = null;
        double bestSimilarity = 0.0;
        boolean contradiction = false;
        for (FactNode existing : user.getFacts()) {
            double similarity = lexicalSimilarity(normalized, normalizeSemanticText(existing.getContent() == null ? "" : existing.getContent()));
            boolean candidateContradiction = isContradictory(existing.getContent() == null ? "" : existing.getContent(), factText);
            if (candidateContradiction && similarity >= 0.22) {
                bestSimilar = existing;
                bestSimilarity = similarity;
                contradiction = true;
                break;
            }
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestSimilar = existing;
            }
        }

        if (bestSimilar != null && contradiction) {
            bestSimilar.setStatus("conflicted");
            bestSimilar.setLastActivatedAt(now);
            bestSimilar.setActivityScore(calculateActivityScore(now, now));
            factRepository.save(bestSimilar);
            FactNode factNode = buildNewFactNode(factText, normalized, clusterKey, subType, semanticProfile.confidence, now);
            factNode.setContradictsFactId(bestSimilar.getId());
            factNode.setStatus("conflicted");
            systemLogService.info("检测到冲突记忆，创建冲突版本: " + shortenLabel(factText, 24), "MEMORY");
            return saveNewFact(user, factNode);
        }

        if (bestSimilar != null && bestSimilarity >= 0.68) {
            bestSimilar.setStatus("superseded");
            bestSimilar.setLastActivatedAt(now);
            bestSimilar.setActivityScore(calculateActivityScore(now, now));
            factRepository.save(bestSimilar);
            FactNode factNode = buildNewFactNode(factText, normalized, clusterKey, subType, semanticProfile.confidence, now);
            factNode.setSupersedesFactId(bestSimilar.getId());
            factNode.setVersion((bestSimilar.getVersion() == null ? 1 : bestSimilar.getVersion()) + 1);
            factNode.setStatus("active");
            systemLogService.info("检测到相似记忆，创建替代版本: " + shortenLabel(factText, 24), "MEMORY");
            return saveNewFact(user, factNode);
        }

        FactNode factNode = buildNewFactNode(factText, normalized, clusterKey, subType, semanticProfile.confidence, now);
        return saveNewFact(user, factNode);
    }

    private FactWriteResult saveNewFact(UserNode user, FactNode factNode) {
        systemLogService.info("持久化新事实: " + shortenLabel(factNode.getContent(), 30), "MEMORY");
        systemLogService.dbStart("neo4j_save", "FactNode", "MEMORY");
        user.addFact(factNode);
        UserNode savedUser = userRepository.save(user);
        systemLogService.dbEnd("neo4j_save", "MEMORY");
        synchronizeFactRelations(buildFactContextsForUser(savedUser));

        FactNode savedFact = savedUser.getFacts().stream()
                .filter(f -> f.getContent().equals(factNode.getContent()))
                .max(Comparator.comparing(FactNode::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(factNode);
        return new FactWriteResult(savedUser, savedFact, true);
    }

    private FactNode buildNewFactNode(String factText, String normalized, String clusterKey, String subType, double semanticConfidence, LocalDateTime now) {
        return FactNode.builder()
                .content(factText)
                .category(clusterKey)
                .normalizedContent(normalized)
                .subType(subType)
                .clusterKey(clusterKey)
                .observedAt(now)
                .lastActivatedAt(now)
                .importance(0.8)
                .confidence(round(Math.max(0.72, semanticConfidence)))
                .activityScore(calculateActivityScore(now, now))
                .status("active")
                .decayRate(0.015)
                .ttlDays(180)
                .version(1)
                .build();
    }

    private FactNode findExactFact(UserNode user, String normalizedContent) {
        if (user.getFacts() == null) {
            return null;
        }
        return user.getFacts().stream()
                .filter(f -> normalizedContent.equals(normalizedFactContent(f)))
                .findFirst()
                .orElse(null);
    }

    private String normalizedFactContent(FactNode fact) {
        if (fact.getNormalizedContent() != null && !fact.getNormalizedContent().isBlank()) {
            return fact.getNormalizedContent();
        }
        return normalizeSemanticText(fact.getContent() == null ? "" : fact.getContent());
    }

    private void refreshExistingFact(FactNode fact, LocalDateTime now) {
        fact.setNormalizedContent(normalizedFactContent(fact));
        fact.setLastActivatedAt(now);
        fact.setActivityScore(calculateActivityScore(now, now));
        fact.setImportance(round(Math.min(0.98, Math.max(fact.getImportance(), 0.82))));
        fact.setConfidence(round(Math.min(0.98, Math.max(fact.getConfidence(), 0.88))));
        if (fact.getStatus() == null || fact.getStatus().isBlank() || "cool".equals(fact.getStatus())) {
            fact.setStatus("active");
        }
        if (fact.getVersion() == null || fact.getVersion() < 1) {
            fact.setVersion(1);
        }
    }

    private boolean shouldArchiveFact(FactNode fact, LocalDateTime now) {
        if (fact.getObservedAt() == null) {
            return false;
        }
        Integer ttlDays = fact.getTtlDays();
        if (ttlDays == null || ttlDays <= 0) {
            return false;
        }
        long days = Math.abs(Duration.between(fact.getObservedAt(), now).toDays());
        return days > ttlDays && (fact.getActivityScore() <= 0.2 || "superseded".equals(fact.getStatus()));
    }

    private List<FactContext> buildFactContextsForUser(UserNode user) {
        if (user == null || user.getFacts() == null) {
            return List.of();
        }
        String userNodeId = "user_" + user.getName();
        return user.getFacts().stream()
                .filter(fact -> !"archived".equals(fact.getStatus()))
                .map(fact -> new FactContext(userNodeId, deriveTopicKey(fact), fact))
                .collect(Collectors.toList());
    }

    private String deriveTopicKey(FactNode fact) {
        if (fact.getClusterKey() != null && !fact.getClusterKey().isBlank()) {
            return normalizeTopicKey(fact.getClusterKey());
        }
        if (fact.getCategory() != null && !fact.getCategory().isBlank()) {
            String categoryKey = normalizeTopicKey(fact.getCategory());
            if (TOPIC_KEYS.contains(categoryKey)) {
                return categoryKey;
            }
        }

        String content = fact.getContent() == null ? "" : fact.getContent().toLowerCase(Locale.ROOT);
        if (containsAny(content, "喜欢", "爱", "咖啡", "音乐", "电影", "偏好", "兴趣")) {
            return "interest";
        }
        if (containsAny(content, "工作", "项目", "开发", "学习", "考试", "职业", "任务")) {
            return "growth";
        }
        if (containsAny(content, "目标", "计划", "准备", "打算", "希望", "想要")) {
            return "goal";
        }
        if (containsAny(content, "焦虑", "开心", "难过", "情绪", "压力", "睡眠", "疲惫")) {
            return "emotion";
        }
        if (containsAny(content, "朋友", "家人", "同事", "老师", "她", "他", "关系")) {
            return "relationship";
        }
        if (containsAny(content, "会议", "出差", "报名", "旅行", "发生", "昨天", "今晚")) {
            return "event";
        }
        if (containsAny(content, "今天", "最近", "刚刚", "计划", "行程", "安排")) {
            return "timeline";
        }
        return "memory";
    }

    private String displayTopicName(String topicKey) {
        return switch (topicKey) {
            case "interest" -> "兴趣偏好";
            case "growth" -> "成长计划";
            case "goal" -> "目标轨道";
            case "emotion" -> "情绪状态";
            case "relationship" -> "关系网络";
            case "event" -> "事件带";
            case "timeline" -> "近期事件";
            case "memory" -> "记忆归档";
            default -> topicKey.substring(0, 1).toUpperCase(Locale.ROOT) + topicKey.substring(1);
        };
    }

    private String inferFactSubType(FactNode fact) {
        if (fact.getSubType() != null && !fact.getSubType().isBlank()) {
            return normalizeSubType(fact.getSubType());
        }
        if (fact.getCategory() != null && !fact.getCategory().isBlank() && SUB_TYPES.contains(fact.getCategory())) {
            return fact.getCategory();
        }

        String content = fact.getContent() == null ? "" : fact.getContent().toLowerCase(Locale.ROOT);
        if (containsAny(content, "喜欢", "爱", "偏好", "最爱", "常喝", "习惯")) {
            return "Preference";
        }
        if (containsAny(content, "焦虑", "开心", "难过", "压力", "疲惫", "睡眠")) {
            return "EmotionState";
        }
        if (containsAny(content, "朋友", "家人", "同事", "老师", "她", "他")) {
            return "Person";
        }
        if (containsAny(content, "项目", "开发", "工作", "考试", "学习")) {
            return "Project";
        }
        if (containsAny(content, "目标", "计划", "准备", "打算", "希望")) {
            return "Goal";
        }
        if (containsAny(content, "会议", "旅行", "报名", "发生", "昨天", "今晚")) {
            return "Event";
        }
        if (containsAny(content, "今天", "最近", "刚刚", "明天", "下周")) {
            return "TimeAnchor";
        }
        return "Memory";
    }

    private String normalizeKey(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "_");
    }

    private String normalizeTopicKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "memory";
        }
        String key = normalizeKey(raw);
        return TOPIC_KEYS.contains(key) ? key : "memory";
    }

    private String normalizeSubType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Memory";
        }
        String normalized = Arrays.stream(raw.trim().split("[_\\-\\s]+"))
                .filter(token -> !token.isBlank())
                .map(token -> token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining());
        return SUB_TYPES.contains(normalized) ? normalized : "Memory";
    }

    private SemanticProfile classifyFactSemantics(String factText, UserNode user) {
        SemanticProfile historyProfile = resolveByHistory(factText, user);
        SemanticProfile ruleProfile = resolveByRules(factText);

        if (historyProfile != null && ruleProfile != null) {
            if (historyProfile.topicKey.equals(ruleProfile.topicKey) || historyProfile.subType.equals(ruleProfile.subType)) {
                return new SemanticProfile(
                        historyProfile.topicKey,
                        historyProfile.subType,
                        Math.max(historyProfile.confidence, ruleProfile.confidence),
                        "history+rule"
                );
            }
            if (historyProfile.confidence >= 0.9 && !isGenericProfile(historyProfile)) {
                return historyProfile;
            }
        }

        if (ruleProfile != null && !needsLlmClassification(factText, ruleProfile)) {
            return ruleProfile;
        }
        if (historyProfile != null && !isGenericProfile(historyProfile)) {
            return historyProfile;
        }

        SemanticProfile llmProfile = resolveByLlm(factText, user);
        if (llmProfile != null) {
            return llmProfile;
        }
        if (ruleProfile != null) {
            return ruleProfile;
        }
        if (historyProfile != null) {
            return historyProfile;
        }
        return new SemanticProfile("memory", "Memory", 0.58, "fallback");
    }

    private SemanticProfile resolveByHistory(String factText, UserNode user) {
        if (user == null || user.getFacts() == null || user.getFacts().isEmpty()) {
            return null;
        }
        String normalized = normalizeSemanticText(factText);
        FactNode bestMatch = null;
        double bestScore = 0.0;

        for (FactNode existing : user.getFacts()) {
            if (existing.getContent() == null || existing.getContent().isBlank()) {
                continue;
            }
            double score = lexicalSimilarity(normalized, normalizedFactContent(existing));
            if (score > bestScore) {
                bestScore = score;
                bestMatch = existing;
            }
        }

        if (bestMatch == null || bestScore < 0.72) {
            return null;
        }

        return new SemanticProfile(
                deriveTopicKey(bestMatch),
                inferFactSubType(bestMatch),
                round(Math.min(0.95, 0.62 + bestScore * 0.3)),
                "history"
        );
    }

    private SemanticProfile resolveByRules(String factText) {
        FactNode probe = FactNode.builder().content(factText).build();
        String topicKey = deriveTopicKey(probe);
        String subType = inferFactSubType(probe);
        double confidence = 0.66;
        if (!"memory".equals(topicKey)) {
            confidence += 0.12;
        }
        if (!"Memory".equals(subType)) {
            confidence += 0.12;
        }
        return new SemanticProfile(topicKey, subType, round(Math.min(0.9, confidence)), "rule");
    }

    private SemanticProfile resolveByLlm(String factText, UserNode user) {
        if (factSemanticClassifier == null) {
            return null;
        }
        try {
            systemLogService.llmStart("fact-semantic-classifier", "ollama", "FACT");
            FactSemanticClassification classification = factSemanticClassifier.classify(factText, buildHistorySummary(user));
            systemLogService.llmEnd(0, "FACT");
            if (classification == null) {
                return null;
            }
            return new SemanticProfile(
                    normalizeTopicKey(classification.getTopicKey()),
                    normalizeSubType(classification.getSubType()),
                    round(Math.max(0.6, Math.min(0.95, classification.getConfidence()))),
                    "llm"
            );
        } catch (Exception e) {
            systemLogService.llmError(e.getMessage(), "FACT");
            log.warn("Fact semantic classification fallback to local rules: {}", e.getMessage());
            return null;
        }
    }

    private boolean needsLlmClassification(String factText, SemanticProfile ruleProfile) {
        if (ruleProfile == null) {
            return true;
        }
        if (!isGenericProfile(ruleProfile)) {
            return false;
        }
        return factText != null && (factText.length() >= 14
                || factText.contains("但是")
                || factText.contains("不过")
                || factText.contains("因为")
                || factText.contains("最近"));
    }

    private boolean isGenericProfile(SemanticProfile profile) {
        return GENERIC_TOPIC_KEYS.contains(profile.topicKey) || GENERIC_SUB_TYPES.contains(profile.subType);
    }

    private String buildHistorySummary(UserNode user) {
        if (user == null || user.getFacts() == null || user.getFacts().isEmpty()) {
            return "暂无历史记忆。";
        }
        return user.getFacts().stream()
                .filter(fact -> fact.getContent() != null && !fact.getContent().isBlank())
                .sorted(Comparator.comparing(FactNode::getLastActivatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(fact -> String.format("[%s/%s] %s",
                        deriveTopicKey(fact),
                        inferFactSubType(fact),
                        shortenLabel(fact.getContent(), 28)))
                .collect(Collectors.joining(" | "));
    }

    private boolean containsAny(String content, String... terms) {
        for (String term : terms) {
            if (content.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private double calculateActivityScore(LocalDateTime timestamp, LocalDateTime now) {
        if (timestamp == null) {
            return 0.25;
        }
        long hours = Math.abs(Duration.between(timestamp, now).toHours());
        if (hours <= 6) {
            return 0.96;
        }
        if (hours <= 24) {
            return 0.84;
        }
        if (hours <= 72) {
            return 0.68;
        }
        if (hours <= 24 * 7) {
            return 0.5;
        }
        if (hours <= 24 * 30) {
            return 0.34;
        }
        return 0.18;
    }

    private int calculateOrbitLevel(double importance, double activityScore) {
        double score = importance * 0.65 + activityScore * 0.35;
        if (score >= 0.82) {
            return 1;
        }
        if (score >= 0.56) {
            return 2;
        }
        return 3;
    }

    private double calculateDensity(int nodes, int links) {
        if (nodes <= 1) {
            return 0.0;
        }
        double maxEdges = (double) nodes * (nodes - 1) / 2.0;
        return round(links / maxEdges);
    }

    private String formatDateTime(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String shortenLabel(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return "未命名记忆";
        }
        String compact = raw.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private final class TopicAggregate {
        private final String key;
        private final String id;
        private final String label;
        private final Set<String> userIds = new java.util.LinkedHashSet<>();
        private int factCount = 0;
        private double totalImportance = 0.0;
        private double totalActivity = 0.0;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivatedAt;
        private double importance = 0.5;
        private double activityScore = 0.4;
        private int orbitLevel = 1;

        private TopicAggregate(String key, String label) {
            this.key = key;
            this.id = "topic_" + key;
            this.label = label;
        }

        private void include(FactNode fact, double activityScore) {
            factCount++;
            totalImportance += fact.getImportance();
            totalActivity += activityScore;
            if (createdAt == null || (fact.getObservedAt() != null && fact.getObservedAt().isBefore(createdAt))) {
                createdAt = fact.getObservedAt();
            }
            if (lastActivatedAt == null || (fact.getObservedAt() != null && fact.getObservedAt().isAfter(lastActivatedAt))) {
                lastActivatedAt = fact.getObservedAt();
            }
            importance = round(totalImportance / factCount);
            this.activityScore = round(totalActivity / factCount);
            orbitLevel = calculateOrbitLevel(importance, this.activityScore);
        }
    }

    private static final class FactContext {
        private final String userNodeId;
        private final String topicKey;
        private final FactNode fact;

        private FactContext(String userNodeId, String topicKey, FactNode fact) {
            this.userNodeId = userNodeId;
            this.topicKey = topicKey;
            this.fact = fact;
        }
    }

    private static final class RelationshipGuess {
        private final String type;
        private final double weight;

        private RelationshipGuess(String type, double weight) {
            this.type = type;
            this.weight = weight;
        }
    }

    private static final class SemanticProfile {
        private final String topicKey;
        private final String subType;
        private final double confidence;
        private final String source;

        private SemanticProfile(String topicKey, String subType, double confidence, String source) {
            this.topicKey = topicKey;
            this.subType = subType;
            this.confidence = confidence;
            this.source = source;
        }
    }

    private static final class FactWriteResult {
        private final UserNode user;
        private final FactNode savedFact;
        private final boolean createdNewFact;

        private FactWriteResult(UserNode user, FactNode savedFact, boolean createdNewFact) {
            this.user = user;
            this.savedFact = savedFact;
            this.createdNewFact = createdNewFact;
        }
    }

    @Override
    public void deleteFact(Long factId) {
        String factContent = factRepository.findById(factId)
                .map(f -> f.getContent())
                .orElse("未知内容");
        
        log.info("Cognitive cleanup: Removing fact node [{}] (ID: #{})", factContent, factId);
        systemLogService.info("认知清理: 删除事实节点 [" + (factContent.length() > 30 ? factContent.substring(0, 30) + "..." : factContent) + "]", "MEMORY");
        
        systemLogService.dbStart("neo4j_delete", "FactNode#" + factId, "MEMORY");
        try {
            factRepository.deleteById(factId);
            log.debug("Neo4j node removal successful: {}", factId);
            synchronizeAllFactRelations();
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

    @Override
    public Object runMemoryMaintenance() {
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;
        int archived = 0;
        int cooled = 0;
        int reactivated = 0;

        List<FactNode> facts = new ArrayList<>();
        factRepository.findAll().forEach(facts::add);
        for (FactNode fact : facts) {
            processed++;
            LocalDateTime activatedAt = fact.getLastActivatedAt() != null ? fact.getLastActivatedAt() : fact.getObservedAt();
            double computedActivity = calculateActivityScore(activatedAt, now);
            fact.setActivityScore(round(computedActivity));
            if (fact.getNormalizedContent() == null || fact.getNormalizedContent().isBlank()) {
                fact.setNormalizedContent(normalizedFactContent(fact));
            }
            if (fact.getClusterKey() == null || fact.getClusterKey().isBlank() || "memory".equals(normalizeTopicKey(fact.getClusterKey()))) {
                SemanticProfile semanticProfile = classifyFactSemantics(fact.getContent(), null);
                fact.setCategory(semanticProfile.topicKey);
                fact.setClusterKey(semanticProfile.topicKey);
                fact.setSubType(semanticProfile.subType);
                if (fact.getConfidence() <= 0) {
                    fact.setConfidence(semanticProfile.confidence);
                }
            } else if (fact.getSubType() == null || fact.getSubType().isBlank()) {
                fact.setSubType(inferFactSubType(fact));
            }
            if (fact.getConfidence() <= 0) {
                fact.setConfidence(0.84);
            }
            if (fact.getVersion() == null || fact.getVersion() < 1) {
                fact.setVersion(1);
            }
            if (fact.getDecayRate() == null) {
                fact.setDecayRate(0.015);
            }
            if (fact.getTtlDays() == null) {
                fact.setTtlDays(180);
            }

            boolean shouldArchive = shouldArchiveFact(fact, now);
            if (shouldArchive) {
                if (!"archived".equals(fact.getStatus())) {
                    archived++;
                }
                fact.setStatus("archived");
                fact.setImportance(round(Math.max(0.12, fact.getImportance() * 0.85)));
            } else if ("superseded".equals(fact.getStatus()) || "conflicted".equals(fact.getStatus())) {
                // 保持特殊状态，但刷新活跃度
            } else if (computedActivity < 0.3) {
                if (!"cool".equals(fact.getStatus())) {
                    cooled++;
                }
                fact.setStatus("cool");
            } else if (computedActivity < 0.75) {
                fact.setStatus("stable");
            } else {
                if (!"active".equals(fact.getStatus())) {
                    reactivated++;
                }
                fact.setStatus("active");
            }
        }

        if (!facts.isEmpty()) {
            factRepository.saveAll(facts);
            synchronizeAllFactRelations();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("processed", processed);
        summary.put("archived", archived);
        summary.put("cooled", cooled);
        summary.put("reactivated", reactivated);
        summary.put("generatedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        lastMaintenanceSummary = summary;
        systemLogService.info(String.format("记忆维护完成: processed=%d archived=%d cooled=%d reactivated=%d", processed, archived, cooled, reactivated), "MEMORY");
        return summary;
    }

    @Override
    public Object getMemoryMaintenanceSummary() {
        if (lastMaintenanceSummary.isEmpty()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("processed", 0);
            summary.put("archived", 0);
            summary.put("cooled", 0);
            summary.put("reactivated", 0);
            summary.put("generatedAt", null);
            return summary;
        }
        return lastMaintenanceSummary;
    }
}
