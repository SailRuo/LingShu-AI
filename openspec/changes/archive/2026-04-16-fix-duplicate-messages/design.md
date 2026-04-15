# Design - 优化对话记忆加载逻辑

## Context

在 LingShu-AI 的当前架构中：
1. `ChatServiceImpl` 收到请求后，立即调用 `turnTimelineService.startTurn`，将用户消息以 `status="running"` 存入 `chat_turns` 表。
2. 随后创建 LangChain4j 的 `AiService`，该服务会通过 `DatabaseChatMemoryStore` 从数据库加载对话历史。
3. `DatabaseChatMemoryStore.getMessages` 目前会查询 `running`, `completed`, `failed` 三种状态的记录。
4. 由于当前回合已被标记为 `running`，它会被当作历史消息加载进来。随后 `AiService` 自身又会根据当前参数添加一遍该消息，最终导致发送给模型的 Context 中存在两条重复的用户消息。

## Goals / Non-Goals

**Goals:**
- 消除发送给大模型提示词中的重复消息。
- 确保只有已成功闭环的对话（`completed`）才作为长期上下文。
- 保持 `TurnTimelineService` 的职责不变（即继续提前创建 `running` 记录以支撑 UI 实时显示）。

**Non-Goals:**
- 修改 LangChain4j 的内存管理机制。
- 修改 `ChatServiceImpl` 的调用时序。

## Decisions

### 1. 调整记忆库的过滤策略
**决策**：在 `DatabaseChatMemoryStore.getMessages` 中，将 `turnRepository` 的查询过滤条件从 `List.of("running", "completed", "failed")` 改为 `List.of("completed")`。

**原因**：
- `AiServices` 在调用 `chat` 方法时，会自动管理“当前回合”的消息。
- 数据库中的 `running` 记录仅用于 UI 状态追踪和异步处理，不应参与 LLM 的上下文构建。
- 只有状态为 `completed` 的 Turn 才代表一个完整的、模型已知的对话回合。

## Risks / Trade-offs

- **风险**：如果用户在对话过程中强制刷新页面或服务异常重启，未完成（`running`）的消息将不会出现在下一次对话的上下文中。
- **权衡**：这是可以接受的，因为未完成的对话通常意味着并没有得到有效的 AI 回复，将其包含在背景中反而可能导致逻辑断层。
