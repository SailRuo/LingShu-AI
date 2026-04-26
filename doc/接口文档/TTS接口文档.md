# TTS (Text-to-Speech) 后端 API 接口文档

本文档详细说明了灵枢 AI 系统中 TTS 模块的后端 REST API 接口规范。

## 1. 基础信息

- **Base URL**: `/api/tts`
- **控制器**: `com.lingshu.ai.web.controller.TtsController`
- **认证方式**: 依赖系统全局认证机制（如 Session/Token）

---

## 2. 接口列表

### 2.1 语音合成 (Speak)

将文本转换为音频流。该接口支持流式响应，适合实时播放或下载。

- **Endpoint**: `/api/tts/speak`
- **Method**: `GET` | `POST`
- **Produces**: `application/octet-stream`

#### 请求参数 (Query Parameters / Form Data)

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `text` | String | 是 | - | 待合成的文本内容。 |
| `seed` | Integer | 否 | 系统配置值 | 随机种子，用于控制合成音色的稳定性。 |

#### 响应说明

- **HTTP 200**: 返回音频二进制流（通常为 MP3 或 AAC 格式，取决于后端配置）。
- **HTTP 400**: 请求参数错误（如 text 为空）。
- **HTTP 500**: 后端合成服务异常或网络连接超时。

---

### 2.2 获取可用音色 (Get Voices)

查询当前 TTS 服务支持的所有音色列表。

- **Endpoint**: `/api/tts/voices`
- **Method**: `GET`
- **Produces**: `application/json`

#### 请求参数 (Query Parameters)

| 参数名 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `baseUrl` | String | 否 | 临时覆盖系统配置的 TTS 服务地址（用于调试）。 |
| `apiKey` | String | 否 | 临时覆盖系统配置的 API Key（用于调试）。 |

#### 响应示例 (HTTP 200)

```json
[
  {
    "id": "alloy",
    "name": "Alloy",
    "description": "Neutral and balanced voice"
  },
  {
    "id": "echo",
    "name": "Echo",
    "description": "Warm and deep voice"
  }
]
```

---

## 3. 后端内部逻辑与配置

### 3.1 转发逻辑
后端接收到请求后，会从 `SystemSetting` 中提取 `ttsConfig`，并构造符合 OpenAI TTS 标准的请求转发至上游服务：
- **上游 URL**: `{baseUrl}/v1/audio/speech`
- **默认模型**: `voxcpm-2`
- **超时设置**: 连接超时 10s，读取超时 1h（支持长文本流式传输）。

### 3.2 系统配置项 (ttsConfig)

| Key | 类型 | 说明 |
| :--- | :--- | :--- |
| `baseUrl` | String | 上游 TTS 服务的基础地址。 |
| `apiKey` | String | 上游服务的授权密钥。 |
| `defaultVoice` | String | 默认使用的音色 ID。 |
| `defaultSpeed` | Double | 默认语速 (0.25 - 4.0)。 |
| `defaultFormat` | String | 默认音频格式 (mp3, aac, etc.)。 |
| `defaultSeed` | Integer | 默认随机种子。 |
