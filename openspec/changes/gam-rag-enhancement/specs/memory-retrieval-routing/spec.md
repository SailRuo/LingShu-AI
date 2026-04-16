## MODIFIED Requirements

### Requirement: 图谱召回质量必须驱动检索路由
系统 MUST 在执行向量检索之前计算图谱召回质量，并以该质量值决定是否进入向量检索阶段。Gain 计算 MUST 考虑事实的状态和时序信息，对 `superseded` 状态或过时的事实降低其增益贡献。

#### Scenario: 图谱召回质量达到阈值
- **WHEN** 图谱检索返回有效事实，且计算得到的 gain 大于等于阈值
- **THEN** 系统 MUST 将当前检索判定为可进入向量补召回阶段

#### Scenario: 图谱召回质量低于阈值
- **WHEN** 图谱检索返回有效事实，但计算得到的 gain 低于阈值
- **THEN** 系统 MUST 将当前检索判定为不进入向量补召回阶段

#### Scenario: 命中事实状态为 superseded 时降低增益贡献
- **WHEN** 图谱检索命中的事实状态为 `superseded`
- **THEN** 该事实对 Gain 的重要度贡献 MUST 乘以 0.3 的惩罚系数

#### Scenario: 命中事实时间过久时降低增益贡献
- **WHEN** 图谱检索命中的事实 `observedAt` 距离当前时间超过 90 天
- **THEN** 该事实对 Gain 的重要度贡献 MUST 乘以 0.7 的时效惩罚系数
