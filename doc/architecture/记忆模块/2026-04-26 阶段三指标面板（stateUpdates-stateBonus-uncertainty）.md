# 2026-04-26 阶段三指标面板（stateUpdates / stateBonus / uncertainty）

## 1. 目的

用于观测阶段三最小版是否稳定生效，重点回答三件事：

1. 状态写回是否真的在执行（`stateUpdates`）
2. 状态加分是否真正命中排序（`stateBonus` 命中率）
3. 不确定性是否沿预期收敛（`uncertainty` 分布）

## 2. 指标清单

已落地的指标（Micrometer）：

- `lingshu.memory.state.write.attempts`
- `lingshu.memory.state.write.updates`
- `lingshu.memory.state.write.skipped`
- `lingshu.memory.state.write.errors`
- `lingshu.memory.state.uncertainty`（summary）
- `lingshu.memory.state.bonus.evaluated`
- `lingshu.memory.state.bonus.applied`
- `lingshu.memory.state.bonus.skipped`
- `lingshu.memory.state.bonus.value`（summary）

## 3. 推荐面板

### 面板 A：状态写回

- 写回触发量：`state.write.attempts`
- 写回成功量：`state.write.updates`
- 写回跳过量：`state.write.skipped`
- 写回错误量：`state.write.errors`

建议展示：

- 累计趋势折线
- 最近 5/15/60 分钟增量柱状
- 错误率：`errors / attempts`

### 面板 B：stateBonus 命中率

- 评估量：`state.bonus.evaluated`
- 命中量：`state.bonus.applied`
- 未命中量：`state.bonus.skipped`

建议展示：

- 命中率：`applied / evaluated`
- 命中量与未命中量对比
- `state.bonus.value` 分布（P50 / P90）

### 面板 C：不确定性分布

- 指标：`lingshu.memory.state.uncertainty`

建议展示：

- P50 / P90 / P99
- 最近窗口均值
- 分位趋势（观察是否从高不确定性逐步收敛）

## 4. 阈值建议

第一版灰度观察建议：

- 写回错误率：`< 1%`
- stateBonus 命中率：`> 10%`（冷启动阶段可低于此值）
- uncertainty P90：不长期贴近 `1.2` 上限

## 5. 接口检查方式

本项目已开启 actuator，可直接检查：

- `/actuator/metrics/lingshu.memory.state.write.updates`
- `/actuator/metrics/lingshu.memory.state.bonus.applied`
- `/actuator/metrics/lingshu.memory.state.uncertainty`

## 6. 关联代码

- [RetrievalFeedbackService.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/RetrievalFeedbackService.java:1)
- [MemoryServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java:1)
