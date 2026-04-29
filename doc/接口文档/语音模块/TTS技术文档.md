# TTS (语音合成) 模块技术文档

本文档介绍灵枢 AI 系统中 TTS 模块的实现细节，包括后端接口定义、前端调用逻辑以及相关的流式播放策略。

## 1. 整体架构

TTS 模块采用 **前端分段请求 + 后端代理转发** 的架构。
- **前端**：负责将文本按标点符号进行切分，维护一个音频播放队列，支持流式增量合成，并控制播放状态。
- **后端**：作为一个轻量级的代理（Proxy），将前端的请求转发至具体的 TTS 引擎（如 ChatTTS, GPT-SoVITS, 或 OpenAI TTS），并透传音频流。

---

## 2. 后端接口定义

后端控制器位于 `com.lingshu.ai.web.controller.TtsController`。

### 2.1 语音合成接口 (Speak)

将文本转换为音频流。支持 `GET` 和 `POST` 方法。

- **Endpoint**: `/api/tts/speak`
- **Produces**: `application/octet-stream`

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `text` | String | 是 | 需要转换的文本。 |
| `seed` | Integer | 否 | 随机种子，默认从系统配置读取。若传 -1 则通常表示随机。 |

#### 内部实现逻辑
1. **配置读取**：从 `SettingService` 获取系统设置中的 `ttsConfig`。配置项包括 `baseUrl` (TTS 引擎地址), `apiKey`, `defaultVoice`, `defaultSpeed`, `defaultFormat` 等。
2. **上游转发**：构造符合 OpenAI 标准的 TTS 请求：
   - **URL**: `{baseUrl}/v1/audio/speech`
   - **Payload**: `{"model": "voxcpm-2", "input": text, "voice": voice, "speed": speed, "response_format": format, "seed": seed}`
3. **流式透传**：使用 `StreamingResponseBody` 将上游引擎返回的音频字节流实时写入 HTTP 响应体，减少内存占用。

---

## 3. 前端实现细节

前端核心逻辑位于 `useTts.ts` 组合式函数中。

### 3.1 文本分段策略
为了降低首包延迟（TTFB）并支持 LLM 的流式输出，前端采用了分段合成策略：
- 使用正则表达式 `/([。！？\.!\?\n]+)/` 将文本拆分为多个片段（Chunks）。
- 每个片段包含完整的语句及末尾标点。
- 在发送给后端前，会调用 `stripMarkdown` 移除文本中的 Markdown 标签（如代码块、加粗等），确保合成效果自然。

### 3.2 播放队列管理
- **并发控制**：设置了 `CONCURRENCY_LIMIT = 2`，同时预加载最多 2 个后续片段的音频。
- **状态维护**：每个 Chunk 有 `idle`, `loading`, `ready`, `error` 四种状态。
- **无缝衔接**：当当前片段播放结束（`onended` 事件触发）时，立即从队列中获取下一个 `ready` 状态的片段进行播放。

### 3.3 流式增量更新 (`appendText`)
针对 AI 的流式响应：
- `ChatWindow.vue` 监听消息内容变化。
- `appendText` 函数会对比当前已拆分的片段和最新的消息内容。
- 若有新的完整句子（带标点）产生，则立即将其加入 `chunks` 队列并开始预加载。

### 3.4 自动播放触发逻辑
- 仅当系统设置中的 `autoTtsEnabled` 为 `true` 时生效。
- 仅处理 AI 发送的消息。
- **历史记录过滤**：通过检查消息状态（`status === 'sending'`），确保只有实时生成的回复会触发自动语音，而加载历史消息时不会突然出声。

---

## 4. 相关配置项

在系统设置的 `ttsConfig` 中，可配置以下参数：

| 配置键 | 说明 |
| :--- | :--- |
| `baseUrl` | TTS 服务的基础 URL (例如 `http://localhost:5050`) |
| `apiKey` | 鉴权密钥 (如果使用的是 OpenAI 或第三方云服务) |
| `defaultVoice` | 默认音色 ID |
| `defaultSpeed` | 语速 (通常为 1.0) |
| `defaultFormat` | 输出格式 (mp3, wav 等) |
| `defaultSeed` | 默认种子值 |

---

## 5. 关键文件索引

- **后端控制器**: [TtsController.java](file:///e:/Project/LingShu-AI/backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/TtsController.java)
- **前端 Composable**: [useTts.ts](file:///e:/Project/LingShu-AI/lingshu-gui/src/composables/useTts.ts)
- **聊天集成组件**: [ChatWindow.vue](file:///e:/Project/LingShu-AI/lingshu-gui/src/components/chat/ChatWindow.vue)
