# 微信 iLink Bot API 接入指南 (Agent 专用)

本文档旨在为智能体 (Agent) 开发者提供全面、精简的微信 iLink Bot 协议接入指南，基于 `v2.1.3/v1.0.2` 协议逆向分析与实测整理。

## 1. 协议概览

iLink (智联) 协议是微信官方为个人账号开放的 Bot 通讯通道，完全基于 HTTP/JSON。

*   **API 基础域名**: `https://ilinkai.weixin.qq.com`
*   **通信模式**: 下行长轮询 (Long-Polling，最高保持 35s) + 上行实时推送。
*   **关键前置**: 所有请求均需要携带特定的身份与安全 Headers。

### 1.1 核心请求头 (Headers)

发送向 iLink 服务器的所有请求必须包含以下 Headers：

```json
{
  "Content-Type": "application/json",
  "AuthorizationType": "ilink_bot_token",
  "Authorization": "Bearer <bot_token>",  // 登录成功后获取，登录接口无需此项
  "iLink-App-Id": "bot",
  "iLink-App-ClientVersion": "131331",    // 或根据实际抓包使用的版本号
  "X-WECHAT-UIN": "<base64_encoded_uin>"  // 动态生成，防重放
}
```

> **注意 (`X-WECHAT-UIN` 生成算法)**：
> 每次请求必须随机生成一个无符号 32 位整数 (uint32)，将其转换为十进制字符串，再进行 Base64 编码。
> Python 示例：`base64.b64encode(str(random.randint(0, 0xFFFFFFFF)).encode()).decode()`

---

## 2. 登录生命周期 (Login Flow)

### 2.1 获取登录二维码
*   **请求**: `GET /ilink/bot/get_bot_qrcode?bot_type=3`
*   **响应返回**: `qrcode` (唯一标识), `qrcode_img_content` (二维码链接 URL，如 `https://liteapp...`)
*   **处理建议**: Agent 可将 `qrcode_img_content` URL 输出给用户点击扫码。

### 2.2 轮询扫码状态
*   **请求**: `GET /ilink/bot/get_qrcode_status?qrcode=<qrcode>`
*   **响应状态 (`status` 字段)**:
    1.  `wait`: 等待扫码。
    2.  `scaned`: 已扫码，等待确认。
    3.  `scaned_but_redirect`: **非常重要**，此时返回包含 `redirect_host`，后续请求必须重定向到该域名。
    4.  `confirmed`: 登录成功，返回核心票据 `bot_token` 以及基础 URL `baseurl`。后续所有接口需带上该 `bot_token`。

---

## 3. 消息交互核心链路

收到用户消息后回复的完整闭环：**收消息 (getupdates) -> 获取配置 (getconfig) -> 发送正在输入状态 (sendtyping) -> 发送消息 (sendmessage) -> 取消正在输入状态 (sendtyping)**。

### 3.1 接收消息 (长轮询)
*   **Endpoint**: `POST /ilink/bot/getupdates`
*   **Payload**:
```json
{
  "get_updates_buf": "<游标>", 
  "base_info": { "channel_version": "2.1.3" } 
}
```
*   **说明**: `get_updates_buf` 首次为空字符串 `""`，后续每次请求必须带上上一次返回的 `get_updates_buf`。服务器在无消息时会 Hold 住连接最长 35 秒。

### 3.2 发送前置：状态同步 (按需)
为了让手机端显示“对方正在输入...”，并在发消息时验证有效性，需调用以下接口。

1.  **获取 `typing_ticket` (针对每个用户首次交互时调用，有效期 24h)**
    *   **Endpoint**: `POST /ilink/bot/getconfig`
    *   **Payload**: `{"ilink_user_id": "<from_user_id>", "context_token": "<收到的token>", "base_info": {"channel_version": "2.1.3"}}`
2.  **设置输入状态**
    *   **Endpoint**: `POST /ilink/bot/sendtyping`
    *   **Payload**: `{"ilink_user_id": "<from_user_id>", "typing_ticket": "<获取到的ticket>", "status": 1}` (`status: 1` 正在输入，`2` 结束输入)

### 3.3 发送消息
*   **Endpoint**: `POST /ilink/bot/sendmessage`
*   **Payload 示例 (文本消息)**:
```json
{
  "msg": {
    "from_user_id": "", 
    "to_user_id": "<对方的 user_id>",
    "client_id": "bot-<当前时间戳>", 
    "message_type": 2,  // 2 表示由 Bot 发出
    "message_state": 2, // 2 表示 FINISH (完整消息)
    "context_token": "<必须与收到该用户的消息的 context_token 一致>",
    "item_list": [
      {
        "type": 1,
        "text_item": { "text": "这是 Agent 的回复" }
      }
    ]
  },
  "base_info": { "channel_version": "2.1.3" }
}
```

---

## 4. 核心数据结构与类型

### 4.1 Inbound 消息结构示例 (`getupdates` 列表元素)
```json
{
  "from_user_id": "xxx@im.wechat",
  "to_user_id": "xxx@im.bot",
  "message_type": 1,
  "context_token": "AARzJWAFAAABAAAAAAAp...", // 极度重要！
  "item_list": [
    {
      "type": 1,
      "text_item": { "text": "用户发的文本" }
    }
  ]
}
```

### 4.2 消息媒体类型 (`item_list[].type`)

| type | 说明 | 处理方式 |
| :--- | :--- | :--- |
| `1` | 文本 (`text_item`) | 读取/赋值 `text` 字段 |
| `2` | 图片 (`image_item`)| CDN AES-128-ECB 加密，下发可读取解密密钥，上传需带 `media` 引用及缩略图 `thumb_media` |
| `3` | 语音 (`voice_item`)| 自动转文本存放于 `text`，发送需 SILK 编码 (`encode_type: 6`) |
| `4` | 文件 (`file_item`) | 发送需提供 `media` 和 `file_name` (必须带后缀名) |
| `5` | 视频 (`video_item`)| 发送需带视频流引用 `media`、封面图 `thumb_media` 和时长 `play_length` (ms) |

---

## 5. 高级：多媒体文件处理机制

所有上传到 CDN 的二进制流必须使用 **AES-128-ECB** 模式加密，密钥为随机 16 字节。
1.  **申请上传**: `POST /ilink/bot/getuploadurl`
2.  **上传加密数据**: `POST` 投递数据至返回的 `upload_full_url`，CDN 域名一般为 `https://novac2c.cdn.weixin.qq.com/c2c`。
3.  **引用**: 在 `sendmessage` 中带上生成的 `aes_key` (Base64) 及相关的引用信息。

---

## 6. 核心踩坑与接入注意事项 (必读)

1.  **`context_token` 原样返回**：向用户发送消息时，**必须**携带最新一次收到该用户消息里的 `context_token`，不可遗漏或乱用，否则消息将被服务器丢弃！
2.  **只有第一条能回复成功？**：如果你发现 Agent 只有第一句话能回，后面全丢了，通常是因为漏掉了 `from_user_id: ""` 或忘记调用 `getconfig` 和 `sendtyping`。建议严格遵守流程 3.2。
3.  **`X-WECHAT-UIN` 动态性**：每次 API 调用必须重新生成并 Base64 编码，不能复用。
4.  **`application/octet-stream` 返回类型**：iLink 接口可能返回该 content-type 而不是 json，你的 HTTP Client 在解析 json 时可能报错（如 Python 的 `aiohttp` 需要 `content_type=None` 才能解析）。
5.  **生命周期与重连**：`bot_token` 有效期一般为 24 小时，Agent 需要设计心跳机制或定时任务（推荐提前 1 小时）通过日志或管理员消息提示重新扫码登录。
6.  **指令分发**：iLink 不自带菜单，一切以 Slash 命令 (如 `/help`) 在普通文本里匹配，应在 Agent 逻辑的**最前端**拦截命令，避免交给大模型产生消耗。