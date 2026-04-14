# 豆包 TTS API 调用文档

本文档介绍如何在其他项目中调用豆包 TTS 服务。提供两种调用方式：

1. **HTTP REST API** - OpenAI 兼容接口，适合简单调用
2. **WebSocket API** - 流式接口，适合实时播放场景

---

## 基础信息

- **服务地址**: `http://localhost:8000`
- **认证方式**: Bearer Token (Cookie 字符串)

---

## 一、HTTP REST API (OpenAI 兼容)

### 1.1 合成语音

**请求**

```
POST /v1/audio/speech
Content-Type: application/json
Authorization: Bearer <your_cookie>
```

**请求体**

```json
{
    "model": "tts-1",
    "input": "你好，这是一段测试文本",
    "voice": "taozi",
    "speed": 1.0,
    "response_format": "aac"
}
```

**参数说明**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|:-----|:-----|:-----|:-------|:-----|
| `model` | string | 否 | `tts-1` | 模型名称，可选 `tts-1` 或 `tts-1-hd` |
| `input` | string | 是 | - | 待合成的文本 |
| `voice` | string | 否 | `taozi` | 语音角色简称 |
| `speed` | float | 否 | `1.0` | 语速，范围 `0.5 ~ 2.0`，1.0 为正常 |
| `response_format` | string | 否 | `aac` | 音频格式，目前仅支持 `aac` |

**响应**

- 成功: 返回音频流 (Content-Type: audio/aac)
- 失败: 返回 JSON 错误信息

**错误码**

| 状态码 | 说明 |
|:-------|:-----|
| 400 | 参数错误 (缺少 input) |
| 401 | 未提供 Cookie 或 Cookie 无效 |

### 1.2 获取可用模型

```
GET /v1/models
```

**响应**

```json
{
    "object": "list",
    "data": [
        {"id": "tts-1", "object": "model", "owned_by": "doubao"},
        {"id": "tts-1-hd", "object": "model", "owned_by": "doubao"}
    ]
}
```

### 1.3 获取可用音色

```
GET /v1/voices
```

**响应**

```json
{
    "object": "list",
    "data": [
        {"id": "taozi", "name": "taozi", "speaker_id": "zh_female_wenroutaozi_uranus_bigtts"},
        ...
    ]
}
```

---

## 二、WebSocket API (流式)

### 2.1 连接

```
ws://localhost:8000/ws
```

### 2.2 发送请求

连接成功后发送 JSON:

```json
{
    "text": "你好，世界",
    "speaker": "taozi",
    "speed": 0,
    "pitch": 0,
    "format": "aac",
    "cookies": "passport_csrf_token=xxx; sessionid=xxx; ..."
}
```

**参数说明**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|:-----|:-----|:-----|:-------|:-----|
| `text` | string | 是 | - | 待合成的文本 |
| `speaker` | string | 否 | `taozi` | 语音角色简称 |
| `speed` | float | 否 | `0` | 语速偏移，范围 `-1.0 ~ 1.0` |
| `pitch` | float | 否 | `0` | 音调偏移，范围 `-1.0 ~ 1.0` |
| `format` | string | 否 | `aac` | 音频格式 |
| `cookies` | string | 是 | - | 豆包官网完整 Cookie |

### 2.3 接收响应

服务端返回两种类型数据：

**二进制数据** - 音频切片，按顺序拼接即可播放

**JSON 消息**

```json
{"event": "sentence_start", "text": "当前句子"}
{"event": "sentence_end"}
{"event": "finish"}
{"event": "error", "message": "错误描述"}
```

---

## 三、可用音色

| 简称 | 完整 ID | 说明 |
|:-----|:---------|:-----|
| `taozi` | `zh_female_wenroutaozi_uranus_bigtts` | 桃子 (温柔女声) |
| `taozi_conv` | `zh_female_taozi_conversation_v4_wvae_bigtts` | 桃子 (对话女声) |
| `shuangkuai` | `zh_female_shuangkuai_emo_v3_wvae_bigtts` | 爽快女声 |
| `tianmei` | `zh_female_tianmei_conversation_v4_wvae_bigtts` | 甜美女声 |
| `qingche` | `zh_female_qingche_moon_bigtts` | 清澈女声 |
| `huopo_kexin` | `zh_female_F765_mars_bigtts` | 活泼可昕 (女声) |
| `yangguang` | `zh_male_yangguang_conversation_v4_wvae_bigtts` | 阳光男声 |
| `chenwen` | `zh_male_chenwen_moon_bigtts` | 沉稳男声 |
| `rap` | `zh_male_rap_mars_bigtts` | 说唱男声 |
| `en_female` | `en_female_sarah_conversation_bigtts` | Sarah (英文女声) |
| `en_male` | `en_male_adam_conversation_bigtts` | Adam (英文男声) |

---

## 四、调用示例

### 4.1 Python (HTTP)

```python
import requests

url = "http://localhost:8000/v1/audio/speech"
headers = {
    "Authorization": "Bearer YOUR_COOKIE_HERE",
    "Content-Type": "application/json"
}
data = {
    "model": "tts-1",
    "input": "你好，这是一段测试文本",
    "voice": "taozi",
    "speed": 1.0
}

response = requests.post(url, json=data, headers=headers)

if response.status_code == 200:
    with open("output.aac", "wb") as f:
        f.write(response.content)
    print("音频已保存到 output.aac")
else:
    print(f"错误: {response.text}")
```

### 4.2 Python (WebSocket 流式)

```python
import asyncio
import websockets
import json

async def synthesize():
    uri = "ws://localhost:8000/ws"
    audio_chunks = []
    
    async with websockets.connect(uri) as ws:
        await ws.send(json.dumps({
            "text": "你好，这是流式合成测试",
            "speaker": "taozi",
            "speed": 0,
            "cookies": "YOUR_COOKIE_HERE"
        }))
        
        while True:
            message = await ws.recv()
            
            if isinstance(message, bytes):
                audio_chunks.append(message)
            else:
                data = json.loads(message)
                event = data.get("event")
                
                if event == "finish":
                    break
                elif event == "error":
                    print(f"错误: {data.get('message')}")
                    break
                elif event == "sentence_start":
                    print(f"正在合成: {data.get('text', '')[:30]}...")
    
    audio_data = b"".join(audio_chunks)
    with open("output_stream.aac", "wb") as f:
        f.write(audio_data)
    print(f"完成，音频大小: {len(audio_data)} bytes")

asyncio.run(synthesize())
```

### 4.3 JavaScript/Node.js (HTTP)

```javascript
const fs = require('fs');

async function synthesize() {
    const response = await fetch('http://localhost:8000/v1/audio/speech', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer YOUR_COOKIE_HERE'
        },
        body: JSON.stringify({
            model: 'tts-1',
            input: '你好，这是测试文本',
            voice: 'taozi',
            speed: 1.0
        })
    });

    if (response.ok) {
        const buffer = await response.arrayBuffer();
        fs.writeFileSync('output.aac', Buffer.from(buffer));
        console.log('音频已保存到 output.aac');
    } else {
        const error = await response.text();
        console.error('错误:', error);
    }
}

synthesize();
```

### 4.4 JavaScript/Node.js (WebSocket 流式)

```javascript
const WebSocket = require('ws');
const fs = require('fs');

const ws = new WebSocket('ws://localhost:8000/ws');
const chunks = [];

ws.on('open', () => {
    ws.send(JSON.stringify({
        text: '你好，这是流式合成测试',
        speaker: 'taozi',
        speed: 0,
        cookies: 'YOUR_COOKIE_HERE'
    }));
});

ws.on('message', (data, isBinary) => {
    if (isBinary) {
        chunks.push(data);
    } else {
        const msg = JSON.parse(data);
        if (msg.event === 'finish') {
            const audio = Buffer.concat(chunks);
            fs.writeFileSync('output.aac', audio);
            console.log(`完成，大小: ${audio.length} bytes`);
            ws.close();
        } else if (msg.event === 'error') {
            console.error('错误:', msg.message);
        }
    }
});
```

### 4.5 cURL

```bash
curl -X POST "http://localhost:8000/v1/audio/speech" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_COOKIE_HERE" \
  -d '{
    "model": "tts-1",
    "input": "你好，这是测试文本",
    "voice": "taozi",
    "speed": 1.0
  }' \
  --output output.aac
```

### 4.6 OpenAI SDK 兼容

由于接口兼容 OpenAI，可以直接使用 OpenAI SDK：

```python
from openai import OpenAI

client = OpenAI(
    api_key="YOUR_COOKIE_HERE",
    base_url="http://localhost:8000/v1"
)

response = client.audio.speech.create(
    model="tts-1",
    voice="taozi",
    input="你好，这是使用 OpenAI SDK 的测试",
    speed=1.0
)

response.stream_to_file("output.mp3")
```

---

## 五、获取 Cookie

1. 打开浏览器访问 https://www.doubao.com 并登录
2. 按 F12 打开开发者工具
3. 切换到 **Network** 标签
4. 刷新页面，点击任意请求
5. 在 **Headers** 中找到 **Cookie** 字段
6. 复制完整 Cookie 值

Cookie 格式示例：
```
passport_csrf_token=xxx; ttwid=xxx; sessionid=xxx; sid_tt=xxx; ...
```

---

## 六、注意事项

1. **Cookie 有效期**: Cookie 会过期，需要定期更新
2. **并发限制**: 建议控制并发请求数量
3. **文本长度**: 单次请求建议不超过 2000 字符
4. **网络超时**: WebSocket 默认 30 秒超时
