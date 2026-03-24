# LingShu-AI 后端接口文档

本文档详细列出了 LingShu-AI 后端服务提供的所有 API 接口、请求方法、参数说明以及相关的数据模型。

## 目录
1. [智能体配置 (Agent Config)](#智能体配置-agent-config)
2. [聊天服务 (Chat Service)](#聊天服务-chat-service)
3. [系统日志 (System Logs)](#系统日志-system-logs)
4. [WebSocket 对话服务 (WebSocket Chat)](#websocket-对话服务-websocket-chat)
5. [MCP 扩展 (MCP Extensions)](#mcp-扩展-mcp-extensions)
6. [记忆图谱 (Memory Graph)](#记忆图谱-memory-graph)
7. [系统设置 (System Settings)](#系统设置-system-settings)
8. [系统状态 (System Status)](#系统状态-system-status)
9. [数据模型 (Data Models)](#数据模型-data-models)

---

## 智能体配置 (Agent Config)
**Base Path:** `/api/agents`

| 接口名称 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 获取所有智能体 | `GET` | `/` | 返回系统中所有已定义的智能体配置。 |
| 获取激活智能体 | `GET` | `/active` | 返回当前标记为激活状态的智能体列表。 |
| 获取默认智能体 | `GET` | `/default` | 获取当前系统选定的默认智能体。 |
| 获取默认提示词 | `GET` | `/defaults` | 获取系统内置的初始智能体配置/提示词。 |
| 根据 ID 获取 | `GET` | `/{id}` | 根据主键 ID 获取特定智能体详情。 |
| 根据名称获取 | `GET` | `/name/{name}` | 根据唯一标识名获取智能体详情。 |
| 创建智能体 | `POST` | `/` | 创建一个新的智能体。请求体：`AgentConfig` |
| 更新智能体 | `PUT` | `/{id}` | 更新现有智能体信息。请求体：`AgentConfig` |
| 删除智能体 | `DELETE` | `/{id}` | 删除指定 ID 的智能体。 |
| 设为默认 | `POST` | `/{id}/set-default` | 将指定智能体设为全局默认。 |

---

## 聊天服务 (Chat Service)
**Base Path:** `/api/chat`

| 接口名称 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 同步聊天 | `POST` | `/send` | 发送消息并等待回复。请求体：`ChatRequest` |
| 流式聊天 | `POST` | `/stream` | **SSE** 方式发送消息，获取实时流式回复。 |
| 获取模型列表 | `GET` | `/models` | 根据 source (ollama/openai) 获取可用模型。 |
| 获取历史记录 | `GET` | `/history` | 获取指定会话的聊天历史。分页支持 `beforeId`。 |
| 主动招呼 | `GET` | `/proactive/greeting` | **SSE** 实时推送系统主动发起的问候语。 |
| 主动安慰 | `GET` | `/proactive/comfort` | **SSE** 实时推送系统主动发起的安慰信息。 |
| 关注用户 | `GET` | `/proactive/attention` | 获取长时间未交互需要关注的用户列表。 |
| 触发招呼 | `POST` | `/proactive/trigger` | 手动通知系统尝试为某用户生成招呼。 |

---

## 系统日志 (System Logs)
**Base Path:** `/api/logs`

| 接口名称 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 实时日志流 | `GET` | `/stream` | **SSE** 持续获取系统运行日志。 |
| 清除日志 | `DELETE` | `/` | 清空后端缓存的历史日志记录。 |

---

## WebSocket 对话服务 (WebSocket Chat)
**Endpoint:** `/ws/chat`

本项目提供基于 WebSocket 的全双工通话能力，支持注册、实时聊天流推送、主动招呼等。

### 交互流程
1. **连接建立**: 客户端连接到 `ws://{host}:{port}/ws/chat`。
2. **用户注册**: 发送 `register` 类型消息绑定 `userId`。
3. **对话交互**: 发送 `chat` 类型消息，通过 `chatChunk` 接收流式回复。
4. **心跳维持**: 定期发送 `ping` 以保持连接。

### 消息格式 (JSON)

#### 客户端发送 (Incoming)
| 类型 `type` | 参数 | 描述 |
| :--- | :--- | :--- |
| `register` | `userId`: 用户标识 | 将当前会话与特定用户绑定。 |
| `chat` | `message`, `agentId`, `model`, `apiKey`, `baseUrl` | 发起聊天请求，参数同 REST 接口。 |
| `history` | `size`, `beforeId` | 请求加载历史记录。 |
| `ping` | - | 心跳包。 |

#### 服务端推送 (Outgoing)
| 类型 `type` | 数据字段 | 描述 |
| :--- | :--- | :--- |
| `connected` | `sessionId`, `message` | 连接成功通知。 |
| `registered`| `userId` | 注册成功确认。 |
| `userState` | `affinity`, `relationshipStage` | 推送用户当前的好感度及关系阶段。 |
| `chatStart` | `userMessage` | 确认收到请求，开始处理。 |
| `chatChunk` | `content` | AI 回复的文本碎片。 |
| `chatEnd` | - | 本次回复推送结束。 |
| `proactiveGreeting` | `content` | 主动触发的问候语推送。 |
| `error` | `message` | 错误信息。 |
| `pong` | - | 心跳响应。 |

---

## MCP 扩展 (MCP Extensions)
**Base Path:** `/api/mcp`

| 接口名称 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 获取所有配置 | `GET` | `/` | 获取所有已配置的 MCP 服务器信息。 |
| 添加配置 | `POST` | `/` | 添加一个新的 MCP 服务器。请求体：`McpServerConfig` |
| 更新配置 | `PUT` | `/{id}` | 修改指定 MCP 服务器配置。 |
| 删除配置 | `DELETE` | `/{id}` | 移除指定 MCP 服务器。 |
| 切换状态 | `POST` | `/{id}/toggle` | 启用或禁用该 MCP 服务器。 |
| 连通性测试 | `POST` | `/{id}/ping` | 测试 MCP 服务器连接并返回可用工具列表。 |
| 导入配置 | `POST` | `/import` | 从指定的 JSON 字符串批量导入 MCP 配置。 |

---

## 记忆图谱 (Memory Graph)
**Base Path:** `/api/memory`

| 接口名称 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 获取图谱数据 | `GET` | `/graph` | 获取针对用户的知识图谱（节点与关系），用于前端可视化。 |
| 手动提取记忆 | `POST` | `/extract` | 输入一段文本，手动触发针对该用户的实时记忆提取。 |
| 删除事实 | `DELETE` | `/fact/{id}` | 从图谱中永久删除一个事实节点及其关联关系。 |

---

## 系统设置 (System Settings)
**Base Path:** `/api/settings`

| 接口名称 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 获取系统设置 | `GET` | `/` | 获取当前生效的全局配置（LLM 来源、Key、模型名等）。 |
| 保存系统设置 | `POST` | `/` | 更新并立即应用新的全局配置。请求体：`SystemSetting` |

---

## 系统状态 (System Status)
**Base Path:** `/api/system`

| 接口名称 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 获取运行状态 | `GET` | `/status` | 返回 AI 服务、数据库、显存占用及网络延时等健康状态。 |

---

## 数据模型 (Data Models)

### `AgentConfig`
| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `Long` | 主键 ID |
| `name` | `String` | 唯一标识名 (如: `lingshu`) |
| `displayName`| `String` | 显示名称 (如: `灵枢`) |
| `systemPrompt`| `String` | 核心系统提示词 |
| `isActive` | `Boolean` | 是否激活 |
| `isDefault` | `Boolean` | 是否为默认智能体 |

### `ChatRequest`
| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `message` | `String` | 用户输入的文本消息 |
| `agentId` | `Long` | 指定处理该消息的智能体 ID |
| `userId` | `String` | 用户标识 (默认为 `User`) |
| `model` | `String` | (可选) 覆盖全局配置的模型名 |

### `SystemSetting`
| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `source` | `String` | AI 来源 (`ollama` / `openai`) |
| `baseUrl` | `String` | API 接口地址 |
| `apiKey` | `String` | API 密钥 |
| `chatModel` | `String` | 聊天主模型名称 |
| `proactiveEnabled`| `Boolean` | 是否开启主动对话功能 |

### `McpServerConfig`
| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `name` | `String` | 服务器名称 |
| `transportType`| `String` | 传输方式 (`STDIO` / `SSE`) |
| `command` | `String` | (STDIO) 可执行命令 |
| `args` | `String` | JSON 格式的参数列表 |
| `url` | `String` | (SSE) 服务地址 |
