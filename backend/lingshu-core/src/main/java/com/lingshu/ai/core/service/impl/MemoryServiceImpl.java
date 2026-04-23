package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.FactSemanticClassification;
import com.lingshu.ai.core.service.FactSemanticClassifier;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.core.service.EmotionAwareFactExtractor;
import com.lingshu.ai.core.dto.ExtractionResult;
import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.PostConstruct;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class MemoryServiceImpl implements MemoryService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MemoryServiceImpl.class);

    private static final double GAIN_THRESHOLD = 0.3;
    private static final Set<String> TOPIC_KEYS = Set.of("interest", "growth", "goal", "emotion", "relationship",
            "event", "timeline", "memory", "health");
    private static final Set<String> GENERIC_TOPIC_KEYS = Set.of("memory", "timeline");
    private static final Set<String> SUB_TYPES = Set.of("Preference", "EmotionState", "Person", "Project", "Goal",
            "Event", "TimeAnchor", "Memory", "HealthState");
    private static final Set<String> GENERIC_SUB_TYPES = Set.of("Memory", "TimeAnchor");
    private static final long SEMANTIC_CLASSIFY_TIMEOUT_SECONDS = 8;
    private static final long HISTORY_EMBED_TIMEOUT_SECONDS = 6;
    private static final long HISTORY_SEARCH_TIMEOUT_SECONDS = 4;
    private static final int RECENT_RETRIEVAL_EVENT_LIMIT = 50;
    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "的", "了", "是", "在", "我", "你", "他", "她", "它", "们",
            "这", "那", "有", "和", "与", "或", "但", "如果", "因为",
            "所以", "但是", "然后", "就", "都", "也", "还", "又", "很",
            "什么", "怎么", "为什么", "哪", "谁", "几", "多少", "怎样",
            "吗", "呢", "吧", "啊", "呀", "哦", "嗯", "哈", "嘿",
            "一个", "一些", "这个", "那个", "不是", "没有", "可以", "会",
            "能", "要", "想", "去", "来", "说", "看", "做", "给", "让");

    private final UserRepository userRepository;
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final dev.langchain4j.store.embedding.EmbeddingStore<TextSegment> embeddingStore;
    private final FactRepository factRepository;
    private final Neo4jClient neo4jClient;
    private final com.lingshu.ai.core.service.SystemLogService systemLogService;
    private final ChatModel chatLanguageModel;
    private final DynamicMemoryModel dynamicMemoryModel;
    private final com.lingshu.ai.core.service.SettingService settingService;
    private final Executor taskExecutor;
    private FactSemanticClassifier factSemanticClassifier;
    private com.lingshu.ai.core.service.FactRelationshipEvaluator factRelationshipEvaluator;
    private EmotionAwareFactExtractor emotionAwareFactExtractor;
    // Removed entityExtractor
    private volatile Map<String, Object> lastMaintenanceSummary = Collections.emptyMap();
    private final Object retrievalEventsLock = new Object();
    private final java.util.Deque<com.lingshu.ai.core.dto.MemoryRetrievalEvent> recentRetrievalEvents = new java.util.ArrayDeque<>();

    public MemoryServiceImpl(UserRepository userRepository,
                             dev.langchain4j.model.embedding.EmbeddingModel embeddingModel,
                             dev.langchain4j.store.embedding.EmbeddingStore<TextSegment> embeddingStore,
                             FactRepository factRepository,
                             Neo4jClient neo4jClient,
                             com.lingshu.ai.core.service.SystemLogService systemLogService,
                             com.lingshu.ai.core.service.SettingService settingService,
                             ChatModel chatLanguageModel,
                             DynamicMemoryModel dynamicMemoryModel,
                             Executor taskExecutor) {
        this.userRepository = userRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.factRepository = factRepository;
        this.neo4jClient = neo4jClient;
        this.systemLogService = systemLogService;
        this.settingService = settingService;
        this.chatLanguageModel = chatLanguageModel;
        this.dynamicMemoryModel = dynamicMemoryModel;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    public void initAiServices() {
        this.factRelationshipEvaluator = AiServices.builder(com.lingshu.ai.core.service.FactRelationshipEvaluator.class)
                .chatModel(dynamicMemoryModel)
                .build();
        this.factSemanticClassifier = AiServices.builder(FactSemanticClassifier.class)
                .chatModel(dynamicMemoryModel)
                .build();
        this.emotionAwareFactExtractor = AiServices.builder(EmotionAwareFactExtractor.class)
                .chatModel(dynamicMemoryModel)
                .build();
        // Removed entityExtractor initialization

        try {
            factRepository.createOriginalMessageIndex();
            factRepository.createEmotionalToneIndex();
            log.info("Neo4j indexes initialized successfully.");
        } catch (Exception e) {
            log.warn("Failed to create Neo4j indexes: {}", e.getMessage());
        }
    }

    @Override
    public void extractFacts(String userId, String message) {
        extractFacts(userId, message, "", null);
    }

    @Override
    public void extractFacts(String userId, String message, com.lingshu.ai.core.dto.EmotionAnalysis emotion) {
        extractFacts(userId, message, "", emotion);
    }

    @Async("taskExecutor")
    @Override
    public void extractFacts(String userId, String message, String assistantResponse, com.lingshu.ai.core.dto.EmotionAnalysis emotion) {
        final String messageSnapshot = message;
        log.debug("Memory pulse: Analyzing input for cognitive facts: {}", messageSnapshot);
        systemLogService.info("事实提取: 开始分析用户消息...", "FACT");

        UserNode user = userRepository.findByName(userId).orElse(null);

        StringBuilder currentFactsBuilder = new StringBuilder();
        if (user != null && user.getFacts() != null) {
            user.getFacts()
                    .forEach(f -> currentFactsBuilder.append(String.format("[%d] %s; ", f.getId(), f.getContent())));
        }

        ExtractionResult extractionResult = null;

        if (emotionAwareFactExtractor != null) {
            String modelName = dynamicMemoryModel.getModelName();
            systemLogService.info(String.format("使用情感感知事实提取器 (模型: %s)...", modelName), "FACT");
            systemLogService.llmStart("emotion-aware-fact-extractor", modelName, "FACT");

            try {
                String emotionType = (emotion != null && emotion.getEmotion() != null) ? emotion.getEmotion() : "neutral";
                Double emotionIntensity = (emotion != null && emotion.getIntensity() != null) ? emotion.getIntensity() : 0.0;
                String triggerKeywords = (emotion != null && emotion.getKeywords() != null) ? String.join(", ", emotion.getKeywords()) : "";
                Boolean needsComfort = emotion != null ? emotion.getNeedsComfort() : Boolean.FALSE;

                LocalDateTime now = LocalDateTime.now();
                String currentDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String currentDayOfWeek = now.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.CHINESE);

                extractionResult = emotionAwareFactExtractor.analyzeWithEmotion(
                        messageSnapshot,
                        assistantResponse != null ? assistantResponse : "",
                        currentFactsBuilder.toString(),
                        emotionType,
                        emotionIntensity,
                        "stable",
                        triggerKeywords,
                        needsComfort,
                        currentDateTime,
                        currentDayOfWeek);
                systemLogService.llmEnd(0, "FACT");

                if (extractionResult != null) {
                    systemLogService.info("情感感知提取完成: " + extractionResult, "FACT");
                }
            } catch (Exception e) {
                log.warn("情感感知事实提取失败，回退到标准提取: {}", e.getMessage());
                systemLogService.llmError(e.getMessage(), "FACT");
            }
        }

        if (extractionResult == null) {
            log.debug("Memory pulse: No cognitive updates required for this message.");
            systemLogService.info("事实提取完成: 无需更新记忆", "FACT");
            return;
        }

        processExtractionResult(userId, messageSnapshot, user, extractionResult, emotion);

    }

    private void processExtractionResult(String userId, String messageSnapshot, UserNode user,
                                         ExtractionResult result, com.lingshu.ai.core.dto.EmotionAnalysis emotion) {

        log.debug("提取结果已收到: {} 新事实, {} 请求删除",
                result.getNewFacts() != null ? result.getNewFacts().size() : 0,
                result.getDeletedFactIds() != null ? result.getDeletedFactIds().size() : 0);

        if (result.getDeletedFactIds() != null && !result.getDeletedFactIds().isEmpty()) {
            for (Long id : result.getDeletedFactIds()) {
                String factContent = "Unknown";
                if (user != null && user.getFacts() != null) {
                    factContent = user.getFacts().stream()
                            .filter(f -> f.getId() != null && f.getId().equals(id))
                            .map(f -> f.getContent())
                            .findFirst()
                            .orElse("Unknown");
                    user.getFacts().removeIf(f -> f.getId() != null && f.getId().equals(id));
                }
                log.info("记忆修正：删除过时事实 [{}] (ID: {})", factContent, id);
                systemLogService.info("删除过时事实: " + factContent, "FACT");
                this.deleteFact(id);
            }
        }

        if (result.getNewFacts() != null && !result.getNewFacts().isEmpty()) {
            if (user == null) {
                systemLogService.info("创建新用户节点: " + userId, "FACT");
                user = UserNode.builder()
                        .name(userId)
                        .firstEncounter(LocalDateTime.now())
                        .build();
            }

            for (ExtractionResult.ExtractedFact fact : result.getNewFacts()) {
                if (fact.getContent() == null || fact.getContent().trim().isEmpty()) {
                    log.error("新事实为空，跳过 {}", fact);
                    continue;
                }

                log.info("新的情绪感知事实候选: {} (type={}, confidence={})",
                        fact.getContent(), fact.getType(), fact.getConfidence());

                FactWriteResult writeResult = persistFactCandidateWithMetadata(user, fact, messageSnapshot, emotion);
                user = writeResult.user;

                if (!writeResult.createdNewFact || writeResult.savedFact == null) {
                    systemLogService.info("事实去重: 已存在相似事实，跳过 ["
                            + (fact.getContent().length() > 30 ? fact.getContent().substring(0, 30) + "..."
                            : fact.getContent())
                            + "]", "FACT");
                    continue;
                }

                systemLogService.success("新增事实: " + writeResult.savedFact.getContent(), "FACT");
                storeFactEmbedding(userId, writeResult.savedFact, messageSnapshot, emotion, fact);
            }

            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        }
    }
    private FactWriteResult persistFactCandidateWithMetadata(UserNode user,
                                                             ExtractionResult.ExtractedFact extractedFact,
                                                             String originalMessage, com.lingshu.ai.core.dto.EmotionAnalysis emotion) {
        LocalDateTime now = LocalDateTime.now();
        String factText = extractedFact.getContent();
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

        double confidence = extractedFact.getConfidence() != null ? extractedFact.getConfidence().getValue()
                : semanticProfile.confidence;



        RelationDecision relationDecision = detectBestFactRelation(user, factText, normalized);
        FactNode bestMatch = relationDecision.match;
        String relationType = relationDecision.type;

        if (bestMatch != null && "CONTRADICTS".equals(relationType)) {
            bestMatch.setStatus("conflicted");
            bestMatch.setLastActivatedAt(now);
            bestMatch.setActivityScore(calculateActivityScore(now, now));
            factRepository.save(bestMatch);

            FactNode factNode = buildNewFactNodeWithMetadata(
                    factText, normalized, clusterKey, subType,
                    confidence, semanticProfile.source, now, originalMessage, emotion, extractedFact);
            factNode.setContradictsFactId(bestMatch.getId());
            factNode.setStatus("conflicted");
            systemLogService.info("检测到冲突记忆，创建冲突版本: " + shortenLabel(factText, 24), "MEMORY");
            return saveNewFact(user, factNode);
        }

        if (bestMatch != null && "SUPERSEDES".equals(relationType)) {
            bestMatch.setStatus("superseded");
            bestMatch.setLastActivatedAt(now);
            bestMatch.setActivityScore(calculateActivityScore(now, now));
            factRepository.save(bestMatch);

            FactNode factNode = buildNewFactNodeWithMetadata(
                    factText, normalized, clusterKey, subType,
                    confidence, semanticProfile.source, now, originalMessage, emotion, extractedFact);
            factNode.setSupersedesFactId(bestMatch.getId());
            factNode.setVersion((bestMatch.getVersion() == null ? 1 : bestMatch.getVersion()) + 1);
            factNode.setStatus("active");
            systemLogService.info("检测到相似记忆，创建替代版本: " + shortenLabel(factText, 24), "MEMORY");
            return saveNewFact(user, factNode);
        }

        FactNode factNode = buildNewFactNodeWithMetadata(
                factText, normalized, clusterKey, subType,
                confidence, semanticProfile.source, now, originalMessage, emotion, extractedFact);
        return saveNewFact(user, factNode);
    }



    private RelationDecision detectBestFactRelation(UserNode user, String factText, String normalized) {
        if (user == null || user.getFacts() == null || user.getFacts().isEmpty()) {
            return new RelationDecision(null, "NONE");
        }

        FactNode bestMatch = null;
        String relationType = "NONE";

        try {
            systemLogService.embeddingStart(factText.length(), "MEMORY");
            CompletableFuture<dev.langchain4j.data.embedding.Embedding> embedFuture = CompletableFuture.supplyAsync(
                    () -> embeddingModel.embed(factText).content());
            dev.langchain4j.data.embedding.Embedding queryEmbedding = embedFuture.get(HISTORY_EMBED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            systemLogService.embeddingEnd("MEMORY");

            systemLogService.dbStart("pgvector_query", "embeddingStore", "MEMORY");
            dev.langchain4j.store.embedding.EmbeddingSearchRequest searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest
                    .builder()
                    .queryEmbedding(queryEmbedding)
                    .filter(MetadataFilterBuilder.metadataKey("user_id").isEqualTo(user.getName()))
                    .maxResults(5)
                    .minScore(0.5)
                    .build();
            CompletableFuture<dev.langchain4j.store.embedding.EmbeddingSearchResult<dev.langchain4j.data.segment.TextSegment>> searchFuture =
                    CompletableFuture.supplyAsync(() -> embeddingStore.search(searchRequest));
            dev.langchain4j.store.embedding.EmbeddingSearchResult<dev.langchain4j.data.segment.TextSegment> searchResult =
                    searchFuture.get(HISTORY_SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            systemLogService.dbEnd("pgvector_query", "MEMORY");

            List<Long> userFactIds = user.getFacts().stream()
                    .filter(this::isComparableForConflict)
                    .filter(f -> f.getId() != null)
                    .map(FactNode::getId)
                    .collect(Collectors.toList());

            List<FactNode> candidates = new java.util.ArrayList<>();
            for (dev.langchain4j.store.embedding.EmbeddingMatch<dev.langchain4j.data.segment.TextSegment> match : searchResult.matches()) {
                String factIdStr = match.embedded().metadata().getString("fact_id");
                if (factIdStr == null) {
                    continue;
                }
                Long factId = Long.parseLong(factIdStr);
                if (!userFactIds.contains(factId)) {
                    continue;
                }

                FactNode existingFact = user.getFacts().stream()
                        .filter(this::isComparableForConflict)
                        .filter(f -> f.getId().equals(factId))
                        .findFirst()
                        .orElse(null);
                if (existingFact != null) {
                    candidates.add(existingFact);
                }
            }

            // 先按字面相似度过滤，保留最相关的 3 条
            final String finalNormalized = normalized;
            List<FactNode> filteredCandidates = candidates.stream()
                    .filter(c -> lexicalSimilarity(finalNormalized, normalizedFactContent(c)) > 0.15)
                    .sorted((a, b) -> Double.compare(
                            lexicalSimilarity(finalNormalized, normalizedFactContent(b)),
                            lexicalSimilarity(finalNormalized, normalizedFactContent(a))))
                    .limit(3)
                    .collect(Collectors.toList());

            if (!filteredCandidates.isEmpty() && factRelationshipEvaluator != null) {
                String modelName = dynamicMemoryModel.getModelName();
                systemLogService.llmStart("fact-relationship-evaluator", modelName, "FACT");
                
                StringBuilder sb = new StringBuilder();
                for (FactNode c : filteredCandidates) {
                    sb.append(c.getId()).append(": ").append(c.getContent()).append("\n");
                }
                
                com.lingshu.ai.core.dto.FactRelationshipResult relResult = factRelationshipEvaluator
                        .evaluateBatch(sb.toString(), factText);
                systemLogService.llmEnd(0, "FACT");

                if (relResult != null && !"NONE".equals(relResult.getType()) && relResult.getMatchedFactId() != null) {
                    Long matchedId = relResult.getMatchedFactId();
                    bestMatch = filteredCandidates.stream()
                            .filter(c -> c.getId().equals(matchedId))
                            .findFirst()
                            .orElse(null);
                    relationType = relResult.getType();
                }
            }
        } catch (Exception e) {
            log.warn("Vector search or LLM evaluation failed, falling back to rule-based: {}", e.getMessage());
        }

        if (bestMatch != null && !"NONE".equals(relationType)) {
            return new RelationDecision(bestMatch, relationType);
        }

        double bestSimilarity = 0.0;
        boolean contradiction = false;
        FactNode lexicalBestMatch = null;
        for (FactNode existing : user.getFacts()) {
            if (!isComparableForConflict(existing) || existing.getContent() == null || existing.getContent().isBlank()) {
                continue;
            }
            double similarity = lexicalSimilarity(normalized, normalizedFactContent(existing));
            boolean candidateContradiction = isContradictory(existing.getContent(), factText);
            if (candidateContradiction && similarity >= 0.22) {
                lexicalBestMatch = existing;
                contradiction = true;
                relationType = "CONTRADICTS";
                break;
            }
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                lexicalBestMatch = existing;
            }
        }

        if (lexicalBestMatch == null) {
            return new RelationDecision(null, "NONE");
        }
        if (contradiction) {
            return new RelationDecision(lexicalBestMatch, "CONTRADICTS");
        }
        if (bestSimilarity >= 0.68) {
            return new RelationDecision(lexicalBestMatch, "SUPERSEDES");
        }
        if (bestSimilarity >= 0.38) {
            return new RelationDecision(lexicalBestMatch, "RELATED_TO");
        }
        return new RelationDecision(null, "NONE");
    }

    private boolean isComparableForConflict(FactNode fact) {
        if (fact == null) {
            return false;
        }
        String status = fact.getStatus();
        if (status == null || status.isBlank()) {
            return true;
        }
        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        return !"archived".equals(normalizedStatus)
                && !"superseded".equals(normalizedStatus)
                && !"conflicted".equals(normalizedStatus);
    }

    private FactNode buildNewFactNodeWithMetadata(String factText, String normalized, String clusterKey, String subType,
                                                  double semanticConfidence, String source, LocalDateTime now,
                                                  String originalMessage, com.lingshu.ai.core.dto.EmotionAnalysis emotion,
                                                  ExtractionResult.ExtractedFact extractedFact) {
        double baseImportance = 0.8;
        if (emotion != null && emotion.getIntensity() != null) {
            double intensityBoost = emotion.getIntensity() * 0.2;
            baseImportance = Math.min(1.0, baseImportance + intensityBoost);
        }

        String toneStr = "neutral";
        if (emotion != null && emotion.getEmotion() != null) {
            toneStr = emotion.getEmotion();
        } else {
            toneStr = inferEmotionalToneFromKeywords(originalMessage);
        }

        String factStatus = extractedFact.isVolatile() ? "volatile" : "active";
        double confidence = extractedFact.getConfidence() != null ? extractedFact.getConfidence().getValue()
                : semanticConfidence;

        LocalDateTime eventTime = extractedFact.getEventTime();

        return FactNode.builder()
                .content(factText)
                .category(clusterKey)
                .normalizedContent(normalized)
                .subType(subType)
                .clusterKey(clusterKey)
                .observedAt(now)
                .eventTime(eventTime)
                .lastActivatedAt(now)
                .importance(baseImportance)
                .confidence(round(Math.max(0.72, confidence)))
                .classificationSource(source)
                .activityScore(calculateActivityScore(now, now))
                .status(factStatus)
                .decayRate(0.015)
                .ttlDays(180)
                .version(1)
                .originalMessage(originalMessage)
                .emotionalTone(toneStr)
                .involvedEntities(java.util.Optional.ofNullable(extractEntities(factText)).filter(l -> !l.isEmpty())
                        .map(java.util.HashSet::new).orElse(null))
                .build();
    }

    private void storeFactEmbedding(String userId, FactNode savedFact, String messageSnapshot,
                                    com.lingshu.ai.core.dto.EmotionAnalysis emotion,
                                    ExtractionResult.ExtractedFact extractedFact) {
        String emotionalToneStr;
        if (emotion != null) {
            emotionalToneStr = emotion.getEmotion();
        } else {
            emotionalToneStr = inferEmotionalToneFromKeywords(messageSnapshot);
        }

        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        if (savedFact.getId() != null) {
            metadata.put("fact_id", savedFact.getId().toString());
        }
        metadata.put("user_id", userId);
        metadata.put("emotional_tone", emotionalToneStr);
        metadata.put("category", savedFact.getClusterKey() != null ? savedFact.getClusterKey() : "unknown");
        metadata.put("content", savedFact.getContent());
        metadata.put("timestamp", java.time.LocalDateTime.now().toString());
        metadata.put("fact_type", extractedFact.getType() != null ? extractedFact.getType().name() : "UNKNOWN");
        metadata.put("confidence",
                String.valueOf(extractedFact.getConfidence() != null ? extractedFact.getConfidence().getValue() : 0.7));
        metadata.put("volatile", String.valueOf(extractedFact.isVolatile()));

        if (extractedFact.getTriggerKeywords() != null && !extractedFact.getTriggerKeywords().isEmpty()) {
            metadata.put("trigger_keywords", String.join(",", extractedFact.getTriggerKeywords()));
        }

        String fullContext = String.format(
                "用户记录原始消息：%s|||提取事实陈述：%s",
                messageSnapshot != null ? messageSnapshot : "无记录",
                savedFact.getContent());
        TextSegment segment = TextSegment.from(fullContext, new dev.langchain4j.data.document.Metadata(metadata));

        systemLogService.embeddingStart(savedFact.getContent().length(), "MEMORY");
        try {
            dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
            systemLogService.embeddingEnd("MEMORY");
        } catch (Exception e) {
            systemLogService.error("Embedding向量化失败: " + e.getMessage(), "MEMORY");
        }
    }

    private String inferEmotionalToneFromKeywords(String message) {
        if (message == null)
            return "neutral";
        String lower = message.toLowerCase();
        if (lower.matches(".*(开心|高兴|喜欢|爱|棒|good|happy|love).*"))
            return "positive";
        if (lower.matches(".*(难过|悲伤|讨厌|恨|bad|sad|hate).*"))
            return "negative";
        return "neutral";
    }

    @Override
    public String retrieveContext(String userId, String message) {
        com.lingshu.ai.core.dto.MemoryRetrievalEvent.MemoryRetrievalEventBuilder eventBuilder = com.lingshu.ai.core.dto.MemoryRetrievalEvent
                .builder()
                .userId(userId)
                .query(message)
                .timestamp(LocalDateTime.now())
                .extractedEntities(new ArrayList<>())
                .adoptedFactIds(new ArrayList<>())
                .semanticMatches(new ArrayList<>())
                .graphMatchedContent(new ArrayList<>())
                .finalRankedContent(new ArrayList<>())
                .finalRankedIds(new ArrayList<>());

        List<String> entities = extractEntities(message);
        eventBuilder.extractedEntities(entities);

        // 1. 图谱检索 (Graph Retrieval)
        List<GraphRetrievalHit> graphFacts = performGraphRetrievalV2(userId, message, entities, eventBuilder);

        // 2. 路由决策 (Routing Decision)
        double gain = calculateGainV2(entities, graphFacts);
        eventBuilder.gain(gain);
        long multiHopCount = graphFacts.stream().filter(hit -> hit.hop >= 2).count();
        long supersededCount = graphFacts.stream()
                .filter(hit -> hit.fact.getStatus() != null && "superseded".equalsIgnoreCase(hit.fact.getStatus()))
                .count();
        long staleCount = graphFacts.stream()
                .filter(hit -> hit.fact.getObservedAt() != null
                        && Duration.between(hit.fact.getObservedAt(), LocalDateTime.now()).toDays() > 90)
                .count();
        String routingDecision;
        boolean runVector;

        if (gain >= GAIN_THRESHOLD) {
            routingDecision = "GRAPH_PRIORITIZED_VECTOR_SUPPLEMENT";
            runVector = true;
            systemLogService.info(String.format("路由决策: 图谱增益(%.2f) >= 阈值(%.2f)，执行图谱优先+向量补召回", gain, GAIN_THRESHOLD), "MEMORY");
        } else if (graphFacts.isEmpty()) {
            routingDecision = "VECTOR_BACKUP";
            runVector = true;
            systemLogService.info(String.format("路由决策: 图谱增益(%.2f) < 阈值，但图谱无结果，执行向量兜底", gain), "MEMORY");
        } else {
            routingDecision = "GRAPH_ONLY";
            runVector = false;
            systemLogService.info(String.format("路由决策: 图谱增益(%.2f) < 阈值，且图谱已有结果，直接返回图谱内容", gain), "MEMORY");
        }
        eventBuilder.routingDecision(routingDecision);
        systemLogService.info(String.format(
                "Routing stats: multiHop=%d, supersededPenalty=%d, stalePenalty=%d, gain=%.2f",
                multiHopCount, supersededCount, staleCount, gain), "MEMORY");

        // 3. 向量检索 (Vector Retrieval)
        List<String> vectorTexts = new ArrayList<>();
        List<Long> vectorFactIds = new ArrayList<>();
        if (runVector) {
            vectorTexts = performVectorRetrievalV2(userId, message, eventBuilder, vectorFactIds);
        }

        // 4. 合并与去重 (Merge and Deduplicate) - Task 3.1 & 3.2
        List<String> finalFacts = mergeAndDeduplicate(graphFacts.stream().map(hit -> hit.fact).collect(Collectors.toList()), vectorTexts);

        // 5. 组装上下文与记录事件
        StringBuilder contextBuilder = new StringBuilder();
        if (!finalFacts.isEmpty()) {
            contextBuilder.append("关于核心上下文的已知事实与记忆：\n");
            finalFacts.forEach(fact -> contextBuilder.append("- ").append(fact).append("\n"));
        }

        List<Long> adoptedFactIds = new ArrayList<>();
        graphFacts.stream()
                .map(hit -> hit.fact.getId())
                .filter(java.util.Objects::nonNull)
                .forEach(adoptedFactIds::add);
        vectorFactIds.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(adoptedFactIds::add);
        eventBuilder.adoptedFactIds(adoptedFactIds.stream().distinct().collect(Collectors.toList()));
        eventBuilder.finalRankedContent(finalFacts);
        com.lingshu.ai.core.dto.MemoryRetrievalEvent event = eventBuilder.build();
        recordRetrievalEvent(event);
        updateRelationshipsFromRetrievalEvent(event);

        return contextBuilder.toString();
    }


    private List<GraphRetrievalHit> performGraphRetrievalV2(String userId, String message, List<String> entities,
                                                            com.lingshu.ai.core.dto.MemoryRetrievalEvent.MemoryRetrievalEventBuilder eventBuilder) {
        List<GraphRetrievalHit> result = new ArrayList<>();
        userRepository.findByName(userId).ifPresent(user -> {
            if (user.getFacts() == null || user.getFacts().isEmpty()) {
                return;
            }

            List<GraphRetrievalHit> oneHop = new ArrayList<>();
            if (!entities.isEmpty()) {
                List<FactNode> matched = user.getFacts().stream()
                        .filter(f -> f.getContent() != null && entities.stream()
                                .anyMatch(e -> f.getContent().toLowerCase().contains(e.toLowerCase())))
                        .sorted((f1, f2) -> Double.compare(f2.getImportance(), f1.getImportance()))
                        .limit(8)
                        .toList();
                oneHop.addAll(matched.stream().map(fact -> new GraphRetrievalHit(fact, 1, 1.0)).collect(Collectors.toList()));
            }

            List<GraphRetrievalHit> directHits = new ArrayList<>(oneHop);
            boolean isIdentity = isIdentityQuery(message, entities);
            if (directHits.size() < 3 || isIdentity) {
                List<FactNode> coreFacts = user.getFacts().stream()
                        .filter(f -> oneHop.stream().noneMatch(hit -> java.util.Objects.equals(hit.fact.getId(), f.getId())))
                        .sorted((f1, f2) -> Double.compare(f2.getImportance(), f1.getImportance()))
                        .limit(isIdentity ? 5 : 2)
                        .toList();
                oneHop.addAll(coreFacts.stream().map(fact -> new GraphRetrievalHit(fact, 1, 1.0)).collect(Collectors.toList()));
                eventBuilder.fallbackActivated(true);
            }

            List<GraphRetrievalHit> expanded = new ArrayList<>(oneHop);
            if (!directHits.isEmpty() && directHits.size() < 3) {
                expanded.addAll(loadSecondHopFacts(user, directHits));
            }

            List<GraphRetrievalHit> deduplicated = deduplicateGraphHits(expanded);
            result.addAll(deduplicated);
            eventBuilder.graphMatchedContent(deduplicated.stream().map(hit -> hit.fact.getContent()).collect(Collectors.toList()));
            eventBuilder.graphMatchedIds(deduplicated.stream()
                    .map(hit -> hit.fact.getId())
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList()));
        });
        return result;
    }

    private List<String> performVectorRetrievalV2(String userId, String message,
                                                  com.lingshu.ai.core.dto.MemoryRetrievalEvent.MemoryRetrievalEventBuilder eventBuilder,
                                                  List<Long> rankedIdsOut) {
        List<String> result = new ArrayList<>();
        try {
            dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(message).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .filter(MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId))
                    .maxResults(5)
                    .minScore(0.6)
                    .build();
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            List<com.lingshu.ai.core.dto.MemoryRetrievalEvent.SemanticMatch> semanticMatches = new ArrayList<>();
            List<Long> rankedIds = new ArrayList<>();

            for (EmbeddingMatch<TextSegment> match : matches) {
                dev.langchain4j.data.segment.TextSegment embedded = match.embedded();
                String displayText = embedded.metadata() != null ? embedded.metadata().getString("content") : null;
                if (displayText == null || displayText.isBlank()) {
                    displayText = embedded.text();
                }
                result.add(displayText);

                Long factId = null;
                if (embedded.metadata() != null && embedded.metadata().getString("fact_id") != null) {
                    try {
                        factId = Long.parseLong(embedded.metadata().getString("fact_id"));
                        rankedIds.add(factId);
                        rankedIdsOut.add(factId);
                    } catch (Exception ignored) {
                    }
                }
                semanticMatches.add(new com.lingshu.ai.core.dto.MemoryRetrievalEvent.SemanticMatch(factId, match.score(), displayText));
            }
            eventBuilder.semanticMatches(semanticMatches);
            eventBuilder.finalRankedIds(rankedIds);
        } catch (Exception e) {
            log.warn("Vector retrieval failed: {}", e.getMessage());
        }
        return result;
    }

    private List<GraphRetrievalHit> loadSecondHopFacts(UserNode user, List<GraphRetrievalHit> seedHits) {
        List<Long> seedIds = seedHits.stream()
                .map(hit -> hit.fact.getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (seedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> excludeIds = new java.util.HashSet<>(seedIds);

        String cypher = """
                MATCH (seed:Fact)-[r:RELATED_TO]-(related:Fact)
                WHERE id(seed) IN $seedIds
                  AND NOT id(related) IN $excludeIds
                RETURN DISTINCT id(related) AS factId,
                       coalesce(r.weight, 0.5) AS relationWeight
                ORDER BY relationWeight DESC
                LIMIT 5
                """;

        Map<Long, FactNode> factIndex = user.getFacts().stream()
                .filter(f -> f.getId() != null)
                .collect(Collectors.toMap(FactNode::getId, fact -> fact, (left, right) -> left, LinkedHashMap::new));

        return neo4jClient.query(cypher)
                .bind(seedIds).to("seedIds")
                .bind(excludeIds).to("excludeIds")
                .fetch().all().stream()
                .map(row -> {
                    Long factId = castLong(row.get("factId"));
                    FactNode fact = factIndex.get(factId);
                    if (fact == null) {
                        return null;
                    }
                    double relationWeight = row.get("relationWeight") instanceof Number
                            ? ((Number) row.get("relationWeight")).doubleValue()
                            : 0.5d;
                    return new GraphRetrievalHit(fact, 2, relationWeight);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<GraphRetrievalHit> deduplicateGraphHits(List<GraphRetrievalHit> hits) {
        Map<Long, GraphRetrievalHit> deduped = new LinkedHashMap<>();
        for (GraphRetrievalHit hit : hits) {
            Long factId = hit.fact.getId();
            if (factId == null) {
                continue;
            }
            GraphRetrievalHit existing = deduped.get(factId);
            if (existing == null || hit.hop < existing.hop) {
                deduped.put(factId, hit);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private double calculateGainV2(List<String> entities, List<GraphRetrievalHit> activatedFacts) {
        if (entities.isEmpty() || activatedFacts.isEmpty()) {
            return 0.0;
        }

        Set<String> matchedEntities = new java.util.HashSet<>();
        double totalImportance = 0.0;
        LocalDateTime now = LocalDateTime.now();

        for (GraphRetrievalHit hit : activatedFacts) {
            FactNode fact = hit.fact;
            String content = fact.getContent() == null ? "" : fact.getContent().toLowerCase();
            boolean factMatched = false;
            for (String entity : entities) {
                if (content.contains(entity.toLowerCase())) {
                    matchedEntities.add(entity.toLowerCase());
                    factMatched = true;
                }
            }
            if (!factMatched) {
                continue;
            }

            double importance = fact.getImportance();
            if (fact.getStatus() != null && "superseded".equalsIgnoreCase(fact.getStatus())) {
                importance *= 0.3;
            }
            if (fact.getObservedAt() != null && Duration.between(fact.getObservedAt(), now).toDays() > 90) {
                importance *= 0.7;
            }
            if (hit.hop >= 2) {
                importance *= 0.7;
            }
            totalImportance += importance;
        }

        double entityRatio = (double) matchedEntities.size() / entities.size();
        double avgImportance = totalImportance / activatedFacts.size();
        return Math.min(1.0, entityRatio * 0.6 + avgImportance * 0.4);
    }

    @Override
    public void updateRelationshipsFromRetrievalEvent(com.lingshu.ai.core.dto.MemoryRetrievalEvent event) {
        if (event == null || event.getAdoptedFactIds() == null) {
            return;
        }

        List<Long> adoptedFactIds = event.getAdoptedFactIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (adoptedFactIds.size() < 2) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            List<com.lingshu.ai.core.dto.MemoryRetrievalEvent> eventSnapshot = snapshotRecentRetrievalEvents();
            for (int i = 0; i < adoptedFactIds.size(); i++) {
                for (int j = i + 1; j < adoptedFactIds.size(); j++) {
                    Long sourceId = adoptedFactIds.get(i);
                    Long targetId = adoptedFactIds.get(j);
                    long cooccurrenceCount = eventSnapshot.stream()
                            .filter(retrievalEvent -> containsAdoptedFacts(retrievalEvent, sourceId, targetId))
                            .count();
                    if (cooccurrenceCount >= 2) {
                        upsertRelatedFactRelation(sourceId, targetId, cooccurrenceCount, event.getTimestamp());
                    }
                }
            }
        }, taskExecutor).whenComplete((unused, ex) -> {
            if (ex != null) {
                log.error("Failed to update related facts from retrieval event", ex);
                systemLogService.error("更新事实关联失败: " + ex.getMessage(), "MEMORY");
            }
        });
    }

    private boolean containsAdoptedFacts(com.lingshu.ai.core.dto.MemoryRetrievalEvent event, Long sourceId, Long targetId) {
        if (event == null || event.getAdoptedFactIds() == null) {
            return false;
        }
        Set<Long> adopted = new java.util.HashSet<>(event.getAdoptedFactIds());
        return adopted.contains(sourceId) && adopted.contains(targetId);
    }

    private void upsertRelatedFactRelation(Long sourceId, Long targetId, long cooccurrenceCount, LocalDateTime activatedAt) {
        if (sourceId == null || targetId == null || java.util.Objects.equals(sourceId, targetId)) {
            return;
        }

        Long canonicalSource = Math.min(sourceId, targetId);
        Long canonicalTarget = Math.max(sourceId, targetId);
        Double existingWeight = factRepository.findRelatedRelationWeight(canonicalSource, canonicalTarget);
        double nextWeight = existingWeight == null ? 0.5 : Math.min(1.0, existingWeight + 0.1);

        Map<String, Object> relationRow = new LinkedHashMap<>();
        relationRow.put("sourceId", canonicalSource);
        relationRow.put("targetId", canonicalTarget);
        relationRow.put("weight", nextWeight);
        relationRow.put("lastActivatedAt", activatedAt != null ? activatedAt : LocalDateTime.now());
        factRepository.saveRelatedRelations(List.of(relationRow));

        if (existingWeight == null) {
            systemLogService.info(String.format(
                    "Created RELATED_TO relation: %d <-> %d weight=%.1f cooccurrence=%d",
                    canonicalSource, canonicalTarget, nextWeight, cooccurrenceCount), "MEMORY");
        } else {
            systemLogService.info(String.format(
                    "Strengthened RELATED_TO relation: %d <-> %d weight %.1f -> %.1f cooccurrence=%d",
                    canonicalSource, canonicalTarget, existingWeight, nextWeight, cooccurrenceCount), "MEMORY");
        }
    }

    private List<String> mergeAndDeduplicate(List<FactNode> graphFacts, List<String> vectorTexts) {
        List<String> finalFacts = new ArrayList<>();
        Set<String> normalizedSet = new java.util.HashSet<>();

        // 图谱结果优先
        for (FactNode fact : graphFacts) {
            String content = fact.getContent();
            String normalized = normalizeSemanticText(content);
            if (!normalizedSet.contains(normalized)) {
                finalFacts.add(formatFactWithTime(content, fact));
                normalizedSet.add(normalized);
            }
        }

        // 向量结果补充
        for (String text : vectorTexts) {
            String normalized = normalizeSemanticText(text);
            if (!normalizedSet.contains(normalized)) {
                // 执行更深度的语义去重检查
                boolean isDuplicate = false;
                for (String existing : finalFacts) {
                    if (lexicalSimilarity(normalized, normalizeSemanticText(existing)) > 0.85) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    finalFacts.add(text);
                    normalizedSet.add(normalized);
                }
            }
        }

        return finalFacts;
    }

    private String formatFactWithTime(String content, FactNode fact) {
        if (fact == null) {
            return content;
        }

        StringBuilder builder = new StringBuilder(content);
        if (fact.getEventTime() != null) {
            builder.append(" [事件时间: ").append(formatRelativeTime(fact.getEventTime())).append("]");
        }
        if (fact.getObservedAt() != null) {
            builder.append(" [记录于: ").append(formatRelativeTime(fact.getObservedAt())).append("]");
        }
        return builder.toString();
    }

    private String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "未知";
        }

        LocalDateTime now = LocalDateTime.now();
        long days = Duration.between(dateTime, now).toDays();

        if (days == 0) {
            return "今天";
        }
        if (days == 1) {
            return "昨天";
        }
        if (days > 1 && days < 7) {
            return days + "天前";
        }
        if (days >= 7 && days < 30) {
            return (days / 7) + "周前";
        }
        if (days >= 30 && days < 365) {
            return (days / 30) + "个月前";
        }
        if (days >= 365) {
            return (days / 365) + "年前";
        }

        long futureDays = Math.abs(days);
        if (futureDays == 1) {
            return "明天";
        }
        if (futureDays < 7) {
            return futureDays + "天后";
        }
        if (futureDays < 30) {
            return (futureDays / 7) + "周后";
        }
        if (futureDays < 365) {
            return (futureDays / 30) + "个月后";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private boolean isIdentityQuery(String message, List<String> entities) {
        if (message == null) return false;

        // 1. Check direct hardcoded patterns (fast path)
        if (message.contains("我是谁") || message.contains("我的名字") || message.contains("叫什么") || message.contains("关于我")) {
            return true;
        }

        // 2. Check extracted entities for identity intent
        if (entities != null) {
            for (String entity : entities) {
                if (entity.equals("名字") || entity.equals("身份") || entity.equals("我是谁")) {
                    return true;
                }
            }
        }

        return false;
    }



    private List<String> extractEntities(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<String> entities = new java.util.ArrayList<>();

        // 1. 规则引擎：处理特殊意图
        if (message.contains("我是谁") || message.contains("我叫什么") || message.contains("关于我")) {
            entities.addAll(java.util.Arrays.asList("名字", "身份", "我是谁"));
        }

        try {
            // 2. 本地 NLP 提词库：提取核心名词、动词
            List<String> nlpKeywords = com.hankcs.hanlp.HanLP.extractKeyword(message, 5);
            if (nlpKeywords != null) {
                for (String kw : nlpKeywords) {
                    if (kw != null && !kw.trim().isEmpty() && !STOP_WORDS.contains(kw.toLowerCase())) {
                        if (!entities.contains(kw.trim())) {
                            entities.add(kw.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("HanLP entity extraction failed, falling back to regex split: {}", e.getMessage());
        }

        // Fallback or additional filtering if needed
        if (entities.isEmpty()) {
            log.debug("HanLP实体提取为空，使用回退逻辑处理: {}", message);
            // 先清理标点和空白
            String cleanMsg = message.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\u3000-\\u303F\\uFF00-\\uFFEF\\s]+", " ").trim();
            String[] words = cleanMsg.split("\\s+");

            for (String word : words) {
                if (word.length() >= 2 && word.length() <= 4 && !STOP_WORDS.contains(word.toLowerCase())) {
                    entities.add(word);
                }
            }

            // 对于没有空格的中文文本，从文本中剔除停用字符后提取有意义的连续片段
            if (entities.isEmpty() && !message.contains(" ")) {
                // 将停用词替换为分隔符，然后提取剩余片段
                String processed = cleanMsg.replace(" ", "");
                for (String sw : STOP_WORDS) {
                    processed = processed.replace(sw, "|");
                }
                // 提取分割后的有意义片段
                String[] segments = processed.split("\\|+");
                for (String seg : segments) {
                    String trimmed = seg.trim();
                    if (trimmed.length() >= 2 && trimmed.length() <= 4) {
                        entities.add(trimmed);
                    } else if (trimmed.length() > 4) {
                        // 对较长的片段，取前2和前3字符作为实体
                        entities.add(trimmed.substring(0, 2));
                        entities.add(trimmed.substring(0, Math.min(3, trimmed.length())));
                    }
                }
            }

            // 只对非常短的文本（<=6字）启用 N-gram，避免长句产生垃圾
            if (entities.isEmpty() && cleanMsg.replace(" ", "").length() <= 6) {
                String shortMsg = cleanMsg.replace(" ", "");
                for (int i = 0; i < shortMsg.length() - 1; i++) {
                    String chunk2 = shortMsg.substring(i, i + 2);
                    if (!STOP_WORDS.contains(chunk2)) entities.add(chunk2);
                }
            }

            log.debug("回退实体提取结果: {}", entities);
        }

        return entities.stream().distinct().limit(10).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 递归扁平化 JSON 数组，处理 LLM 可能返回嵌套数组（如 [["今天", "干什么"]]）的情况
     */
    private void flattenJsonArray(com.fasterxml.jackson.databind.JsonNode node, List<String> result) {
        if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode child : node) {
                if (child.isTextual()) {
                    result.add(child.asText());
                } else if (child.isArray()) {
                    flattenJsonArray(child, result);
                } else if (child.isValueNode()) {
                    result.add(child.asText());
                }
            }
        }
    }

    private double calculateGain(List<String> entities, List<FactNode> activatedFacts) {
        if (entities.isEmpty() || activatedFacts.isEmpty()) {
            return 0.0;
        }

        java.util.Set<String> matchedEntities = new java.util.HashSet<>();
        double totalImportance = 0.0;

        for (FactNode fact : activatedFacts) {
            String content = fact.getContent().toLowerCase();
            boolean factMatched = false;
            for (String entity : entities) {
                if (content.contains(entity.toLowerCase())) {
                    matchedEntities.add(entity.toLowerCase());
                    factMatched = true;
                }
            }
            if (factMatched) {
                totalImportance += fact.getImportance();
            }
        }

        double entityRatio = (double) matchedEntities.size() / entities.size();
        double avgImportance = totalImportance / activatedFacts.size();

        return Math.min(1.0, entityRatio * 0.6 + avgImportance * 0.4);
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
        if (userId != null && !userId.isBlank()) {
            userRepository.findByName(userId.trim()).ifPresent(users::add);
        } else {
            userRepository.findAll().forEach(users::add);
        }

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
                TopicAggregate topic = topics.computeIfAbsent(topicKey,
                        key -> new TopicAggregate(key, displayTopicName(key)));
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
                    links.add(buildLink(topic.id, "fact_" + fact.getId(), "BELONGS_TO_TOPIC", fact.getImportance(),
                            fact.getObservedAt()));
                } else {
                    links.add(buildLink("user_" + user.getName(), "fact_" + fact.getId(), "HAS_FACT",
                            fact.getImportance(), fact.getObservedAt()));
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
        node.put("label",
                user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getName());
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
        double activityScore = fact.getActivityScore() > 0 ? fact.getActivityScore()
                : calculateActivityScore(
                fact.getLastActivatedAt() != null ? fact.getLastActivatedAt() : fact.getObservedAt(), now);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "fact_" + fact.getId());
        node.put("label", fact.getContent());
        node.put("shortLabel", shortenLabel(fact.getContent(), 18));
        node.put("type", "Fact");
        node.put("subType",
                fact.getSubType() != null && !fact.getSubType().isBlank() ? fact.getSubType() : inferFactSubType(fact));
        node.put("importance", round(fact.getImportance()));
        node.put("confidence", round(fact.getConfidence() > 0 ? fact.getConfidence()
                : Math.min(0.95, Math.max(0.55, fact.getImportance() + 0.08))));
        node.put("activityScore", round(activityScore));
        node.put("cluster",
                fact.getClusterKey() != null && !fact.getClusterKey().isBlank() ? fact.getClusterKey() : topicKey);
        node.put("orbitLevel", orbitLevel > 0 ? orbitLevel : calculateOrbitLevel(fact.getImportance(), activityScore));
        node.put("createdAt", formatDateTime(fact.getObservedAt()));
        node.put("observedAt", formatDateTime(fact.getObservedAt()));
        node.put("lastActivatedAt",
                formatDateTime(fact.getLastActivatedAt() != null ? fact.getLastActivatedAt() : fact.getObservedAt()));
        node.put("status", fact.getStatus() != null && !fact.getStatus().isBlank() ? fact.getStatus()
                : (activityScore >= 0.75 ? "active" : activityScore >= 0.38 ? "stable" : "cool"));
        node.put("version", fact.getVersion());
        node.put("supersedesFactId", fact.getSupersedesFactId());
        node.put("contradictsFactId", fact.getContradictsFactId());
        node.put("classificationSource", fact.getClassificationSource());
        node.put("originalMessage", fact.getOriginalMessage());
        return node;
    }

    private Map<String, Object> buildLink(String source, String target, String type, double weight,
                                          LocalDateTime lastActivatedAt) {
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
                .collect(Collectors.groupingBy(ctx -> ctx.userNodeId + "::" + ctx.topicKey, LinkedHashMap::new,
                        Collectors.toList()));

        for (List<FactContext> group : grouped.values()) {
            group.sort(Comparator.comparing(ctx -> ctx.fact.getObservedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder())));
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < Math.min(group.size(), i + 3); j++) {
                    FactContext older = group.get(i);
                    FactContext newer = group.get(j);
                    RelationshipGuess guess = guessFactRelationship(older.fact, newer.fact);
                    if (guess == null) {
                        continue;
                    }
                    String source = guess.type.equals("SUPERSEDES") ? "fact_" + newer.fact.getId()
                            : "fact_" + older.fact.getId();
                    String target = guess.type.equals("SUPERSEDES") ? "fact_" + older.fact.getId()
                            : "fact_" + newer.fact.getId();
                    LocalDateTime activatedAt = newer.fact.getObservedAt() != null ? newer.fact.getObservedAt()
                            : older.fact.getObservedAt();
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

        List<Map<String, Object>> related = inferred.stream().filter(link -> "RELATED_TO".equals(link.get("type")))
                .collect(Collectors.toList());
        List<Map<String, Object>> supersedes = inferred.stream().filter(link -> "SUPERSEDES".equals(link.get("type")))
                .collect(Collectors.toList());
        List<Map<String, Object>> contradicts = inferred.stream().filter(link -> "CONTRADICTS".equals(link.get("type")))
                .collect(Collectors.toList());

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

        String cypher = """
                MATCH (a:Fact)-[r:RELATED_TO|SUPERSEDES|CONTRADICTS]->(b:Fact)
                WHERE id(a) IN $factIds AND id(b) IN $factIds
                RETURN id(a) AS sourceId,
                       id(b) AS targetId,
                       type(r) AS relationType,
                       r.weight AS weight,
                       r.lastActivatedAt AS lastActivatedAt
                """;

        return neo4jClient.query(cypher)
                .bind(factIds).to("factIds")
                .fetch().all().stream()
                .map(row -> {
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("source", "fact_" + castLong(row.get("sourceId")));
                    link.put("target", "fact_" + castLong(row.get("targetId")));
                    link.put("type", row.get("relationType"));
                    link.put("weight", row.get("weight") != null ? row.get("weight") : 0.5d);
                    link.put("lastActivatedAt", formatDateTime(castLocalDateTime(row.get("lastActivatedAt"))));
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
            row.put("lastActivatedAt", castLocalDateTime(link.get("lastActivatedAt")));
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

    private LocalDateTime castLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(raw).toLocalDateTime();
        } catch (Exception ignored) {
        }
        log.warn("Skipping invalid relation timestamp: {}", raw);
        return null;
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
        if (similarity >= 0.55) {
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

    private FactNode buildNewFactNode(String factText, String normalized, String clusterKey, String subType,
                                      double semanticConfidence, String source, LocalDateTime now, String originalMessage,
                                      com.lingshu.ai.core.dto.EmotionAnalysis emotion) {
        double baseImportance = 0.8;
        if (emotion != null && emotion.getIntensity() != null) {
            double intensityBoost = emotion.getIntensity() * 0.2;
            baseImportance = Math.min(1.0, baseImportance + intensityBoost);
        }

        String toneStr = "neutral";
        if (emotion != null && emotion.getEmotion() != null) {
            toneStr = emotion.getEmotion();
        } else {
            toneStr = inferEmotionalToneFromKeywords(originalMessage);
        }

        return FactNode.builder()
                .content(factText)
                .category(clusterKey)
                .normalizedContent(normalized)
                .subType(subType)
                .clusterKey(clusterKey)
                .observedAt(now)
                .lastActivatedAt(now)
                .importance(baseImportance)
                .confidence(round(Math.max(0.72, semanticConfidence)))
                .classificationSource(source)
                .activityScore(calculateActivityScore(now, now))
                .status("active")
                .decayRate(0.015)
                .ttlDays(180)
                .version(1)
                .originalMessage(originalMessage)
                .emotionalTone(toneStr)
                .involvedEntities(java.util.Optional.ofNullable(extractEntities(factText)).filter(l -> !l.isEmpty())
                        .map(java.util.HashSet::new).orElse(null))
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
        if (containsAny(content, "消化不良", "感冒", "头疼", "过敏", "生病", "健康", "身体")) {
            return "health";
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
            case "health" -> "身体状况";
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
        if (containsAny(content, "消化不良", "感冒", "头疼", "过敏", "生病", "健康", "身体")) {
            return "HealthState";
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
                .map(token -> token.substring(0, 1).toUpperCase(Locale.ROOT)
                        + token.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining());
        return SUB_TYPES.contains(normalized) ? normalized : "Memory";
    }

    private SemanticProfile classifyFactSemantics(String factText, UserNode user) {
        SemanticProfile historyProfile = resolveByHistory(factText, user);
        SemanticProfile ruleProfile = resolveByRules(factText);

        if (historyProfile != null && ruleProfile != null) {
            if (historyProfile.topicKey.equals(ruleProfile.topicKey)
                    || historyProfile.subType.equals(ruleProfile.subType)) {
                return new SemanticProfile(
                        historyProfile.topicKey,
                        historyProfile.subType,
                        Math.max(historyProfile.confidence, ruleProfile.confidence),
                        "history+rule");
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

        FactNode bestMatch = null;
        double bestScore = 0.0;

        try {
            systemLogService.embeddingStart(factText.length(), "MEMORY");
            dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(factText).content();
            systemLogService.embeddingEnd("MEMORY");

            systemLogService.dbStart("pgvector_query", "embeddingStore", "MEMORY");
            dev.langchain4j.store.embedding.EmbeddingSearchRequest searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest
                    .builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.72)
                    .build();
            dev.langchain4j.store.embedding.EmbeddingSearchResult<dev.langchain4j.data.segment.TextSegment> searchResult = embeddingStore
                    .search(searchRequest);
            systemLogService.dbEnd("pgvector_query", "MEMORY");

            List<Long> userFactIds = user.getFacts().stream()
                    .filter(f -> f.getId() != null)
                    .map(FactNode::getId)
                    .collect(Collectors.toList());

            for (dev.langchain4j.store.embedding.EmbeddingMatch<dev.langchain4j.data.segment.TextSegment> match : searchResult
                    .matches()) {
                String factIdStr = match.embedded().metadata().getString("fact_id");
                if (factIdStr == null)
                    continue;
                Long factId = Long.parseLong(factIdStr);
                if (!userFactIds.contains(factId))
                    continue;

                FactNode existingFact = user.getFacts().stream().filter(f -> f.getId().equals(factId)).findFirst()
                        .orElse(null);
                if (existingFact == null)
                    continue;

                bestMatch = existingFact;
                bestScore = match.score();
                break;
            }
        } catch (Exception e) {
            log.warn("Vector search failed in resolveByHistory, falling back to rule-based: {}", e.getMessage());
            String normalized = normalizeSemanticText(factText);
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
        }

        if (bestMatch == null || bestScore < 0.72) {
            return null;
        }

        return new SemanticProfile(
                deriveTopicKey(bestMatch),
                inferFactSubType(bestMatch),
                round(Math.min(0.95, 0.62 + bestScore * 0.3)),
                "history");
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
            String modelName = dynamicMemoryModel.getModelName();
            systemLogService.llmStart("fact-semantic-classifier", modelName, "FACT");
            CompletableFuture<FactSemanticClassification> future = CompletableFuture.supplyAsync(
                    () -> factSemanticClassifier.classify(factText, buildHistorySummary(user)));
            FactSemanticClassification classification = future.get(SEMANTIC_CLASSIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            systemLogService.llmEnd(0, "FACT");
            if (classification == null) {
                return null;
            }
            return new SemanticProfile(
                    normalizeTopicKey(classification.getTopicKey()),
                    normalizeSubType(classification.getSubType()),
                    round(Math.max(0.6, Math.min(0.95, classification.getConfidence()))),
                    "llm");
        } catch (TimeoutException e) {
            systemLogService.llmError("fact-semantic-classifier timeout", "FACT");
            log.warn("Fact semantic classification timeout after {}s; fallback to non-LLM",
                    SEMANTIC_CLASSIFY_TIMEOUT_SECONDS);
            return null;
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
                .sorted(Comparator.comparing(FactNode::getLastActivatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
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
            if (lastActivatedAt == null
                    || (fact.getObservedAt() != null && fact.getObservedAt().isAfter(lastActivatedAt))) {
                lastActivatedAt = fact.getObservedAt();
            }
            importance = round(totalImportance / factCount);
            this.activityScore = round(totalActivity / factCount);
            orbitLevel = calculateOrbitLevel(importance, this.activityScore);
        }
    }

    private static final class GraphRetrievalHit {
        private final FactNode fact;
        private final int hop;
        private final double relationWeight;

        private GraphRetrievalHit(FactNode fact, int hop, double relationWeight) {
            this.fact = fact;
            this.hop = hop;
            this.relationWeight = relationWeight;
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

    private static final class RelationDecision {
        private final FactNode match;
        private final String type;

        private RelationDecision(FactNode match, String type) {
            this.match = match;
            this.type = type;
        }
    }

    @Override
    public void deleteFact(Long factId) {
        String factContent = factRepository.findById(factId)
                .map(f -> f.getContent())
                .orElse("未知内容");

        log.info("Cognitive cleanup: Removing fact node [{}] (ID: #{})", factContent, factId);
        systemLogService.info("认知清理: 删除事实节点 ["
                + (factContent.length() > 30 ? factContent.substring(0, 30) + "..." : factContent) + "]", "MEMORY");

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
            log.warn("Semantic cleanup skipped or failed for factId {}: Vector store may not support automated removal",
                    factId);
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
            LocalDateTime activatedAt = fact.getLastActivatedAt() != null ? fact.getLastActivatedAt()
                    : fact.getObservedAt();
            double computedActivity = calculateActivityScore(activatedAt, now);
            fact.setActivityScore(round(computedActivity));
            if (fact.getNormalizedContent() == null || fact.getNormalizedContent().isBlank()) {
                fact.setNormalizedContent(normalizedFactContent(fact));
            }
            if (fact.getClusterKey() == null || fact.getClusterKey().isBlank()
                    || "memory".equals(normalizeTopicKey(fact.getClusterKey()))) {
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
        lastMaintenanceSummary = Map.copyOf(summary);
        systemLogService.info(String.format("记忆维护完成: processed=%d archived=%d cooled=%d reactivated=%d", processed,
                archived, cooled, reactivated), "MEMORY");
        return summary;
    }

    @Override
    public Object getMemoryMaintenanceSummary() {
        Map<String, Object> summary = lastMaintenanceSummary;
        if (summary.isEmpty()) {
            Map<String, Object> emptySummary = new LinkedHashMap<>();
            emptySummary.put("processed", 0);
            emptySummary.put("archived", 0);
            emptySummary.put("cooled", 0);
            emptySummary.put("reactivated", 0);
            emptySummary.put("generatedAt", null);
            return emptySummary;
        }
        return new LinkedHashMap<>(summary);
    }

    @Override
    public void updateFactClassification(Long factId, String clusterKey, String subType) {
        factRepository.findById(factId).ifPresent(fact -> {
            fact.setClusterKey(clusterKey);
            fact.setCategory(clusterKey); // keep backward compatibility
            fact.setSubType(subType);
            fact.setClassificationSource("manual");
            factRepository.save(fact);
            systemLogService.info("手动更新事实分类: ID=" + factId + ", Topic=" + clusterKey + ", SubType=" + subType,
                    "MEMORY");
        });
    }

    private void recordRetrievalEvent(com.lingshu.ai.core.dto.MemoryRetrievalEvent event) {
        if (event == null) {
            return;
        }
        synchronized (retrievalEventsLock) {
            recentRetrievalEvents.addLast(event);
            while (recentRetrievalEvents.size() > RECENT_RETRIEVAL_EVENT_LIMIT) {
                recentRetrievalEvents.pollFirst();
            }
        }
    }

    @Override
    public java.util.List<com.lingshu.ai.core.dto.MemoryRetrievalEvent> getRecentRetrievalEvents(String userId) {
        List<com.lingshu.ai.core.dto.MemoryRetrievalEvent> snapshot = snapshotRecentRetrievalEvents();
        if (userId == null || userId.isBlank()) {
            return snapshot;
        }
        return snapshot.stream()
                .filter(e -> userId.equals(e.getUserId()))
                .collect(Collectors.toList());
    }

    private List<com.lingshu.ai.core.dto.MemoryRetrievalEvent> snapshotRecentRetrievalEvents() {
        synchronized (retrievalEventsLock) {
            return new ArrayList<>(recentRetrievalEvents);
        }
    }

    @Override
    public Object getMemoryGovernanceList(int page, int size, String status, String userId) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "lastActivatedAt"));
        if (userId != null && !userId.isBlank()) {
            UserNode user = userRepository.findByName(userId.trim()).orElse(null);
            List<FactNode> facts = user == null || user.getFacts() == null
                    ? List.of()
                    : user.getFacts().stream()
                            .filter(fact -> status == null || status.isBlank() || "all".equalsIgnoreCase(status)
                                    || status.equalsIgnoreCase(fact.getStatus()))
                            .sorted(Comparator.comparing(FactNode::getLastActivatedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
            int start = Math.min(page * size, facts.size());
            int end = Math.min(start + size, facts.size());
            return new org.springframework.data.domain.PageImpl<>(facts.subList(start, end), pageable, facts.size());
        }
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return factRepository.findAll(pageable);
        } else {
            return factRepository.findAllByStatus(status, pageable);
        }
    }

    @Override
    public void archiveFact(Long factId) {
        factRepository.findById(factId).ifPresent(fact -> {
            fact.setStatus("archived");
            fact.setActivityScore(0.0);
            factRepository.save(fact);
            systemLogService.info("手动归档事实: ID=" + factId, "MEMORY");
        });
    }

    @Override
    public void restoreFact(Long factId) {
        factRepository.findById(factId).ifPresent(fact -> {
            fact.setStatus("active");
            fact.setActivityScore(0.85);
            fact.setLastActivatedAt(LocalDateTime.now());
            factRepository.save(fact);
            systemLogService.info("手动恢复事实: ID=" + factId, "MEMORY");
        });
    }
    @Override
    @Async("taskExecutor")
    public void rebuildAllEmbeddings() {
        log.info("Starting global embedding rebuild process for migration...");
        systemLogService.info("开始全局向量索引重建流程 (元数据补全)...", "MEMORY");

        java.util.concurrent.atomic.AtomicInteger totalCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        userRepository.findAll().forEach(user -> {
            String userId = user.getName();
            if (user.getFacts() != null) {
                user.getFacts().stream()
                        .filter(f -> !"archived".equals(f.getStatus()))
                        .forEach(fact -> {
                            totalCount.incrementAndGet();
                            try {
                                // 1. 先根据 fact_id 清除旧向量，确保幂等
                                if (fact.getId() != null) {
                                    embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("fact_id").isEqualTo(fact.getId().toString()));
                                }

                                // 2. 重新写入带元数据的向量
                                ExtractionResult.ExtractedFact dummy = new ExtractionResult.ExtractedFact();
                                dummy.setContent(fact.getContent());
                                dummy.setVolatile("volatile".equals(fact.getStatus()));

                                storeFactEmbedding(userId, fact, fact.getOriginalMessage(), null, dummy);
                                successCount.incrementAndGet();
                            } catch (Exception e) {
                                log.error("Failed to rebuild embedding for fact {}: {}", fact.getId(), e.getMessage());
                            }
                        });
            }
        });

        systemLogService.success(String.format("全局向量索引重建完成: 共处理 %d 条，成功 %d 条", totalCount.get(), successCount.get()), "MEMORY");
    }
}
