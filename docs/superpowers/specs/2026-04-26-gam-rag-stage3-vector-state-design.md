# GAM-RAG Stage 3 Vector-State (Minimal) Design

**Date:** 2026-04-26  
**Status:** Draft approved for planning baseline  
**Scope:** LingShu-AI memory module backend (`lingshu-core`, `lingshu-infrastructure`)

---

## 1. Goal

在阶段二“标量自适应排序”稳定的前提下，引入阶段三最小可运行版向量状态：

- 为事实建立独立 `MemoryState`
- 仅引入 `taskVector + taskUncertainty + updateCount + lastUpdate`
- 让高置信 `supportedFacts` 沿 query embedding 方向做小步更新
- 把状态相似度作为图谱排序的附加项，而不是替代阶段二评分

## 2. Non-goals

本次明确不做：

- `timeVector` / `timeUncertainty`
- 完整 Kalman 公式复刻
- 负反馈导致的激进向量反向漂移
- 多模态状态和可视化投影

## 3. Design Decision

采用“最小版优先”路径，原因：

- 阶段二刚落地，优先验证“向量状态是否有增益”
- 降低一次性引入多个高风险变量导致的不可诊断问题
- 保持回滚简单：关闭 feature flag 即退回阶段二

## 4. Data Model

新增 `MemoryStateRecord`（旁路存储，不改 `FactNode` 核心结构）：

- `id`
- `factId` (unique)
- `taskVector` (`float[]` serialized)
- `taskUncertainty` (`double`, default `1.0`)
- `updateCount` (`int`, default `0`)
- `lastUpdate` (`LocalDateTime`)
- `stateVersion` (`long`, optimistic lock field)

存储策略：

- 第一版放在 SQL/JPA 表（与 `RetrievalFeedbackRecord` 同风格）
- 避免把长向量直接塞 Neo4j 节点属性造成查询耦合

## 5. Update Flow

触发点：沿用现有 `RetrievalFeedbackService.analyzeTurn(...)` 后半段，位于阶段二 `applyAdaptiveUpdates` 之后。

输入：

- `turnId`
- 当前轮 query 文本
- `RetrievalFeedbackResult` 的高置信 `supportedFacts`

处理逻辑：

1. 为 query 生成 embedding `qVec`
2. 对每个高置信 `supportedFact` 读取或初始化 `MemoryState`
3. 计算最小版增益：
   - `gain = clamp(0.08 + 0.22 * confidence + 0.20 * uncertainty, 0.05, 0.35)`
4. 更新向量与不确定性：
   - `newVec = normalize((1 - gain) * oldVec + gain * qVec)`
   - `newUncertainty = max(0.08, oldUncertainty * 0.92)`
   - `updateCount += 1`
5. 写回状态仓库

负反馈策略（最小版）：

- 不做向量反向更新
- 对 `unsupportedFacts` 仅轻微上调不确定性（上限 1.2），避免状态过快僵化

## 6. Retrieval Integration

在图谱排序中新增状态相似度附加项：

- `stateSimilarity = cosine(queryVec, taskVector)`
- `stateBonus = max(0, stateSimilarity) * (1 - taskUncertainty) * alpha`
- `alpha` 初始建议 `0.12`

最终排序分数：

- `finalScore = stage2AdaptiveScore + stateBonus`

约束：

- 仅当 `feature.memory.state.enabled=true` 时启用
- 若 state 缺失或 embedding 失败，自动退回 stage2AdaptiveScore

## 7. Components

新增组件：

- `MemoryStateService`
- `MemoryStateRepository`
- `MemoryStateUpdater`
- `MemoryStateProjector` (仅负责 `stateBonus` 计算)

接线位置：

- 更新链路：`RetrievalFeedbackService`
- 检索链路：`MemoryServiceImpl` 图谱排序处

## 8. Observability

新增日志与指标：

- `memory_state_updates_total`
- `memory_state_init_total`
- `memory_state_avg_uncertainty`
- `memory_state_bonus_applied_total`
- `memory_state_update_skipped_total`（embedding 失败或状态异常）

## 9. Rollout and Rollback

灰度开关：

- `feature.memory.state.enabled`（总开关）
- `feature.memory.state.write.enabled`（只写不读时可打开）
- `feature.memory.state.read.enabled`（只读不写回放验证）

发布顺序：

1. 写开关先灰度（只写状态，不影响排序）
2. 观察状态稳定后再开读开关（参与排序）
3. 若异常，先关读开关，保留写回

## 10. Test Strategy

单元测试：

- `MemoryStateUpdaterTest`
- `MemoryStateProjectorTest`
- `RetrievalFeedbackServiceMemoryStateUpdateTest`
- `MemoryServiceImplStateBonusTest`

集成测试：

- 一轮反馈后状态写入可见
- 下一轮相同 query 下支持事实排名提升
- 关开关后行为退回阶段二

---

## 11. Acceptance Criteria

- 状态写回成功率 >= 99%
- 开启读开关后，离线回放集上下文有效事实占比提升
- 关闭开关后排序行为与阶段二一致
- 未引入主链路显著时延回归
