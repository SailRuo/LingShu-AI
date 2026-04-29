# ASR 语音识别技术文档

本文档详细说明了灵枢 AI (LingShu-AI) 项目中语音识别 (ASR) 模块的技术实现、接口调用方式及注意事项。

## 1. 模块概述

ASR 模块负责将用户的语音输入转换为文本指令。本项目支持两种前端形态：
- **JavaFX 桌面端 (fx-frontend)**: 直接调用 ASR 服务。
- **Web 端 (frontend)**: 通过 WebSocket 将音频传给后端，由后端转发至 ASR 服务。

## 2. 技术实现细节

### 2.1 JavaFX 前端 (fx-frontend)
- **核心类**: `com.lingshu.core.AsrService`
- **音频格式**: 16kHz, 16-bit, 单声道 (Mono), 小端序 (Little Endian), 有符号 PCM。
- **端点检测 (VAD)**:
    - **原理**: 计算音频帧的 RMS (均方根) 值。
    - **校准**: 启动时进行 `CALIBRATION_FRAMES` (50帧) 的环境噪音基准校准。
    - **动态阈值**: `threshold = Math.max(50, (int) (noiseFloor * vadMultiplier))`。
    - **自动结束**: 检测到持续 800ms 的静默且语音时长超过 300ms 时自动触发识别。
- **智能避让**: 提供 `setMuted(boolean)` 方法。当 TTS 播放时，ASR 进入静音模式，防止录入 AI 自身的声音。

### 2.2 Web 前端 (frontend)
- **核心逻辑**: `useAsr.ts`
- **采集**: 使用浏览器 `navigator.mediaDevices.getUserMedia`。
- **VAD**: 使用 `AudioContext` 和 `AnalyserNode` 获取 `Float32Array` 数据并计算 RMS。
- **传输**: 录音完成后，将音频转换为 Base64 字符串，通过 WebSocket 发送 `type: "audio"` 消息。

### 2.3 后端代理 (backend)
- **核心类**: `com.lingshu.ai.core.service.AsrService`
- **职责**: 接收 Web 端的 Base64 音频，根据 `mimeType` 处理（`webm`/`wav` 直接转发，PCM 转为 WAV），然后转发至 ASR 服务。

## 3. 接口调用与 URL 处理

### 3.1 ASR 服务接口
- **请求方式**: `POST`
- **Content-Type**: `multipart/form-data`
- **参数**: `file` (WAV 格式音频文件)
- **响应格式**: JSON
    ```json
    {
      "text": "识别出的文本内容"
    }
    ```
- **文本清洗**: 客户端/后端会过滤掉识别结果中的标签（如 `<|...|>`）。

### 3.2 URL 自动修正逻辑
项目会对配置的 `asrUrl` 进行自动修正：
1.  **协议转换**: `ws://` -> `http://`, `wss://` -> `https://`。
2.  **路径补全**: 若 URL 不包含 `/asr`，会自动追加 `/asr`。
3.  **前缀补全**: 若无协议头，默认补全 `http://`。

## 4. 注意事项

1.  **采样率一致性**: ASR 服务通常要求 16kHz 采样率，前端采集时需严格遵守。
2.  **灵敏度调节**: `vadMultiplier` (JavaFX) 或 `sensitivity` (Web) 可调节识别的灵敏度，需根据用户环境调整。
3.  **并发处理**: 识别请求采用异步调用 (`HttpClient.sendAsync`)，不会阻塞主线程。
4.  **静音逻辑**: 在实现新的 UI 交互时，务必确保在 AI 说话时调用 `setMuted(true)`。
