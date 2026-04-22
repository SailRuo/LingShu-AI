## ADDED Requirements

### Requirement: 对话标题自动摘要 (Automatic Title Summarization)
系统 MUST 在新会话的首次交互完成后，自动根据对话内容生成简洁的标题，以替换默认标题。

#### Scenario: 首次交互后自动更新标题
- **GIVEN** 用户处于一个标题为默认值（如“新对话 1”）的新会话中
- **WHEN** 用户发送第一条消息并收到完整的 AI 回复
- **THEN** 系统 MUST 在后台异步发起标题摘要请求
- **AND** 系统 MUST 将生成的简洁标题（建议 2-5 字）更新为该会话的标题

#### Scenario: 非默认标题不触发自动更新
- **GIVEN** 用户已经手动修改了对话标题，或者会话已有实质性标题
- **WHEN** 产生新的对话交互
- **THEN** 系统 MUST NOT 覆盖现有的标题内容，以尊重用户的自定义意志

#### Scenario: 摘要失败时的鲁棒性
- **WHEN** 摘要服务（LLM）调用失败或超时
- **THEN** 系统 MUST 保留原始标题
- **AND** 报错信息 MUST 记录在系统日志中，不应干扰正常的对话流程
