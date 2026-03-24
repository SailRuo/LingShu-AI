# 豆包 TTS WebSocket 流式 API 文档

本文档介绍了豆包 TTS (Text-to-Speech) 服务通过 WebSocket 提供的流式调用接口。

## 1. 接口信息

- **端点地址**: `/ws`
- **协议**: WebSocket (`ws://` 或 `wss://`)
- **连接示例**: `ws://localhost:8000/ws`

## 2. 请求格式

客户端连接成功后，需发送一个 JSON 字符串来开始合成：

```json
{
    "text": "这是一段需要转换为语音的文本",
    "speaker": "taozi",
    "speed": 0,
    "pitch": 0,
    "format": "aac",
    "cookies": "passport_csrf_token=...; ttwid=...; sessionid=..."
}
```

### 参数说明

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `text` | string | 是 | - | 待合成的文本 |
| `speaker` | string | 否 | `taozi` | 语音角色 ID 或简称（见下文） |
| `speed` | number | 否 | `0` | 语速，范围 `-1.0 ~ 1.0`，0 为正常 |
| `pitch` | number | 否 | `0` | 音调，范围 `-1.0 ~ 1.0`，0 为正常 |
| `format` | string | 否 | `aac` | 音频格式，支持 `aac`, `mp3` |
| `cookies` | string | 是 | - | 豆包官网的完整 Cookie 字符串 (用于验证) |

## 3. 响应格式

服务端将通过 WebSocket 发送两种类型的数据：

### A. 二进制数据 (Binary)
合成过程中的音频切片数据。客户端应按顺序接收并拼接或放入播放队列。

### B. JSON 消息
包含事件通知或错误信息：

- **句子开始**: `{"event": "sentence_start", "text": "当前处理的文字"}`
- **句子结束**: `{"event": "sentence_end"}`
- **合成完成**: `{"event": "finish"}`
- **错误通知**: `{"event": "error", "message": "错误描述"}`

## 4. 可用角色 (Speakers)

| 简称 | 完整 ID | 说明 |
| :--- | :--- | :--- |
| `taozi` | `zh_female_wenroutaozi_uranus_bigtts` | 桃子 (温柔女声) |
| `taozi_conv` | `zh_female_taozi_conversation_v4_wvae_bigtts` | 桃子 (对话女声) |
| `shuangkuai` | `zh_female_shuangkuai_emo_v3_wvae_bigtts` | 爽快女声 |
| `tianmei` | `zh_female_tianmei_conversation_v4_wvae_bigtts` | 甜美女声 |
| `qingche` | `zh_female_qingche_moon_bigtts` | 清澈女声 |
| `yangguang` | `zh_male_yangguang_conversation_v4_wvae_bigtts` | 阳光男声 |
| `chenwen` | `zh_male_chenwen_moon_bigtts` | 沉稳男声 |
| `rap` | `zh_male_rap_mars_bigtts` | 说唱男声 |
| `en_female` | `en_female_sarah_conversation_bigtts` | Sarah (英文女声) |
| `en_male` | `en_male_adam_conversation_bigtts` | Adam (英文男声) |

## 5. 调用流程示例 (JavaScript)

```javascript
const socket = new WebSocket('ws://localhost:8000/ws');
socket.binaryType = 'arraybuffer';

socket.onopen = () => {
    socket.send(json.stringify({
        text: "你好，世界",
        speaker: "taozi",
        cookies: "你的完整COOKIE"
    }));
};

socket.onmessage = (event) => {
    if (typeof event.data === 'string') {
        const msg = json.parse(event.data);
        console.log('事件:', msg.event, msg);
    } else {
        // 处理二进制音频块
        console.log('接收到音频切片:', event.data.byteLength);
    }
};
```
