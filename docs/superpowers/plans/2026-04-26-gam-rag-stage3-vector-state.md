# GAM-RAG Stage 3 Vector-State (Minimal) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在阶段二基础上落地阶段三最小版向量状态（`taskVector + taskUncertainty`），并以 feature flag 方式安全接入排序。

**Architecture:** 通过旁路 `MemoryState` 仓库存储事实级向量状态，在 `RetrievalFeedbackService` 中按高置信 `supportedFacts` 更新状态，在 `MemoryServiceImpl` 排序中读取状态相似度作为附加加分。开关关闭时行为完全退回阶段二。

**Tech Stack:** Java 21, Spring Boot 3.2, Spring Data JPA, LangChain4j EmbeddingModel, JUnit 5, Mockito

---

### Task 1: 建立 MemoryState 领域模型与仓库

**Files:**
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/MemoryStateRecord.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/MemoryStateRecordRepository.java`
- Modify: `backend/lingshu-infrastructure/pom.xml`
- Test: `backend/lingshu-infrastructure/src/test/java/com/lingshu/ai/infrastructure/repository/MemoryStateRecordRepositoryTest.java`

- [ ] Step 1: 先写仓库测试，验证按 `factId` 查询与新建状态可持久化。
- [ ] Step 2: 运行测试确认当前失败。
- [ ] Step 3: 实现 `MemoryStateRecord` 与 `MemoryStateRecordRepository`，字段包含 `factId/taskVector/taskUncertainty/updateCount/lastUpdate/stateVersion`。
- [ ] Step 4: 运行测试确认通过。
- [ ] Step 5: 提交一次小 commit。

### Task 2: 实现向量状态更新器（只处理最小版正反馈）

**Files:**
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/MemoryStateUpdater.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryStateUpdaterImpl.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/MemoryStateUpdaterImplTest.java`

- [ ] Step 1: 写失败测试，覆盖“初始化状态”“二次更新”“unsupported 仅上调 uncertainty”三条路径。
- [ ] Step 2: 运行测试确认失败。
- [ ] Step 3: 实现最小更新公式与边界夹紧（gain、uncertainty 上下限、向量归一化）。
- [ ] Step 4: 运行测试确认通过。
- [ ] Step 5: 提交一次小 commit。

### Task 3: 在 RetrievalFeedbackService 接入状态写回

**Files:**
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/config/MemoryFeatureProperties.java` (若不存在则创建)
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackServiceMemoryStateUpdateTest.java`

- [ ] Step 1: 写失败测试，验证开关开启时支持事实触发状态更新、关闭时不更新。
- [ ] Step 2: 运行测试确认失败。
- [ ] Step 3: 注入 `MemoryStateUpdater` 并在阶段二更新后调用，加入 `write.enabled` 开关控制。
- [ ] Step 4: 增加阶段三观测日志（stateInit/stateUpdate/stateSkip）。
- [ ] Step 5: 运行测试确认通过并提交。

### Task 4: 在图谱排序中读取状态加分

**Files:**
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/MemoryStateProjector.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryStateProjectorImpl.java`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/MemoryServiceImplStateBonusTest.java`

- [ ] Step 1: 写失败测试，验证 state 相似度加分能提升支持事实排序，且开关关闭时无影响。
- [ ] Step 2: 运行测试确认失败。
- [ ] Step 3: 在 `computeAdaptiveGraphScore` 附近接入 `stateBonus`（不改现有阶段二评分结构）。
- [ ] Step 4: 对 embedding 失败或 state 缺失走安全降级，保持阶段二分数。
- [ ] Step 5: 测试通过后提交。

### Task 5: 灰度开关、指标与文档收口

**Files:**
- Modify: `backend/lingshu-core/src/main/resources/application.yml` (若项目使用 profile 配置则按现有模式落位)
- Modify: `doc/architecture/记忆模块/GAM-RAG 增益自适应实现状态与演进路线.md`
- Modify: `doc/architecture/记忆模块/2026-04-26 GAM-RAG 反馈闭环最小版变更说明与代码审查总结.md`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/Stage3FeatureFlagIntegrationTest.java`

- [ ] Step 1: 写失败测试，验证 `state.read.enabled` / `state.write.enabled` 两个开关组合行为。
- [ ] Step 2: 运行测试确认失败。
- [ ] Step 3: 增加配置与指标日志，完成阶段三文档更新。
- [ ] Step 4: 运行阶段三聚焦测试与阶段二回归测试。
- [ ] Step 5: 提交收口 commit。

---

## Verification Commands

```powershell
mvn -pl lingshu-infrastructure -am "-Dtest=MemoryStateRecordRepositoryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl lingshu-core -am "-Dtest=MemoryStateUpdaterImplTest,RetrievalFeedbackServiceMemoryStateUpdateTest,MemoryServiceImplStateBonusTest,Stage3FeatureFlagIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl lingshu-core -am "-Dtest=RetrievalFeedbackServiceTest,RetrievalFeedbackRelationUpdateTest,MemoryServiceImplAdaptiveRankingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Definition of Done

- `MemoryState` 写回链路可运行且可观测。
- 排序加分默认关闭，开关开启后才生效。
- 阶段二回归测试全部通过。
- 架构文档与变更说明文档均更新为阶段三启动状态。
