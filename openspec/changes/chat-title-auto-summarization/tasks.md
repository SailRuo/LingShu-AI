# Tasks: 聊天对话标题自动摘要实现

## 1. 接口与数据模型调整

- [x] 1.1 修改 `ChatService` 及其实现类的方法签名，确保 `sessionId` 能够传递至后处理阶段
- [x] 1.2 更新 `TurnPostProcessingService` 接口，增加 `Long sessionId` 参数
- [x] 1.3 在 `ChatSessionRepository` 中增加必要的查询支持（如通过 ID 获取完整实体）

## 2. 摘要逻辑开发

- [x] 2.1 创建 `ConversationTitleSummarizer` 接口，定义摘要 Prompt
- [x] 2.2 实现 `ConversationTitleSummarizer`，调用 LLM 获取建议标题
- [x] 2.3 在 `ChatSessionService` 中实现 `updateSessionTitle(Long sessionId, String newTitle)` 方法

## 3. 集成与触发

- [x] 3.1 在 `TurnPostProcessingServiceImpl` 中引入 `ChatSessionService`
- [x] 3.2 实现标题检查逻辑：识别默认标题及首轮对话触发条件
- [x] 3.3 串联摘要任务：在满足条件时异步更新标题

## 4. 测试与验证

- [x] 4.1 编写单元测试验证标题检查正则表达式的准确性
- [ ] 4.2 进行集成测试，确认第一轮对话后数据库标题字段已更新
- [ ] 4.3 观察日志，确保异步任务不会对主回复流量产生负面影响
