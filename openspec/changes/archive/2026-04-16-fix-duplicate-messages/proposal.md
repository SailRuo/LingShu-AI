# Proposal - 修复对话记忆消息重复问题

## Why

目前在 LingShu-AI 中，用户发送一条消息时，日志显示大模型收到了两条完全重复的用户消息。这会导致：
1. **Token 浪费**：增加了不必要的 Token 消耗。
2. **模型干扰**：重复的输入可能导致模型产生错误的理解或冗余的回复。
3. **日志混乱**：开发者难以追踪真实的对话流。

该问题源于 `ChatServiceImpl` 在调用 AI 前就将当前回合存入了数据库，而 `DatabaseChatMemoryStore` 错误地将“运行中”的回合也读作历史记忆，导致消息叠加。

## What Changes

本变更主要修改 `DatabaseChatMemoryStore` 的消息加载逻辑：
- 在从数据库加载对话记忆时，过滤掉 `status` 为 `running`（正在运行）和 `failed`（已失败）的回合。
- 仅保留 `status` 为 `completed`（已完成）的历史记录作为大模型的上下文背景。

## Capabilities

### New Capabilities
- 无

### Modified Capabilities
- `chat-memory`: 修改对话记忆的持久化读取策略，确保当前活动的 UserMessage 仅由 AiService 实例管理，而不从数据库重复加载。

## Impact

- **Affected Components**: `lingshu-infrastructure` 模块中的 `DatabaseChatMemoryStore.java`。
- **User Experience**: 提升对话的准确性和响应的一致性。
- **System Cost**: 略微降低 Token 使用成本。
