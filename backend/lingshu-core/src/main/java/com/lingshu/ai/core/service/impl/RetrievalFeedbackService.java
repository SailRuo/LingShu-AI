package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.AdaptiveMemoryScorer;
import com.lingshu.ai.core.service.MemoryStateUpdater;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.FactNode;
import com.lingshu.ai.infrastructure.entity.MemoryStateRecord;
import com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord;
import com.lingshu.ai.infrastructure.repository.FactRepository;
import com.lingshu.ai.infrastructure.repository.MemoryStateRecordRepository;
import com.lingshu.ai.infrastructure.repository.RetrievalFeedbackRecordRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RetrievalFeedbackService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RetrievalFeedbackService.class);
    private static final double RELATION_FEEDBACK_CONFIDENCE_THRESHOLD = 0.80;
    private static final double RELATION_UNSUPPORTED_CONFIDENCE_THRESHOLD = 0.90;
    private static final double NEW_RELATION_BASE_WEIGHT = 0.50;
    private static final String METRIC_STATE_WRITE_ATTEMPTS = "lingshu.memory.state.write.attempts";
    private static final String METRIC_STATE_WRITE_UPDATES = "lingshu.memory.state.write.updates";
    private static final String METRIC_STATE_WRITE_SKIPPED = "lingshu.memory.state.write.skipped";
    private static final String METRIC_STATE_WRITE_ERRORS = "lingshu.memory.state.write.errors";
    private static final String METRIC_STATE_UNCERTAINTY = "lingshu.memory.state.uncertainty";

    private final RetrievalContextSnapshotStore snapshotStore;
    private final RetrievalFeedbackAnalyzer retrievalFeedbackAnalyzer;
    private final SystemLogService systemLogService;
    private final RetrievalFeedbackRecordRepository retrievalFeedbackRecordRepository;
    private final FactRepository factRepository;
    private final AdaptiveMemoryScorer adaptiveMemoryScorer;
    private final MemoryStateRecordRepository memoryStateRecordRepository;
    private final MemoryStateUpdater memoryStateUpdater;
    private final EmbeddingModel embeddingModel;
    private final boolean memoryStateWriteEnabled;
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired
    public RetrievalFeedbackService(RetrievalContextSnapshotStore snapshotStore,
                                    RetrievalFeedbackAnalyzer retrievalFeedbackAnalyzer,
                                    SystemLogService systemLogService,
                                    RetrievalFeedbackRecordRepository retrievalFeedbackRecordRepository,
                                    FactRepository factRepository,
                                    AdaptiveMemoryScorer adaptiveMemoryScorer,
                                    MemoryStateRecordRepository memoryStateRecordRepository,
                                    MemoryStateUpdater memoryStateUpdater,
                                    EmbeddingModel embeddingModel,
                                    @Value("${feature.memory.state.write.enabled:false}") boolean memoryStateWriteEnabled) {
        this.snapshotStore = snapshotStore;
        this.retrievalFeedbackAnalyzer = retrievalFeedbackAnalyzer;
        this.systemLogService = systemLogService;
        this.retrievalFeedbackRecordRepository = retrievalFeedbackRecordRepository;
        this.factRepository = factRepository;
        this.adaptiveMemoryScorer = adaptiveMemoryScorer;
        this.memoryStateRecordRepository = memoryStateRecordRepository;
        this.memoryStateUpdater = memoryStateUpdater;
        this.embeddingModel = embeddingModel;
        this.memoryStateWriteEnabled = memoryStateWriteEnabled;
    }

    RetrievalFeedbackService(RetrievalContextSnapshotStore snapshotStore,
                             RetrievalFeedbackAnalyzer retrievalFeedbackAnalyzer,
                             SystemLogService systemLogService) {
        this(snapshotStore, retrievalFeedbackAnalyzer, systemLogService, null, null, null, null, null, null, false);
    }

    RetrievalFeedbackService(RetrievalContextSnapshotStore snapshotStore,
                             RetrievalFeedbackAnalyzer retrievalFeedbackAnalyzer,
                             SystemLogService systemLogService,
                             RetrievalFeedbackRecordRepository retrievalFeedbackRecordRepository) {
        this(snapshotStore, retrievalFeedbackAnalyzer, systemLogService, retrievalFeedbackRecordRepository, null, null, null, null, null, false);
    }

    RetrievalFeedbackService(RetrievalContextSnapshotStore snapshotStore,
                             RetrievalFeedbackAnalyzer retrievalFeedbackAnalyzer,
                             SystemLogService systemLogService,
                             RetrievalFeedbackRecordRepository retrievalFeedbackRecordRepository,
                             FactRepository factRepository,
                             AdaptiveMemoryScorer adaptiveMemoryScorer) {
        this(snapshotStore, retrievalFeedbackAnalyzer, systemLogService, retrievalFeedbackRecordRepository, factRepository, adaptiveMemoryScorer, null, null, null, false);
    }

    public void analyzeTurn(Long turnId, String assistantResponse) {
        if (turnId == null) {
            return;
        }

        RetrievalContextSnapshot snapshot = snapshotStore.findByTurnId(turnId).orElse(null);
        if (snapshot == null) {
            systemLogService.debug("检索反馈跳过: 未找到快照 turnId=" + turnId, "MEMORY");
            return;
        }

        boolean shouldRemoveSnapshot = false;
        try {
            RetrievalFeedbackResult result = retrievalFeedbackAnalyzer.analyze(
                    snapshot,
                    assistantResponse == null ? "" : assistantResponse
            );
            FeedbackPersistenceState persistenceState = persistFactFeedbackRecords(snapshot, result);
            AdaptiveUpdateSummary adaptiveUpdateSummary = AdaptiveUpdateSummary.skipped();
            if (persistenceState == FeedbackPersistenceState.NEWLY_PERSISTED) {
                adaptiveUpdateSummary = applyAdaptiveUpdatesSafely(snapshot, result);
            } else if (persistenceState == FeedbackPersistenceState.ALREADY_RECORDED) {
                systemLogService.debug("检索反馈已存在，跳过重复自适应更新 turnId=" + turnId, "MEMORY");
            }
            logAnalysis(turnId, snapshot, result);
            logAdaptiveSummary(turnId, result, adaptiveUpdateSummary, persistenceState);
            shouldRemoveSnapshot = true;
        } catch (RetrievalFeedbackPersistenceException exception) {
            log.warn("检索反馈持久化失败 turnId={}: {}", turnId, exception.getCause().getMessage(), exception.getCause());
            systemLogService.warn("检索反馈持久化失败 turnId=" + turnId + ": " + exception.getCause().getMessage(), "MEMORY");
        } catch (Exception exception) {
            shouldRemoveSnapshot = true;
            log.warn("检索反馈分析失败 turnId={}: {}", turnId, exception.getMessage(), exception);
            systemLogService.warn("检索反馈分析失败 turnId=" + turnId + ": " + exception.getMessage(), "MEMORY");
        } finally {
            if (shouldRemoveSnapshot) {
                snapshotStore.remove(turnId);
            }
        }
    }

    private FeedbackPersistenceState persistFactFeedbackRecords(RetrievalContextSnapshot snapshot,
                                                               RetrievalFeedbackResult result) {
        if (retrievalFeedbackRecordRepository == null || result == null || result.getFactFeedback().isEmpty()) {
            return FeedbackPersistenceState.NEWLY_PERSISTED;
        }

        List<RetrievalFeedbackRecord> records = result.getFactFeedback().stream()
                .filter(feedback -> feedback != null && feedback.getFactId() != null)
                .map(feedback -> RetrievalFeedbackRecord.builder()
                        .turnId(snapshot.getTurnId())
                        .sessionId(snapshot.getSessionId())
                        .userId(snapshot.getUserId())
                        .factId(feedback.getFactId())
                        .query(snapshot.getQuery())
                        .routingDecision(snapshot.getRoutingDecision())
                        .valid(feedback.getValid())
                        .confidence(feedback.getConfidence())
                        .reason(feedback.getReason())
                        .build())
                .toList();

        if (records.isEmpty()) {
            return FeedbackPersistenceState.NEWLY_PERSISTED;
        }

        List<Long> factIds = records.stream()
                .map(RetrievalFeedbackRecord::getFactId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<RetrievalFeedbackRecord> existingRecords;
        try {
            existingRecords = retrievalFeedbackRecordRepository.findByTurnIdAndFactIdIn(snapshot.getTurnId(), factIds);
        } catch (Exception exception) {
            throw new RetrievalFeedbackPersistenceException(exception);
        }

        if (!existingRecords.isEmpty()) {
            return FeedbackPersistenceState.ALREADY_RECORDED;
        }

        try {
            retrievalFeedbackRecordRepository.saveAll(records);
            return FeedbackPersistenceState.NEWLY_PERSISTED;
        } catch (DataIntegrityViolationException conflict) {
            // Compensate concurrent insert races: if all rows now exist, treat as idempotent success.
            try {
                List<RetrievalFeedbackRecord> reloaded = retrievalFeedbackRecordRepository
                        .findByTurnIdAndFactIdIn(snapshot.getTurnId(), factIds);
                long covered = reloaded.stream()
                        .map(RetrievalFeedbackRecord::getFactId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                if (covered >= factIds.size()) {
                    systemLogService.debug("检索反馈并发写冲突已补偿为幂等成功 turnId=" + snapshot.getTurnId(), "MEMORY");
                    return FeedbackPersistenceState.ALREADY_RECORDED;
                }
            } catch (Exception reloadFailure) {
                throw new RetrievalFeedbackPersistenceException(reloadFailure);
            }
            throw new RetrievalFeedbackPersistenceException(conflict);
        } catch (Exception exception) {
            throw new RetrievalFeedbackPersistenceException(exception);
        }
    }

    enum FeedbackPersistenceState {
        NEWLY_PERSISTED,
        ALREADY_RECORDED
    }

    private static final class RetrievalFeedbackPersistenceException extends RuntimeException {

        private RetrievalFeedbackPersistenceException(Exception cause) {
            super(cause);
        }
    }

    private AdaptiveUpdateSummary applyAdaptiveUpdatesSafely(RetrievalContextSnapshot snapshot,
                                                             RetrievalFeedbackResult result) {
        try {
            return applyAdaptiveUpdates(snapshot, result);
        } catch (Exception exception) {
            throw new RetrievalFeedbackPersistenceException(exception);
        }
    }

    AdaptiveUpdateSummary applyAdaptiveUpdates(RetrievalContextSnapshot snapshot, RetrievalFeedbackResult result) {
        if (snapshot == null || result == null) {
            return AdaptiveUpdateSummary.skipped();
        }

        Map<Long, RetrievalFeedbackResult.FactFeedback> feedbackIndex = indexFactFeedback(result);
        if (feedbackIndex.isEmpty()) {
            return AdaptiveUpdateSummary.skipped();
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        int factUpdates = 0;
        int relationUpdates = 0;
        if (factRepository != null && adaptiveMemoryScorer != null) {
            factUpdates = applyFactAdaptiveUpdates(snapshot, feedbackIndex, updatedAt);
            relationUpdates = applyRelatedAdaptiveUpdates(snapshot, result, updatedAt);
        }
        int memoryStateUpdates = applyMemoryStateUpdates(snapshot, feedbackIndex, updatedAt);
        return new AdaptiveUpdateSummary(factUpdates, relationUpdates, memoryStateUpdates);
    }

    private int applyFactAdaptiveUpdates(RetrievalContextSnapshot snapshot,
                                         Map<Long, RetrievalFeedbackResult.FactFeedback> feedbackIndex,
                                         LocalDateTime updatedAt) {
        int factUpdates = 0;
        for (RetrievalFactCandidate candidate : snapshot.getContextFacts()) {
            if (candidate == null || candidate.getFactId() == null) {
                continue;
            }

            RetrievalFeedbackResult.FactFeedback feedback = feedbackIndex.get(candidate.getFactId());
            if (feedback == null) {
                continue;
            }

            FactNode fact = factRepository.findById(candidate.getFactId()).orElse(null);
            if (fact == null) {
                continue;
            }

            AdaptiveMemoryScorer.FactDelta delta = adaptiveMemoryScorer.scoreFact(
                    fact.getImportance(),
                    fact.getConfidence(),
                    feedback
            );
            if (hasNoScoreChange(fact, delta)) {
                continue;
            }

            factRepository.updateFactAdaptiveScores(
                    candidate.getFactId(),
                    delta.newImportance(),
                    delta.newConfidence(),
                    updatedAt
            );
            factUpdates++;
        }
        return factUpdates;
    }

    private int applyRelatedAdaptiveUpdates(RetrievalContextSnapshot snapshot,
                                            RetrievalFeedbackResult result,
                                            LocalDateTime updatedAt) {
        List<Long> contextFactIds = snapshot.getContextFacts().stream()
                .filter(Objects::nonNull)
                .map(RetrievalFactCandidate::getFactId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (contextFactIds.size() < 2) {
            return 0;
        }

        List<Long> supportedIds = collectHighConfidenceSupportedFactIds(result).stream()
                .filter(contextFactIds::contains)
                .toList();
        int relationUpdates = 0;
        if (supportedIds.size() >= 2) {
            for (int i = 0; i < supportedIds.size(); i++) {
                for (int j = i + 1; j < supportedIds.size(); j++) {
                    relationUpdates += updateRelatedRelationWeight(supportedIds.get(i), supportedIds.get(j), 1, 0, updatedAt);
                }
            }
        }
        List<Long> unsupportedIds = collectHighConfidenceUnsupportedFactIds(result).stream()
                .filter(contextFactIds::contains)
                .filter(id -> !supportedIds.contains(id))
                .toList();
        if (unsupportedIds.size() >= 2) {
            for (int i = 0; i < unsupportedIds.size(); i++) {
                for (int j = i + 1; j < unsupportedIds.size(); j++) {
                    relationUpdates += updateRelatedRelationWeight(unsupportedIds.get(i), unsupportedIds.get(j), 0, 1, updatedAt);
                }
            }
        }
        return relationUpdates;
    }

    List<Long> collectHighConfidenceSupportedFactIds(RetrievalFeedbackResult result) {
        if (result == null) {
            return List.of();
        }

        return result.getFactFeedback().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getFactId() != null)
                .filter(item -> Boolean.TRUE.equals(item.getValid()))
                .filter(item -> item.getConfidence() != null
                        && item.getConfidence() >= RELATION_FEEDBACK_CONFIDENCE_THRESHOLD)
                .map(RetrievalFeedbackResult.FactFeedback::getFactId)
                .distinct()
                .sorted()
                .toList();
    }

    List<Long> collectHighConfidenceUnsupportedFactIds(RetrievalFeedbackResult result) {
        if (result == null) {
            return List.of();
        }

        return result.getFactFeedback().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getFactId() != null)
                .filter(item -> Boolean.FALSE.equals(item.getValid()))
                .filter(item -> item.getConfidence() != null
                        && item.getConfidence() >= RELATION_UNSUPPORTED_CONFIDENCE_THRESHOLD)
                .map(RetrievalFeedbackResult.FactFeedback::getFactId)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Long, RetrievalFeedbackResult.FactFeedback> indexFactFeedback(RetrievalFeedbackResult result) {
        return result.getFactFeedback().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getFactId() != null)
                .collect(Collectors.toMap(
                        RetrievalFeedbackResult.FactFeedback::getFactId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private int updateRelatedRelationWeight(Long sourceId,
                                            Long targetId,
                                            long supportedCooccurrence,
                                            long unsupportedCooccurrence,
                                            LocalDateTime updatedAt) {
        Long canonicalSource = Math.min(sourceId, targetId);
        Long canonicalTarget = Math.max(sourceId, targetId);
        Double currentWeight = factRepository.findRelatedRelationWeight(canonicalSource, canonicalTarget);
        if (unsupportedCooccurrence > 0 && currentWeight == null) {
            return 0;
        }

        double baselineWeight = currentWeight == null ? NEW_RELATION_BASE_WEIGHT : currentWeight;
        double nextWeight = adaptiveMemoryScorer.scoreRelationWeight(
                baselineWeight,
                supportedCooccurrence,
                unsupportedCooccurrence
        );
        if (currentWeight != null && Double.compare(currentWeight, nextWeight) == 0) {
            return 0;
        }

        factRepository.updateRelatedRelationWeight(
                canonicalSource,
                canonicalTarget,
                nextWeight,
                updatedAt
        );
        return 1;
    }

    private boolean hasNoScoreChange(FactNode fact, AdaptiveMemoryScorer.FactDelta delta) {
        return Double.compare(fact.getImportance(), delta.newImportance()) == 0
                && Double.compare(fact.getConfidence(), delta.newConfidence()) == 0;
    }

    private int applyMemoryStateUpdates(RetrievalContextSnapshot snapshot,
                                        Map<Long, RetrievalFeedbackResult.FactFeedback> feedbackIndex,
                                        LocalDateTime updatedAt) {
        if (!memoryStateWriteEnabled || memoryStateRecordRepository == null || memoryStateUpdater == null || embeddingModel == null) {
            incrementMetric(METRIC_STATE_WRITE_SKIPPED);
            return 0;
        }
        if (snapshot == null || feedbackIndex.isEmpty() || snapshot.getQuery() == null || snapshot.getQuery().isBlank()) {
            incrementMetric(METRIC_STATE_WRITE_SKIPPED);
            return 0;
        }

        incrementMetric(METRIC_STATE_WRITE_ATTEMPTS);
        double[] queryVector = embedQueryVector(snapshot.getQuery());
        if (queryVector == null || queryVector.length == 0) {
            systemLogService.debug("阶段三状态写回跳过: query embedding 为空 turnId=" + snapshot.getTurnId(), "MEMORY");
            incrementMetric(METRIC_STATE_WRITE_SKIPPED);
            return 0;
        }

        int updates = 0;
        for (RetrievalFactCandidate candidate : snapshot.getContextFacts()) {
            if (candidate == null || candidate.getFactId() == null) {
                continue;
            }

            RetrievalFeedbackResult.FactFeedback feedback = feedbackIndex.get(candidate.getFactId());
            if (feedback == null || feedback.getValid() == null) {
                continue;
            }

            MemoryStateRecord record = memoryStateRecordRepository.findByFactId(candidate.getFactId())
                    .orElseGet(() -> MemoryStateRecord.builder().factId(candidate.getFactId()).build());

            if (Boolean.TRUE.equals(feedback.getValid()) && isHighConfidence(feedback.getConfidence())) {
                MemoryStateUpdater.MemoryStateDelta delta = memoryStateUpdater.applySupported(
                        record.getTaskVector(),
                        record.getTaskUncertainty(),
                        record.getUpdateCount(),
                        queryVector,
                        feedback.getConfidence()
                );
                record.setTaskVector(delta.serializedVector());
                record.setTaskUncertainty(delta.uncertainty());
                record.setUpdateCount(delta.updateCount());
            } else if (Boolean.FALSE.equals(feedback.getValid())) {
                double uncertainty = memoryStateUpdater.applyUnsupportedUncertainty(
                        record.getTaskUncertainty(),
                        feedback.getConfidence()
                );
                record.setTaskUncertainty(uncertainty);
                if (record.getUpdateCount() == null) {
                    record.setUpdateCount(0);
                }
            } else {
                continue;
            }

            record.setLastUpdate(updatedAt);
            record.setStateVersion(record.getStateVersion() == null ? 1L : record.getStateVersion() + 1L);
            memoryStateRecordRepository.save(record);
            incrementMetric(METRIC_STATE_WRITE_UPDATES);
            recordSummary(METRIC_STATE_UNCERTAINTY, record.getTaskUncertainty());
            updates++;
        }
        return updates;
    }

    private boolean isHighConfidence(Double confidence) {
        return confidence != null && confidence >= RELATION_FEEDBACK_CONFIDENCE_THRESHOLD;
    }

    private double[] embedQueryVector(String query) {
        try {
            Embedding embedding = embeddingModel.embed(query).content();
            if (embedding == null || embedding.vector() == null) {
                return null;
            }
            float[] raw = embedding.vector();
            double[] converted = new double[raw.length];
            for (int i = 0; i < raw.length; i++) {
                converted[i] = raw[i];
            }
            return converted;
        } catch (Exception exception) {
            log.warn("阶段三状态写回 embedding 失败: {}", exception.getMessage());
            incrementMetric(METRIC_STATE_WRITE_ERRORS);
            return null;
        }
    }

    private void incrementMetric(String metricName) {
        if (meterRegistry != null) {
            meterRegistry.counter(metricName).increment();
        }
    }

    private void recordSummary(String metricName, Double value) {
        if (meterRegistry != null && value != null) {
            meterRegistry.summary(metricName).record(value);
        }
    }

    private void logAnalysis(Long turnId,
                             RetrievalContextSnapshot snapshot,
                             RetrievalFeedbackResult result) {
        int contextFactCount = snapshot.getContextFacts().size();
        int supportedCount = 0;
        int unsupportedCount = 0;
        int uncertainCount = 0;

        for (RetrievalFeedbackResult.FactFeedback feedback : result.getFactFeedback()) {
            if (feedback == null || feedback.getValid() == null) {
                uncertainCount++;
            } else if (Boolean.TRUE.equals(feedback.getValid())) {
                supportedCount++;
            } else {
                unsupportedCount++;
            }
        }

        systemLogService.info(String.format(
                "检索反馈分析完成: turnId=%d, routing=%s, contextFacts=%d, supportedFacts=%d, unsupportedFacts=%d, uncertainFacts=%d",
                turnId,
                snapshot.getRoutingDecision(),
                contextFactCount,
                supportedCount,
                unsupportedCount,
                uncertainCount
        ), "MEMORY");
    }

    void logAdaptiveSummary(Long turnId,
                            RetrievalFeedbackResult result,
                            AdaptiveUpdateSummary adaptiveUpdateSummary,
                            FeedbackPersistenceState persistenceState) {
        int supportedCount = result == null ? 0 : collectHighConfidenceSupportedFactIds(result).size();
        AdaptiveUpdateSummary summary = adaptiveUpdateSummary == null ? AdaptiveUpdateSummary.skipped() : adaptiveUpdateSummary;
        systemLogService.info(String.format(
                "阶段二自适应更新完成: turnId=%d, persistence=%s, supportedFacts=%d, factScoreUpdates=%d, relatedUpdates=%d",
                turnId,
                persistenceState != null ? persistenceState.name() : "UNKNOWN",
                supportedCount,
                summary.factUpdates(),
                summary.relationUpdates()
        ), "MEMORY");
        if (memoryStateWriteEnabled) {
            systemLogService.info(String.format(
                    "阶段三状态写回完成: turnId=%d, stateUpdates=%d",
                    turnId,
                    summary.memoryStateUpdates()
            ), "MEMORY");
        }
    }

    record AdaptiveUpdateSummary(int factUpdates, int relationUpdates, int memoryStateUpdates) {

        AdaptiveUpdateSummary(int factUpdates, int relationUpdates) {
            this(factUpdates, relationUpdates, 0);
        }

        private static AdaptiveUpdateSummary skipped() {
            return new AdaptiveUpdateSummary(0, 0, 0);
        }
    }
}
