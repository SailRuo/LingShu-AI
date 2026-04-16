## ADDED Requirements

### Requirement: 系统应记录检索反馈信号
当一次检索完成后，系统应记录哪些事实被 LLM 采纳用于最终回答，作为后续关系更新的输入信号。

#### Scenario: 检索完成后记录采纳事实
- **WHEN** LLM 完成回答生成
- **THEN** 系统记录本次检索中被采纳的事实 ID 列表到 `MemoryRetrievalEvent` 中

#### Scenario: 多事实共同采纳时标记关联候选
- **WHEN** 一次检索中有两个或以上事实同时被采纳
- **THEN** 系统将这些事实标记为"关联候选对"，用于后续关系建立

### Requirement: 系统应基于反馈动态建立 RELATED_TO 关系
当多个事实共同参与了一次成功的回答时，系统应在 Neo4j 中为它们建立或增强 `RELATED_TO` 关系。

#### Scenario: 首次建立关联候选对的关系
- **WHEN** 一对事实首次被标记为关联候选
- **THEN** 系统在 Neo4j 中创建 `RELATED_TO` 关系，初始权重为 0.5

#### Scenario: 重复共现时增强关系权重
- **WHEN** 一对已存在 `RELATED_TO` 关系的事实再次共同被采纳
- **THEN** 系统将该关系的权重增加 0.1，上限为 1.0

#### Scenario: 异步执行关系更新
- **WHEN** 检索完成并记录反馈后
- **THEN** 关系更新操作在异步线程池中执行，不阻塞主检索流程

### Requirement: 系统应提供关系更新的观测日志
每次关系建立或权重更新后，系统应记录日志以便观测和调试。

#### Scenario: 记录新建关系日志
- **WHEN** 新建一条 `RELATED_TO` 关系
- **THEN** 系统日志中包含"新建关系: Fact A <-> Fact B, 权重=0.5"

#### Scenario: 记录权重更新日志
- **WHEN** 更新已有关系权重
- **THEN** 系统日志中包含"增强关系: Fact A <-> Fact B, 权重从 0.5 -> 0.6"
