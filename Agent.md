# LingShu-AI Agent 指南

这是给后续 Codex 使用的快速作战手册。先读这份，再按需查看项目文档。

## 1. 项目是什么

LingShu-AI 是一个本地优先的 AI 陪伴项目，核心能力围绕长期记忆、情感感知、主动关怀和 MCP 工具调用展开。

核心技术栈：

- 后端：Java 21、Spring Boot 3.2.4、LangChain4j
- 前端：Vue 3、Vite、TypeScript、Naive UI、Tailwind CSS
- 存储：Neo4j、PostgreSQL + pgvector、Redis
- 运行方式：Web、Tauri 桌面端、Docker

## 2. 必须遵守的原则

- 面向用户的回复、实现计划、日志和项目内说明，优先使用中文。
- 语气要温和、克制、像陪伴者，不要写得像生硬的管理后台。
- 保持项目的 “Cyber-Zen” 视觉方向：高级、沉浸、通透、有氛围感，避免普通 CRUD 风。
- 前端不要直接连数据库，图谱数据必须通过后端 API 获取。
- 不要破坏聊天链路：WebSocket 聊天、记忆检索与上下文构建、流式输出、回合后处理（包含异步情感分析与事实提取）。注：情感前置分析目前已默认禁用，改为后处理。
- 保持本地优先，不要不必要地引入云端依赖。

## 3. 先看哪些文档

需要上下文时，优先阅读：

- `doc/architecture/系统架构设计文档.md`
- `doc/architecture/对话调用链路详解.md`
- `doc/architecture/记忆模块设计文档.md`
- `doc/architecture/UI_UX设计文档.md`
- `doc/implementation/Tauri实施文档.md`
- `doc/implementation/记忆图谱3D银河系实施文档.md`
- `README.md`
- `CONTRIBUTING.md`
- `.agents/rules/defult.md`

## 4. 架构速览

后端模块：

- `backend/lingshu-web`：控制器、WebSocket 入口、API 层
- `backend/lingshu-core`：聊天编排、记忆、情感、主动关怀、MCP、Prompt 构建
- `backend/lingshu-infrastructure`：实体、仓储、持久化、记忆存储、配置

前端结构：

- `frontend/src/components/chat`：聊天 UI、流式展示、输入、消息渲染
- `frontend/src/components/common`：氛围效果、主题组件
- `frontend/src/composables`：WebSocket、聊天、TTS、ASR、主动关怀 hooks
- `frontend/src/views`：聊天、洞察、共鸣、设置、安全、日志

聊天链路是：

1. 前端通过 WebSocket `ws://.../ws/chat` 发送聊天消息。
2. 后端先做回复前情感分析。
3. 后端检索记忆上下文并构建 System Prompt。
4. 模型把 token 流式返回前端。
5. 回复后处理决定是否再做情感分析或事实提取。

## 5. 记忆模型

文档里定义了三层记忆：

- L1：工作记忆
- L2：情景记忆
- L3：语义记忆

重要约束：

- 情感分析目前主要在回合后处理中执行，用于事实提取和情感记忆存储；对话时的情感感知主要依靠检索到的情感记忆上下文。
- 事实要做去重和合并，不要把原始文本当垃圾堆一直塞。
- 记忆需要支持衰减、强化、压缩和冲突处理。
- 图谱数据是产品能力的一部分，不只是可视化展示。

## 6. 前端规则

- 保留现有页面的视觉语言，不要把它改成普通后台。
- 优先沿用现成的主题系统和氛围效果，再考虑新增方案。
- 聊天体验要快、清晰、流畅，流式输出要“有生命感”，但不能吵。
- 记忆图谱当前在往 3D 银河方向演进，但不要把平面图硬改成假 3D。
- 改前端时，要顺手确认是否影响 Tauri 兼容性。

前端重点文件：

- `frontend/src/components/chat/ChatView.vue`
- `frontend/src/components/chat/ChatMessage.vue`
- `frontend/src/components/chat/ChatInput.vue`
- `frontend/src/composables/useWebSocket.ts`
- `frontend/src/composables/useChat.ts`
- `frontend/src/views/InsightView.vue`
- `frontend/src/views/ResonanceView.vue`
- `frontend/src/views/SettingsView.vue`
- `frontend/src/components/McpSettings.vue`

## 7. 后端规则

- 控制器保持轻薄，复杂编排放到 Service。
- 如果动到 `ChatServiceImpl`，一定检查回复前分析和回复后处理链路是否仍然完整。
- 优先做小步、可回退的改动，除非任务明确要求重构。
- Neo4j 和 pgvector 的交互要清晰、可解释，不要把检索逻辑写成黑盒。
- Prompt 构建会影响语气、记忆和工具调用，修改后要认真验证输出。

后端重点文件：

- `backend/lingshu-web/src/main/java/com/lingshu/ai/web/websocket/ChatWebSocketHandler.java`
- `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java`
- `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/EmotionPreAnalysisService.java`
- `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/EmotionContextCache.java`
- `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java`
- `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/PromptBuilderServiceImpl.java`
- `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/McpServiceImpl.java`
- `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/memory/DatabaseChatMemoryStore.java`

## 8. 运行与验证

优先使用项目现成命令：

- 前端开发：`cd frontend && npm run dev`
- 前端构建：`cd frontend && npm run build`
- Tauri 开发：`cd frontend && npm run tauri:dev`
- 后端批处理启动：`run_backend.bat`
- 后端 Maven 方式：`cd backend && mvn clean install -DskipTests && cd lingshu-web && mvn spring-boot:run`
- Docker 全栈：`docker-compose up --build`

改动后至少验证对应范围：

- 前端 UI 改动：构建前端
- 后端 API 或编排改动：编译或运行对应模块
- 聊天或记忆改动：尽量走一遍 WebSocket 全链路

## 9. 常见坑

- 不要假设前端可以直接访问数据库。
- 不要把 `README.md` 当成唯一行为规范，文档和代码优先级更高。
- 不要把情感感知链路弱化成普通聊天机器人。
- 不要随意引入大而重的新抽象，除非它真的简化了现有架构。
- 不要改动生成文件、依赖目录或 vendor 代码，除非别无选择。

## 10. 更高效的工作方式

处理新任务时：

1. 先读相关文档，再看最小文件集。
2. 先理解现有实现，再决定怎么改。
3. 只做最小且安全的修改。
4. 不只看语法，要验证关联流程。
5. 最后用中文总结，并写清楚具体文件。

