## 1. 核心逻辑修改

- [ ] 1.1 修改 `DatabaseChatMemoryStore.java`：在 `getMessages` 方法中，将 `turnRepository` 查询的状态列表从 `List.of("running", "completed", "failed")` 修改为 `List.of("completed")`。

## 2. 验证与测试

- [ ] 2.1 本地启动服务并发送测试消息，观察控制台输出的 `LLM Request Summary` 和 `LLM Request Message` 日志。
- [ ] 2.2 确认日志中同一 UserMessage 不再连续出现两次。
- [ ] 2.3 验证对话上下文的连贯性，确保历史消息（已完成的回合）仍能正常加载。
