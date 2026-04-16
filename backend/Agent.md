# LingShu-AI Backend Agent 指南

这是给进入 `backend/` 目录的 Codex 用的目录级工作手册。先读它，再看相关文档和实现。

## 1. 目录职责

这里是 LingShu-AI 的核心编排层，负责聊天、记忆、情感分析、主动关怀、MCP 工具调用、系统设置和日志等能力。

后端技术栈：

- Java 21
- Spring Boot 3.2.4
- LangChain4j
- Neo4j
- PostgreSQL + pgvector
- Redis

## 2. 优先阅读

优先阅读：

- `../doc/architecture/系统架构设计文档.md`
- `../doc/architecture/对话调用链路详解.md`
- `../doc/architecture/记忆模块设计文档.md`
- `../README.md`
- `../CONTRIBUTING.md`
- `../.agents/rules/defult.md`

然后再看这些关键文件：

- `pom.xml`
- `lingshu-web/src/main/java/com/lingshu/ai/web/LingshuAiApplication.java`
- `lingshu-web/src/main/java/com/lingshu/ai/web/websocket/ChatWebSocketHandler.java`
- `lingshu-web/src/main/java/com/lingshu/ai/web/websocket/WebSocketConfig.java`
- `lingshu-web/src/main/java/com/lingshu/ai/web/controller/ChatController.java`
- `lingshu-web/src/main/java/com/lingshu/ai/web/controller/MemoryController.java`
- `lingshu-web/src/main/java/com/lingshu/ai/web/controller/McpController.java`
- `lingshu-web/src/main/java/com/lingshu/ai/web/controller/SettingController.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/EmotionPreAnalysisService.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/EmotionContextCache.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/PromptBuilderServiceImpl.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TurnPostProcessingServiceImpl.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ProactiveServiceImpl.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/McpServiceImpl.java`
- `lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/SettingServiceImpl.java`
- `lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/*`
- `lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/*`
- `lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/memory/DatabaseChatMemoryStore.java`

## 3. 架构职责

后端按三层拆分：

- `lingshu-web`：Web、Controller、WebSocket、对外接口
- `lingshu-core`：业务编排、聊天流、记忆、情感、工具、Prompt
- `lingshu-infrastructure`：实体、仓储、数据库访问、持久化实现

核心原则：

- 控制器保持轻薄。
- 业务流程尽量放在 Service 层。
- 记忆、情感和 MCP 是项目核心能力，不要把它们改成普通 CRUD 逻辑。
- 前端只调后端 API，不要让前端直接接触数据库。

## 4. 关键链路

聊天主链路大致是：

1. 前端通过 WebSocket 发消息到 `ChatWebSocketHandler`。
2. 后端检索长期记忆（图谱 + 向量混合召回，GAM-RAG）和当前上下文。
3. `PromptBuilderService` 组装最终 System Prompt（注入记忆和关系状态）。
4. `ChatServiceImpl` 触发流式回复。
5. 回复结束后触发异步后处理（`TurnPostProcessingService`），决定是否做情感分析、提取事实或记录互动。

改聊天相关代码时，必须确认这条链路没有断。

## 5. 记忆与情感

项目的记忆模型分三层：

- 工作记忆
- 情景记忆
- 语义记忆

重要约束：

- 情感分析目前已改为后置异步处理，前置分析（`EmotionPreAnalysisService`）默认禁用，仅作为历史实现保留。
- 事实提取（`MemoryService`）依赖后处理决策，需支持去重、合并、归类及置信度评估。
- 长期记忆要支持衰减、强化和冲突处理。
- 图谱检索和向量检索要有明确边界，别混成一个黑盒。

## 6. MCP 与工具

- MCP 是这个项目的重要扩展点，涉及外部工具、技能和本地任务执行。
- 修改 MCP 逻辑时，要关注工具结果汇总、技能映射和安全边界。
- 工具调用链路的变化，通常会影响前端展示和日志输出。

## 7. 验证

- 后端编译安装：`mvn clean install -DskipTests`
- 运行 Web 模块：`cd lingshu-web && mvn spring-boot:run`
- 整体快速启动：`../run_backend.bat`
- Docker 全栈：`../docker-compose up --build`

改动后至少检查：

- 相关模块能否编译
- WebSocket 聊天流程是否正常
- 记忆查询和写入是否正常
- 重要接口返回结构是否和前端一致

## 8. 常见坑

- `ChatWebSocketHandler` 的消息协议不要随意改。
- `ChatServiceImpl` 的流式输出、工具事件和后处理耦合很强，改动要小心。
- `PromptBuilderServiceImpl` 的改动会影响回答风格和记忆注入。
- `MemoryServiceImpl` 的改动会影响长期记忆、图谱和语义检索。
- `UserState`、`SystemSetting`、`McpServerConfig` 这类配置和状态实体，变更时要同时考虑接口和数据库。

## 9. 输出要求

如果你在这个目录里做代码改动，最后用中文说明：

- 改了什么
- 为什么这么改
- 哪些文件受影响
- 你验证了什么
