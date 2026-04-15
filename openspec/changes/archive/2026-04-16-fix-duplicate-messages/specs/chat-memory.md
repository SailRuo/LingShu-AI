## ADDED Requirements

### Requirement: 对话历史去重
对话记忆系统在向大模型提供上下文（Context）时，必须确保历史消息中不包含当前正在处理的用户消息副本。

#### Scenario: 发送单条文本消息
- **WHEN** 用户发送文本消息“你好”且该消息已存入数据库但尚未完成 AI 回复
- **THEN** 大模型收到的消息序列中，最后的 UserMessage 应当且仅有一条“你好”

### REMOVED Requirements

### Requirement: 包含运行中回合
**Reason**: 包含 `running` 状态的回合会导致消息在 LLM 请求中重复出现。
**Migration**: 仅依赖 `AiServices` 自动维护的当前回合内存，数据库仅加载已完成的记录。
