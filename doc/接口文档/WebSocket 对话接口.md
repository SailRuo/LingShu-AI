# WebSocket 对话接口文档

## 概述

灵枢 AI 使用 WebSocket 实现实时双向通信，支持流式对话、语音识别、工具调用等功能。

---

## 基本信息

- **接口地址**: `ws://{host}/ws/chat`
- **协议**: WebSocket (RFC 6455)
- **编码格式**: JSON
- **字符编码**: UTF-8

### 环境配置

| 环境   | 地址示例                          |
|--------|-----------------------------------|
| 开发   | `ws://localhost:8080/ws/chat`     |
| 生产   | `wss://your-domain.com/ws/chat`   |

---

## 连接管理

### 连接建立

前端使用 `useWebSocket.ts` composable 建立连接：

```typescript
const ws = new WebSocket(wsUrl);
```

**连接成功后返回**:
```json
{
  "type": "connected",
  "sessionId": "abc123-def456",
  "message": "连接成功"
}
```

### 连接关闭与重连

- **自动重连策略**: 指数退避 (1s, 2s, 4s, 8s, 16s, 30s)
- **最大重试次数**: 5 次
- **超时时间**: 30 秒

---

## 消息类型

### 1. register - 用户注册

注册当前用户和会话，建立 WebSocket 会话与用户/会话的映射关系。

**请求**:
```json
{
  "type": "register",
  "userId": "user-123",
  "sessionId": 1001
}
```

| 字段        | 类型   | 必填 | 说明                     |
|-------------|--------|------|--------------------------|
| type        | string | 是   | 消息类型，固定为"register" |
| userId      | string | 是   | 用户唯一标识             |
| sessionId   | number | 否   | 聊天会话 ID              |

**响应**:
```json
{
  "type": "registered",
  "userId": "user-123",
  "chatSessionId": 1001
}
```

**附带用户状态**:
```json
{
  "type": "userState",
  "affinity": 50,
  "relationshipStage": "FRIEND"
}
```

---

### 2. chat - 发送对话消息

发送聊天消息，支持文本和多模态输入。

**请求**:
```json
{
  "type": "chat",
  "message": "你好，请介绍一下你自己",
  "agentId": 1,
  "model": "Qwen/Qwen3-8B",
  "apiKey": "sk-xxx",
  "baseUrl": "http://localhost:11434/v1",
  "images": ["data:image/png;base64,..."],
  "sessionId": 1001,
  "enableThinking": true
}
```

| 字段             | 类型     | 必填 | 说明                           |
|------------------|----------|------|--------------------------------|
| type             | string   | 是   | 消息类型，固定为"chat"         |
| message          | string   | 否   | 文本消息内容                   |
| images           | string[] | 否   | Base64 编码的图片数组          |
| agentId          | number   | 否   | Agent ID（智能体选择）         |
| model            | string   | 否   | 模型名称                       |
| apiKey           | string   | 否   | API Key（OpenAI 兼容接口）     |
| baseUrl          | string   | 否   | 模型服务地址                   |
| sessionId        | number   | 否   | 聊天会话 ID                    |
| enableThinking   | boolean  | 否   | 是否启用思考过程显示           |

**响应流程**:

1. **chatStart** - 开始处理
   ```json
   {
     "type": "chatStart",
     "userMessage": "你好，请介绍一下你自己",
     "chatSessionId": 1001
   }
   ```

2. **chatChunk** - 流式回复片段（多次）
   ```json
   {
     "type": "chatChunk",
     "content": "你好！我是灵枢 AI 助手..."
   }
   ```

3. **reasoningChunk** - 思考过程片段（可选）
   ```json
   {
     "type": "reasoningChunk",
     "content": "用户想了解我的基本信息..."
   }
   ```

4. **toolCallStart** - 工具调用开始（可选）
   ```json
   {
     "type": "toolCallStart",
     "toolCallId": "call_123",
     "toolName": "searchMemory",
     "skillName": "记忆检索",
     "arguments": "{\"query\": \"用户信息\"}"
   }
   ```

5. **toolCallEnd** - 工具调用结束（可选）
   ```json
   {
     "type": "toolCallEnd",
     "toolCallId": "call_123",
     "toolName": "searchMemory",
     "skillName": "记忆检索",
     "arguments": "{\"query\": \"用户信息\"}",
     "result": "{...}",
     "isError": false,
     "artifacts": [...]
   }
   ```

6. **chatEnd** - 回复完成
   ```json
   {
     "type": "chatEnd"
   }
   ```

---

### 3. history - 请求历史消息

加载指定会话的历史消息。

**请求**:
```json
{
  "type": "history",
  "size": 20,
  "beforeId": 150,
  "sessionId": 1001
}
```

| 字段        | 类型   | 必填 | 说明                      |
|-------------|--------|------|---------------------------|
| type        | string | 是   | 消息类型，固定为"history" |
| size        | number | 否   | 加载数量，默认 20         |
| beforeId    | number | 否   | 加载此 ID 之前的消息        |
| sessionId   | number | 否   | 聊天会话 ID               |

**响应**:
```json
{
  "type": "historyLoad",
  "size": 20,
  "beforeId": 150
}
```

---

### 4. audio - 语音识别请求

发送音频数据进行语音识别（ASR）。

**请求**:
```json
{
  "type": "audio",
  "data": "base64-encoded-audio-data",
  "mimeType": "audio/webm;codecs=opus"
}
```

| 字段        | 类型   | 必填 | 说明                      |
|-------------|--------|------|---------------------------|
| type        | string | 是   | 消息类型，固定为"audio"   |
| data        | string | 是   | Base64 编码的音频数据      |
| mimeType    | string | 是   | 音频 MIME 类型              |

**响应**:

- **识别成功**:
  ```json
  {
    "type": "asrResult",
    "text": "今天天气怎么样"
  }
  ```

- **识别失败**:
  ```json
  {
    "type": "asrError",
    "message": "语音识别失败：连接超时"
  }
  ```

---

### 5. ping - 心跳检测

保持连接活跃，检测连接状态。

**请求**:
```json
{
  "type": "ping"
}
```

**响应**:
```json
{
  "type": "pong"
}
```

---

### 6. error - 错误消息

服务端或客户端发送的错误信息。

```json
{
  "type": "error",
  "message": "详细错误信息"
}
```

---

## 服务端广播消息

### proactiveGreeting - 主动问候

服务端主动向用户发送问候。

```json
{
  "type": "proactiveGreeting",
  "content": "好久不见，最近过得怎么样？"
}
```

---

## 前端实现示例

### useWebSocket.ts 核心方法

```typescript
export function useWebSocket() {
  // 连接到 WebSocket
  function connect(url?: string)
  
  // 断开连接
  function disconnect()
  
  // 发送消息
  function send(message: WebSocketMessage)
  
  // 注册用户
  function register(uid: string, activeChatSessionId?: number | null)
  
  // 发送聊天消息
  function sendChat(
    message: string,
    agentId?: number,
    model?: string,
    apiKey?: string,
    baseUrl?: string,
    images?: string[],
    activeChatSessionId?: number | null,
  )
  
  // 请求历史消息
  function requestHistory(size: number = 20, beforeId?: number)
  
  // 发送心跳
  function ping()
  
  // 监听消息
  function on(type: string, handler: MessageHandler)
  
  // 移除监听
  function off(type: string, handler: MessageHandler)
  
  // 启动心跳检测
  function startHeartbeat(intervalMs: number = 30000)
}
```

### 使用示例

```typescript
const { connect, sendChat, on, disconnect } = useWebSocket();

// 建立连接
connect();

// 监听聊天片段
on('chatChunk', (message) => {
  appendAssistantChunk(message.content);
});

// 发送消息
sendChat('你好', undefined, 'Qwen/Qwen3-8B', apiKey, baseUrl);

// 清理
onUnmounted(() => disconnect());
```

---

## 后端实现

### ChatWebSocketHandler.java

**核心方法**:

- `afterConnectionEstablished()` - 连接建立
- `handleTextMessage()` - 处理文本消息
- `afterConnectionClosed()` - 连接关闭
- `handleRegister()` - 处理注册
- `handleChat()` - 处理聊天
- `handleHistory()` - 处理历史请求
- `handleAudio()` - 处理音频识别

**会话管理**:

```java
private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
private static final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
private static final Map<String, Long> sessionChatMap = new ConcurrentHashMap<>();
```

---

## 错误处理

### 常见错误码

| 错误类型          | 说明                           |
|-------------------|--------------------------------|
| 连接未打开        | WebSocket 未建立或已断开       |
| 消息不能为空      | chat 消息缺少 message 和 images |
| ASR 服务未启用    | 音频识别服务未配置             |
| 模型调用失败      | LLM 服务异常                   |
| 会话不存在        | sessionId 无效                 |

### 错误处理建议

1. **前端**: 监听 `error` 消息类型，显示友好提示
2. **重试机制**: 对可恢复错误实现指数退避重试
3. **日志记录**: 记录错误上下文便于排查

---

## 性能优化

### 消息大小限制

- **文本消息**: 建议不超过 10KB
- **图片**: 自动压缩，单张不超过 5MB
- **音频**: 建议不超过 10MB

### 连接数限制

- **单用户**: 建议最多 3 个并发连接
- **服务端**: 根据服务器配置调整

### 心跳间隔

- **推荐**: 30 秒
- **超时**: 90 秒无响应断开

---

## 安全建议

1. **生产环境**: 使用 WSS (WebSocket Secure)
2. **用户认证**: 通过 userId 验证用户身份
3. **输入校验**: 服务端校验所有输入数据
4. **频率限制**: 防止恶意请求（待实现）

---

## 相关文件

- **前端**: `frontend/src/composables/useWebSocket.ts`
- **后端**: `backend/lingshu-web/src/main/java/com/lingshu/ai/web/websocket/ChatWebSocketHandler.java`
- **配置**: `backend/lingshu-web/src/main/java/com/lingshu/ai/web/websocket/WebSocketConfig.java`

---

## 更新日志

| 日期       | 版本 | 更新内容                     |
|------------|------|------------------------------|
| 2026-04-26 | 1.0  | 初始版本，包含核心对话功能   |
