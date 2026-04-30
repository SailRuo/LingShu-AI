# Task Agent Runtime 计划与实施文档

> **目标：** 在保留现有陪伴对话链的前提下，新增一条独立的 `Task Agent Runtime` 路线，用于执行可暂停、可恢复、可审批、面向任意本地目录的复杂任务代理。

---

## 📌 当前进度总览 (截至 2026-04-30)

| 阶段 | 状态 | 核心内容 |
| :--- | :--- | :--- |
| **Phase 1: 基础设施** | ✅ 已完成 | 数据库模型、权限校验、任务生命周期管理、事件流推送、任务分流路由。 |
| **Phase 2: 引擎重构** | 🚧 实施中 | 将线性脚本执行器升级为 **Agentic Loop (自主智能体循环)**，已支持工具隔离、状态保存与断点恢复。 |

---

## 🛠 Phase 2 详细实施进展

### 1. 已完成的任务 (Done)

#### ✅ 任务引擎 Agent 化 (Task 9)
- **重构内容**：废弃了原有的“先计划再执行”的死板模式。
- **现状**：`TaskExecutionEngineImpl` 现在使用 LangChain4j 的 `AiServices` 构建了一个自主 Agent。它能根据任务目标自主决定调用哪些工具，并根据工具返回的结果（如编译错误）自动调整下一步行动。
- **代码引用**：[TaskExecutionEngineImpl.java](file:///e:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskExecutionEngineImpl.java)

#### ✅ 工具权限隔离 (Task 8)
- **重构内容**：为了安全，将危险工具（如执行系统命令）与普通聊天隔离。
- **现状**：
    - **普通聊天**：默认不再拥有 `execute_command` 权限。
    - **任务模式**：Agent 拥有完整的 `execute_command`、`read_file`、`write_file` 权限。
- **代码引用**：[ChatServiceImpl.java](file:///e:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java)

#### ✅ 状态自动保存 (Task 10 - Step 1)
- **重构内容**：任务执行过程中或暂停时，需要保存 Agent 的“大脑状态”（对话上下文）。
- **现状**：实现了 `saveAgentState` 方法，能将当前的对话历史序列化为 JSON 并存入数据库的 `runtime_snapshot_json` 字段。

#### ✅ 任务断点恢复 (Task 10 - Step 2)
- **重构内容**：将任务快照从“仅保存字符串”升级为“可恢复的结构化消息”，并在恢复时重建 Agent 记忆。
- **现状**：
    - 在 `executeAgenticWorkflow` 中接入 `restoreAgentState`，任务恢复时会自动加载快照并注入 `ChatMemory`。
    - 快照序列化支持 `SYSTEM / USER / AI / TOOL_EXECUTION_RESULT` 四类消息。
    - 对历史旧快照（字符串数组）做了向后兼容，避免历史任务恢复直接失败。
- **代码引用**：[TaskExecutionEngineImpl.java](file:///e:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskExecutionEngineImpl.java)

#### ✅ 稳定性与编译修复 (Extra)
- **修复内容**：解决了重构过程中引入的大量 DTO 编译错误（Missing Builders/Getters）以及 Spring Bean 注入歧义问题（`@Qualifier` 冲突）。
- **现状**：后端项目目前可以正常编译通过。

#### ✅ 任务意图路由门控优化 (2026-04-30)
- **问题背景**：任务模式开启后，短寒暄（如“你好”）被误路由到任务执行流，导致体验与预期不一致。
- **实现内容**：
    - 在 `TaskSessionRouterService` 增加“非任务短语优先拦截”（寒暄/确认语/纯标点短消息）。
    - 保留路径/文件名/命令等强信号直通规则。
    - 将“动作+目标”升级为意图打分（动作词 + 目标词 + 工程信号），阈值 `>=2` 才进入任务。
    - 增加“双重门禁”：前端先调 `/api/chat/task-intent` 再决定是否 `/api/tasks/start`；后端 `TaskRuntimeService.start()` 再次校验并拒绝非任务请求。
    - 新增单元测试覆盖“你好/hello 不进任务”和“明确开发请求进任务”。
- **代码引用**：
    - [TaskSessionRouterService.java](file:///e:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskSessionRouterService.java)
    - [TaskSessionRouterServiceTest.java](file:///e:/Project/LingShu-AI/backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskSessionRouterServiceTest.java)

#### ✅ 任务卡片动态步骤渲染 (2026-04-30)
- **问题背景**：固定步骤模板无法真实反映 Agent 的动态执行过程，用户难以理解“当前在做什么”。
- **实现内容**：
    - 前端任务卡片改为按事件流动态生成步骤，不再依赖固定四步。
    - `STEP_STARTED / STEP_COMPLETED / LOG / TASK_*` 按时间顺序映射为执行轨迹与终态收敛。
    - 日志按步骤上下文标注，提升可读性。
- **代码引用**：
    - [chat.ts](file:///e:/Project/LingShu-AI/lingshu-gui/src/stores/chat.ts)

---

### 2. 待完成的任务 (Pending)

#### ⏳ 快照恢复稳定性验证与可观测性补强 (Next)
- **目标**：
    - 增加针对快照保存/恢复的单元测试与集成测试（覆盖新格式与旧格式兼容）。
    - 增加恢复成功/跳过条数等诊断日志的监控指标，便于线上排查恢复异常。
    - 评估并完善“任务中途周期性快照”策略，降低异常中断时的上下文丢失窗口。

---

## 📝 历史记录 (Phase 1)
*(已于 2026-04-30 前全部完成，归档备查)*
- [x] Task 1: 任务域模型 (TaskRun, TaskEvent) 与 JPA 持久化。
- [x] Task 2: 任务权限服务 (目录授权校验)。
- [x] Task 3: 任务事件流 (WebSocket 实时推送)。
- [x] Task 6: 任务意图分流路由。
