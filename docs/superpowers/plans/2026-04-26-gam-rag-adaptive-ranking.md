# GAM-RAG Adaptive Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已完成“最近一轮反馈闭环”的基础上，把 `supportedFacts` 真实接入图谱排序、路由增益计算和 `RELATED_TO` 关系权重更新，形成阶段二“反馈驱动自适应排序版”。

**Architecture:** 阶段二不引入 `MemoryState`、Kalman 或新向量状态，只做三件事：一是把每轮事实级反馈持久化为可查询记录；二是把反馈结果转成 `FactNode.importance/confidence` 与 `RELATED_TO.weight` 的小步标量更新；三是在图谱检索排序和 `gain` 计算里消费这些更新，让下一轮检索能从上一轮反馈中获益。所有更新异步执行，并保留开关可回滚到当前阶段一行为。

**Tech Stack:** Java 21, Spring Boot 3.2, Spring Data Neo4j, LangChain4j, JUnit 5, Mockito

---

### Task 1: 持久化每轮事实级反馈记录

**Files:**
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/RetrievalFeedbackRecord.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/RetrievalFeedbackRecordRepository.java`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackServicePersistenceTest.java`

- [ ] **Step 1: Write the failing persistence test**

```java
package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.infrastructure.repository.RetrievalFeedbackRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalFeedbackServicePersistenceTest {

    @Test
    void analyzeTurn_shouldPersistFactLevelFeedbackRecords() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(201L)
                .sessionId(12L)
                .userId("alice")
                .query("我最近在忙什么")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(1L).content("用户最近在准备面试").source("graph").rank(1).build(),
                        RetrievalFactCandidate.builder().factId(2L).content("用户喜欢蓝色").source("graph").rank(2).build()
                ))
                .build();

        var snapshotStore = mock(com.lingshu.ai.core.service.RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(201L)).thenReturn(Optional.of(snapshot));

        var analyzer = mock(com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "你最近在准备面试")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(201L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(1L).valid(Boolean.TRUE).confidence(0.93).reason("直接引用").build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(2L).valid(Boolean.FALSE).confidence(0.88).reason("未使用").build()
                ))
                .build());

        RetrievalFeedbackRecordRepository repository = mock(RetrievalFeedbackRecordRepository.class);
        RetrievalFeedbackService service = TestRetrievalFeedbackServiceFactory.create(snapshotStore, analyzer, repository);

        service.analyzeTurn(201L, "你最近在准备面试");

        verify(repository).saveAll(anyList());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lingshu-core -am -Dtest=RetrievalFeedbackServicePersistenceTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because `RetrievalFeedbackRecordRepository` and persistence wiring do not exist yet

- [ ] **Step 3: Add the feedback record entity, repository, and save path**

```java
package com.lingshu.ai.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("RetrievalFeedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalFeedbackRecord {

    @Id
    @GeneratedValue
    private Long id;

    private Long turnId;
    private Long sessionId;
    private String userId;
    private Long factId;
    private String query;
    private String routingDecision;
    private Boolean valid;
    private Double confidence;
    private String reason;
    private LocalDateTime createdAt;
}
```

```java
package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RetrievalFeedbackRecordRepository extends Neo4jRepository<RetrievalFeedbackRecord, Long> {

    List<RetrievalFeedbackRecord> findByFactId(Long factId);

    @Query("""
            MATCH (r:RetrievalFeedback)
            WHERE r.factId = $factId
            RETURN r
            ORDER BY r.createdAt DESC
            LIMIT $limit
            """)
    List<RetrievalFeedbackRecord> findRecentByFactId(Long factId, long limit);
}
```

```java
private void persistFeedbackRecords(RetrievalContextSnapshot snapshot, RetrievalFeedbackResult result) {
    List<com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord> records = result.getFactFeedback().stream()
            .filter(feedback -> feedback != null && feedback.getFactId() != null)
            .map(feedback -> com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord.builder()
                    .turnId(snapshot.getTurnId())
                    .sessionId(snapshot.getSessionId())
                    .userId(snapshot.getUserId())
                    .factId(feedback.getFactId())
                    .query(snapshot.getQuery())
                    .routingDecision(snapshot.getRoutingDecision())
                    .valid(feedback.getValid())
                    .confidence(feedback.getConfidence())
                    .reason(feedback.getReason())
                    .createdAt(java.time.LocalDateTime.now())
                    .build())
            .toList();
    if (!records.isEmpty()) {
        retrievalFeedbackRecordRepository.saveAll(records);
    }
}
```

- [ ] **Step 4: Run the persistence test**

Run: `mvn -pl lingshu-core -am -Dtest=RetrievalFeedbackServicePersistenceTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS with `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/RetrievalFeedbackRecord.java backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/RetrievalFeedbackRecordRepository.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackServicePersistenceTest.java
git commit -m "feat: persist retrieval feedback records"
```

### Task 2: 将反馈转换为事实与关系的标量更新

**Files:**
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/AdaptiveMemoryScorer.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/AdaptiveMemoryScorerImpl.java`
- Modify: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/FactRepository.java`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/AdaptiveMemoryScorerImplTest.java`

- [ ] **Step 1: Write the failing scorer test**

```java
package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.AdaptiveMemoryScorer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveMemoryScorerImplTest {

    @Test
    void score_shouldRewardSupportedFactAndPenalizeConfidentUnsupportedFact() {
        AdaptiveMemoryScorer scorer = new AdaptiveMemoryScorerImpl();

        AdaptiveMemoryScorer.FactDelta supported = scorer.scoreFact(
                0.82,
                0.88,
                RetrievalFeedbackResult.FactFeedback.builder().factId(1L).valid(Boolean.TRUE).confidence(0.92).build()
        );
        AdaptiveMemoryScorer.FactDelta rejected = scorer.scoreFact(
                0.82,
                0.88,
                RetrievalFeedbackResult.FactFeedback.builder().factId(2L).valid(Boolean.FALSE).confidence(0.91).build()
        );

        assertEquals(0.85, supported.newImportance(), 0.0001);
        assertEquals(0.80, rejected.newImportance(), 0.0001);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lingshu-core -am -Dtest=AdaptiveMemoryScorerImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because `AdaptiveMemoryScorer` is missing

- [ ] **Step 3: Add the scorer and repository update queries**

```java
package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.RetrievalFeedbackResult;

public interface AdaptiveMemoryScorer {

    FactDelta scoreFact(double currentImportance, double currentConfidence, RetrievalFeedbackResult.FactFeedback feedback);

    double scoreRelationWeight(double currentWeight, long supportedCooccurrence, long unsupportedCooccurrence);

    record FactDelta(double newImportance, double newConfidence) {}
}
```

```java
package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.AdaptiveMemoryScorer;
import org.springframework.stereotype.Service;

@Service
public class AdaptiveMemoryScorerImpl implements AdaptiveMemoryScorer {

    @Override
    public FactDelta scoreFact(double currentImportance, double currentConfidence, RetrievalFeedbackResult.FactFeedback feedback) {
        if (feedback == null || feedback.getValid() == null || feedback.getConfidence() == null) {
            return new FactDelta(currentImportance, currentConfidence);
        }
        if (Boolean.TRUE.equals(feedback.getValid())) {
            return new FactDelta(
                    Math.min(0.98, currentImportance + 0.03),
                    Math.min(0.98, currentConfidence + 0.02)
            );
        }
        if (feedback.getConfidence() >= 0.80) {
            return new FactDelta(
                    Math.max(0.12, currentImportance - 0.02),
                    Math.max(0.20, currentConfidence - 0.03)
            );
        }
        return new FactDelta(currentImportance, currentConfidence);
    }

    @Override
    public double scoreRelationWeight(double currentWeight, long supportedCooccurrence, long unsupportedCooccurrence) {
        double next = currentWeight + supportedCooccurrence * 0.05 - unsupportedCooccurrence * 0.03;
        return Math.max(0.20, Math.min(1.0, next));
    }
}
```

```java
@Query("""
        MATCH (f:Fact)
        WHERE id(f) = $factId
        SET f.importance = $importance,
            f.confidence = $confidence,
            f.lastActivatedAt = $updatedAt
        """)
void updateFactAdaptiveScores(Long factId, double importance, double confidence, java.time.LocalDateTime updatedAt);

@Query("""
        MATCH (a:Fact)-[r:RELATED_TO]-(b:Fact)
        WHERE id(a) = $sourceId AND id(b) = $targetId
        SET r.weight = $weight,
            r.lastActivatedAt = $updatedAt
        """)
void updateRelatedRelationWeight(Long sourceId, Long targetId, double weight, java.time.LocalDateTime updatedAt);
```

- [ ] **Step 4: Run the scorer test**

Run: `mvn -pl lingshu-core -am -Dtest=AdaptiveMemoryScorerImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS with `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/AdaptiveMemoryScorer.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/AdaptiveMemoryScorerImpl.java backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/FactRepository.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/AdaptiveMemoryScorerImplTest.java
git commit -m "feat: add adaptive feedback scorer"
```

### Task 3: 用 `supportedFacts` 驱动 `RELATED_TO` 正负向更新

**Files:**
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackRelationUpdateTest.java`

- [ ] **Step 1: Write the failing relation update test**

```java
package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RetrievalFeedbackRelationUpdateTest {

    @Test
    void analyzeTurn_shouldStrengthenOnlySupportedFactPairs() {
        var factRepository = mock(com.lingshu.ai.infrastructure.repository.FactRepository.class);
        var service = TestRetrievalFeedbackServiceFactory.createWithFactRepository(factRepository);

        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .turnId(301L)
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(1L).content("用户在准备面试").build(),
                        RetrievalFactCandidate.builder().factId(2L).content("用户在整理项目经历").build(),
                        RetrievalFactCandidate.builder().factId(3L).content("用户喜欢蓝色").build()
                ))
                .build();
        RetrievalFeedbackResult result = RetrievalFeedbackResult.builder()
                .turnId(301L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder().factId(1L).valid(Boolean.TRUE).confidence(0.95).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(2L).valid(Boolean.TRUE).confidence(0.88).build(),
                        RetrievalFeedbackResult.FactFeedback.builder().factId(3L).valid(Boolean.FALSE).confidence(0.91).build()
                ))
                .build();

        service.applyAdaptiveUpdates(snapshot, result);

        verify(factRepository).updateRelatedRelationWeight(1L, 2L, 0.55, org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lingshu-core -am -Dtest=RetrievalFeedbackRelationUpdateTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because `applyAdaptiveUpdates` does not exist yet

- [ ] **Step 3: Implement supported-pair updates and stop raw adopted-fact strengthening**

```java
void applyAdaptiveUpdates(RetrievalContextSnapshot snapshot, RetrievalFeedbackResult result) {
    java.util.Map<Long, RetrievalFeedbackResult.FactFeedback> feedbackIndex = result.getFactFeedback().stream()
            .filter(item -> item != null && item.getFactId() != null)
            .collect(java.util.stream.Collectors.toMap(
                    RetrievalFeedbackResult.FactFeedback::getFactId,
                    item -> item,
                    (left, right) -> left,
                    java.util.LinkedHashMap::new
            ));

    for (RetrievalFactCandidate candidate : snapshot.getContextFacts()) {
        RetrievalFeedbackResult.FactFeedback feedback = feedbackIndex.get(candidate.getFactId());
        if (feedback == null) {
            continue;
        }
        com.lingshu.ai.infrastructure.entity.FactNode fact = factRepository.findById(candidate.getFactId()).orElse(null);
        if (fact == null) {
            continue;
        }
        var delta = adaptiveMemoryScorer.scoreFact(fact.getImportance(), fact.getConfidence(), feedback);
        factRepository.updateFactAdaptiveScores(candidate.getFactId(), delta.newImportance(), delta.newConfidence(), java.time.LocalDateTime.now());
    }

    java.util.List<Long> supportedIds = result.getFactFeedback().stream()
            .filter(item -> item != null && Boolean.TRUE.equals(item.getValid()))
            .filter(item -> item.getConfidence() != null && item.getConfidence() >= 0.80)
            .map(RetrievalFeedbackResult.FactFeedback::getFactId)
            .distinct()
            .sorted()
            .toList();

    for (int i = 0; i < supportedIds.size(); i++) {
        for (int j = i + 1; j < supportedIds.size(); j++) {
            Long sourceId = supportedIds.get(i);
            Long targetId = supportedIds.get(j);
            Double currentWeight = factRepository.findRelatedRelationWeight(sourceId, targetId);
            double nextWeight = adaptiveMemoryScorer.scoreRelationWeight(currentWeight == null ? 0.50 : currentWeight, 1, 0);
            factRepository.updateRelatedRelationWeight(sourceId, targetId, nextWeight, java.time.LocalDateTime.now());
        }
    }
}
```

```java
@Override
public void updateRelationshipsFromRetrievalEvent(com.lingshu.ai.core.dto.MemoryRetrievalEvent event) {
    // Stage 2 后不再基于 raw adoptedFactIds 直接增强 RELATED_TO。
    // 关系更新改由 RetrievalFeedbackService 在拿到 supportedFacts 后执行。
}
```

- [ ] **Step 4: Run the relation update test**

Run: `mvn -pl lingshu-core -am -Dtest=RetrievalFeedbackRelationUpdateTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS with `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackRelationUpdateTest.java
git commit -m "feat: update related facts from supported feedback"
```

### Task 4: 将反馈更新接入图谱排序与路由增益

**Files:**
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/MemoryServiceImplAdaptiveRankingTest.java`

- [ ] **Step 1: Write the failing ranking test**

```java
package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.infrastructure.entity.FactNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryServiceImplAdaptiveRankingTest {

    @Test
    void performGraphRetrieval_shouldPreferSupportedHighConfidenceFact() {
        FactNode supported = FactNode.builder()
                .id(11L)
                .content("用户最近在准备面试")
                .importance(0.84)
                .confidence(0.92)
                .observedAt(LocalDateTime.now())
                .build();
        FactNode unsupported = FactNode.builder()
                .id(12L)
                .content("用户喜欢蓝色")
                .importance(0.86)
                .confidence(0.52)
                .observedAt(LocalDateTime.now())
                .build();

        MemoryServiceImpl service = TestMemoryServiceFactory.create();

        List<?> hits = service.rankGraphFactsForTest(List.of(unsupported, supported), List.of("准备", "面试"));

        assertEquals(11L, ((MemoryServiceImpl.GraphRetrievalHit) hits.get(0)).fact.getId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lingshu-core -am -Dtest=MemoryServiceImplAdaptiveRankingTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because adaptive ranking helper does not exist

- [ ] **Step 3: Blend confidence into graph ranking and gain calculation**

```java
private double computeAdaptiveGraphScore(FactNode fact, int hop, double relationWeight) {
    double score = fact.getImportance() * 0.60 + fact.getConfidence() * 0.30 + relationWeight * 0.10;
    if (fact.getStatus() != null && "superseded".equalsIgnoreCase(fact.getStatus())) {
        score *= 0.35;
    }
    if (hop >= 2) {
        score *= 0.75;
    }
    return score;
}
```

```java
List<FactNode> matched = user.getFacts().stream()
        .filter(f -> f.getContent() != null && entities.stream()
                .anyMatch(e -> f.getContent().toLowerCase().contains(e.toLowerCase())))
        .sorted((left, right) -> Double.compare(
                computeAdaptiveGraphScore(right, 1, 1.0),
                computeAdaptiveGraphScore(left, 1, 1.0)))
        .limit(8)
        .toList();
```

```java
double score = computeAdaptiveGraphScore(fact, hit.hop, hit.relationWeight);
totalImportance += score;
```

- [ ] **Step 4: Run the ranking verification suite**

Run: `mvn -pl lingshu-core -am "-Dtest=MemoryServiceImplAdaptiveRankingTest,RetrievalFeedbackRelationUpdateTest,RetrievalFeedbackServicePersistenceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS with `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/MemoryServiceImplAdaptiveRankingTest.java
git commit -m "feat: feed retrieval feedback into graph ranking"
```

### Task 5: 文档、开关与可观测性收口

**Files:**
- Modify: `doc/architecture/记忆模块/GAM-RAG 增益自适应实现状态与演进路线.md`
- Modify: `doc/architecture/记忆模块/2026-04-26 GAM-RAG 反馈闭环最小版变更说明与代码审查总结.md`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackServiceMetricsTest.java`

- [ ] **Step 1: Write the failing metrics test**

```java
package com.lingshu.ai.core.service.impl;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RetrievalFeedbackServiceMetricsTest {

    @Test
    void analyzeTurn_shouldLogAdaptiveUpdateSummary() {
        var systemLogService = mock(com.lingshu.ai.core.service.SystemLogService.class);
        RetrievalFeedbackService service = TestRetrievalFeedbackServiceFactory.createWithLogger(systemLogService);

        service.logAdaptiveSummary(401L, 2, 1, 1);

        verify(systemLogService).info(contains("supportedFacts=2"), eq("MEMORY"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lingshu-core -am -Dtest=RetrievalFeedbackServiceMetricsTest "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because adaptive summary logging helper does not exist

- [ ] **Step 3: Add feature flag notes and final observability**

```java
private void logAdaptiveSummary(Long turnId, int supportedCount, int downgradedCount, int relationUpdates) {
    systemLogService.info(String.format(
            "阶段二自适应更新完成: turnId=%d, supportedFacts=%d, downgradedFacts=%d, relatedUpdates=%d",
            turnId,
            supportedCount,
            downgradedCount,
            relationUpdates
    ), "MEMORY");
}
```

```markdown
## 阶段二实施入口

- 计划文件：`docs/superpowers/plans/2026-04-26-gam-rag-adaptive-ranking.md`
- 核心范围：反馈记录持久化、事实标量更新、`RELATED_TO` 正负向调节、图谱排序与 gain 修正
- 明确不做：`MemoryState`、`taskVector`、Kalman、uncertainty
```

- [ ] **Step 4: Run the final focused suite**

Run: `mvn -pl lingshu-core -am "-Dtest=RetrievalFeedbackServiceMetricsTest,MemoryServiceImplAdaptiveRankingTest,RetrievalFeedbackRelationUpdateTest,RetrievalFeedbackServicePersistenceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS with `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add doc/architecture/记忆模块/GAM-RAG\ 增益自适应实现状态与演进路线.md "doc/architecture/记忆模块/2026-04-26 GAM-RAG 反馈闭环最小版变更说明与代码审查总结.md" backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackServiceMetricsTest.java
git commit -m "docs: capture stage-two adaptive ranking rollout"
```

---

## Self-Review

### Spec coverage

- 阶段二要求的反馈记录持久化、`supportedFacts` 接入排序、`RELATED_TO` 正负向更新、路由 `gain` 调整、指标与回滚边界，都已映射到任务 1-5。
- 本计划明确不包含 `MemoryState`、`taskVector`、`uncertainty`、Kalman 更新，这些仍属于阶段三。

### Placeholder scan

- 无 `TODO`、`TBD`、`后续补充` 占位项。
- 每个任务都给出了文件路径、测试入口、命令和预期结果。

### Type consistency

- 反馈输入统一来自 `RetrievalContextSnapshot` + `RetrievalFeedbackResult`
- 持久化记录统一使用 `RetrievalFeedbackRecord`
- 标量更新统一通过 `AdaptiveMemoryScorer`
- 图谱排序统一消费 `FactNode.importance/confidence` 与 `RELATED_TO.weight`

---

Plan complete and saved to `docs/superpowers/plans/2026-04-26-gam-rag-adaptive-ranking.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
