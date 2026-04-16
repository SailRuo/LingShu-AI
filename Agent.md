# LingShu-AI Agent 指南

这是给 AI 使用的项目级工作手册。先读这份，再按需进入子目录文档。

## 1. 项目职责

LingShu-AI 是一个本地优先的 AI 陪伴项目，核心能力围绕长期记忆、情感感知、主动关怀和 MCP 工具调用展开。

## 2. 全局原则

- 面向用户的回复、实现计划和项目内说明，优先使用中文。
- 语气要温和、克制、像陪伴者，不要写得像生硬的管理后台。
- 保持项目的 `Cyber-Zen` 视觉方向：高级、沉浸、通透、有氛围感，避免普通 CRUD 风。
- 前端不要直接连数据库，图谱数据必须通过后端 API 获取。
- 不要破坏聊天链路、记忆链路和回合后处理。
- 保持本地优先，不要不必要地引入云端依赖。

## 3. 优先阅读

需要上下文时，优先阅读：

- `doc/architecture/系统架构设计文档.md`
- `doc/architecture/对话调用链路详解.md`
- `doc/architecture/记忆模块设计文档.md`
- `doc/architecture/UI_UX设计文档.md`
- `doc/implementation/Tauri实施文档.md`
- `doc/implementation/记忆图谱3D银河系实施文档.md`
- `README.md`
- `CONTRIBUTING.md`
- `.agents/rules/defult.md`（保留仓库既有文件名）

## 4. 目录速览

- `backend/lingshu-web`：控制器、WebSocket 入口、API 层
- `backend/lingshu-core`：聊天编排、记忆、情感、主动关怀、MCP、Prompt 构建
- `backend/lingshu-infrastructure`：实体、仓储、持久化、记忆存储、配置
- `frontend/src/components/chat`：聊天 UI、流式展示、输入、消息渲染
- `frontend/src/components/common`：氛围效果、主题组件
- `frontend/src/composables`：WebSocket、聊天、TTS、ASR、主动关怀 hooks
- `frontend/src/views`：聊天、洞察、共鸣、设置、安全、日志

聊天链路是：

1. 前端通过 WebSocket `ws://.../ws/chat` 发送聊天消息。
2. 后端检索记忆上下文并构建 System Prompt，当前实现不再依赖前置情感分析。
3. 模型把 token 流式返回前端。
4. 回复后处理决定是否做情感分析、事实提取或其他回合级记录。

## 5. 记忆模型

文档里定义了三层记忆：

- L1：工作记忆
- L2：情景记忆
- L3：语义记忆

重要约束：

- 情感分析目前主要在回合后处理中执行，用于事实提取和情感记忆存储；对话时的情感感知主要依赖检索到的情感记忆上下文。
- 事实要做去重和合并，不要把原始文本当垃圾堆一直塞。
- 记忆需要支持衰减、强化、压缩和冲突处理。
- 图谱数据是产品能力的一部分，不只是可视化展示。

## 6. 常见坑

- 不要假设前端可以直接访问数据库。
- 不要把 `README.md` 当成唯一行为规范，文档和代码优先级更高。
- 不要把情感感知链路弱化成普通聊天机器人。
- 不要随意引入大而重的新抽象，除非它真的简化了现有架构。
- 不要改动生成文件、依赖目录或 vendor 代码，除非别无选择。

## 7. 运行与验证

优先使用项目现成命令：

- 前端开发：`cd frontend && npm run dev`
- 前端构建：`cd frontend && npm run build`
- Tauri 开发：`cd frontend && npm run tauri:dev`
- 后端批处理启动：`run_backend.bat`
- 后端 Maven 方式：`cd backend && mvn clean install -DskipTests && cd lingshu-web && mvn spring-boot:run`
- Docker 全栈：`docker-compose up --build`

改动后至少验证对应范围：

- 前端 UI 改动：验证语法错误，确保代码符合 Vue 语法规范
- 后端 API 或编排改动：编译全部模块
- 聊天或记忆改动：审查一遍 WebSocket 全链路

## 8. 输出要求

如果你在这个目录里做代码改动，最后用中文说明：

- 改了什么
- 为什么这么改
- 哪些文件受影响
- 你验证了什么
