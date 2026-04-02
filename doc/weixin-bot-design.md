# 微信 iLink Bot 接入设计与实施计划

## 1. 架构定位
本设计方案旨在将底层的 iLink 协议（微信官方为个人账号开放的 Bot 通讯通道）与 LingShu-AI 现有的基于 Spring WebFlux + Reactor 的 `ChatService` 核心进行无缝结合。为了不污染核心业务逻辑，将在 `lingshu-infrastructure` 模块下新增独立的适配层（Channel Adapter），负责将 iLink 的长轮询模型转换为对 `ChatService` 的方法调用，并将 AI 的响应转化为 iLink 的 HTTP 发送请求。

## 2. 核心组件设计

### 2.1 数据与存储扩展 (`SystemSetting`)
利用现有 `SystemSetting` 实体的 JSONB 存储特性，无缝扩展微信 Bot 的配置。此方案无需修改数据库表结构，且默认开启，不设额外的启用/禁用开关。

**JSON 结构设计：**
```json
{
  "wechatBot": {
    "botToken": "Bearer xxxxxxx...",
    "baseUrl": "https://ilinkai.weixin.qq.com",
    "status": "confirmed", // wait, scaned, confirmed, expired
    "lastLoginTime": "2024-04-02T10:00:00"
  }
}
```

### 2.2 身份与安全组件 (`ILinkSecurityManager`)
* **Token 管理**：维护 `bot_token` 的存储，从 `SystemSetting` 中读取和更新。
* **UIN 动态生成**：为每次 iLink 请求生成无符号 32 位整数并进行 Base64 编码，注入到 `X-WECHAT-UIN` 请求头中。
* **统一拦截器**：自动为所有对 iLink API 的调用附加 `Authorization`、`iLink-App-Id`、`X-WECHAT-UIN` 等必要的安全 Headers。

### 2.3 登录认证服务 (`WechatBotAuthService` / `WechatBotController`)
* **获取登录二维码**：向 iLink 接口 `/ilink/bot/get_bot_qrcode` 发起请求，返回唯一的 `qrcode` 及图片 URL 给前端展示。
* **状态轮询与确认**：通过 `/ilink/bot/get_qrcode_status` 轮询扫码状态。当状态为 `scaned_but_redirect` 时动态更新基础域名；当状态为 `confirmed` 时，提取并保存核心票据 `bot_token` 至 `SystemSetting`。

### 2.4 非流式对话适配接口 (`ChatController`)
在现有的 `ChatController` 中新增 `/api/chat/sync` 接口。
由于微信 Bot 的 `/sendmessage` 接口不支持类似 SSE 的流式传输，该接口将内部的 `Flux<String>` 响应通过 `.reduce(String::concat)` 聚合为完整的回复文本后再一次性返回，以满足 iLink 协议对 `message_state: 2`（完整消息）的要求。

### 2.5 消息通信闭环 (`ILinkMessagePoller` & `ILinkMessageDispatcher`)
* **长连接轮询 (Poller)**：后台常驻任务，持续调用 `/ilink/bot/getupdates`（携带 `get_updates_buf` 游标），并设置合理的读超时（如 40 秒），以获取微信用户的最新消息。
* **消息分发 (Dispatcher)**：
  1. 解析收到的文本消息，提取并保存关键的 `context_token` 和 `from_user_id`。
  2. 调用 iLink 的 `getconfig` 获取 `typing_ticket`，随后调用 `sendtyping(status: 1)` 向用户展示“对方正在输入...”。
  3. 调用后端的同步对话接口 (`/api/chat/sync`) 获取大模型的完整回复内容。
  4. 携带先前保存的 `context_token`，调用 iLink 的 `sendmessage` 将回复发送给用户，并随后调用 `sendtyping(status: 2)` 取消正在输入状态。

## 3. 前端界面设计 (`SettingsView.vue`)
在系统设置页的标签页中增加一个独立的 `微信 Bot (iLink)` Tab：
* **无开关设计**：接入逻辑默认始终处于准备状态。
* **状态展示区**：展示当前的授权状态（未登录/等待确认/已登录/已过期）、最后授权时间以及脱敏后的 `botToken`。
* **扫码授权区**：在未授权或授权过期时，提供“获取登录二维码”功能，展示二维码图片，并在扫码期间通过后端 API 进行状态轮询。一旦扫码并在手机上确认，页面状态自动刷新为“已授权”。

## 4. 实施规划
可以按照以下四个阶段进行渐进式实施：
1. **阶段一：核心数据与鉴权 (后端)**
   - 扩展 `SystemSetting.java`，增加 WeChat 相关的存取方法。
   - 实现鉴权拦截器、动态 UIN 算法、二维码获取及扫码状态轮询的逻辑，并提供对应的 REST API。
2. **阶段二：非流式对话接口 (后端)**
   - 在 `ChatController` 增加 `/api/chat/sync` 同步聚合接口。
3. **阶段三：长轮询与消息处理 (后端)**
   - 实现后台轮询任务拉取 iLink 消息，串联起“输入状态展示 -> 调用流式转非流式服务 -> 消息回复”的完整通信链路。
4. **阶段四：前端授权页面 (前端)**
   - 在 `SettingsView.vue` 中开发微信 Bot 配置面板，完成完整的前后端授权联调。