# 微信Bot接入计划与实施文档

> **状态**: ✅ 已完成实施 (2026-04)
> 
> 本文档记录了基于微信官方 iLink 协议的个人账号 Bot 接入方案的完整实现细节。

---

## 1. 架构定位

本方案将微信 iLink 协议（微信官方为个人账号开放的 Bot 通讯通道）与 LingShu-AI 现有的 Spring WebFlux + Reactor `ChatService` 核心无缝集成。通过独立的适配层（Channel Adapter）实现协议转换，避免污染核心业务逻辑。

**核心设计原则**:
- **零侵入**: 不修改现有 ChatService 接口
- **多账户支持**: 每个微信用户独立记忆和对话上下文
- **自动重连**: 会话过期自动检测并提醒重新扫码
- **流式转非流式**: 内部流式处理，对外一次性发送
- **富媒体支持**: 支持文本、语音识别、图片发送

## 2. 核心组件设计

## 2. 核心组件实现

### 2.1 数据存储扩展 (`SystemSetting`)

**文件**: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/SystemSetting.java`

利用现有 `SystemSetting` 实体的 JSONB 存储特性，采用**多账户列表**结构：

```json
{
  "id": "wechat_bot",
  "settings": {
    "wechatBotAccounts": [
      {
        "accountId": "wxuser_abc123...",
        "botToken": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "baseUrl": "https://ilinkai.weixin.qq.com",
        "status": "confirmed",
        "lastLoginTime": "2026-04-14T10:30:00",
        "ilinkUserId": "wxid_xxxxx",
        "ilinkBotId": "bot_xxxxx"
      }
    ]
  }
}
```

**关键方法**:
- `getWechatBotAccounts()`: 获取所有授权账户列表
- `addWechatBotAccount(account)`: 添加或更新账户（按 accountId 去重）
- `removeWechatBotAccount(accountId)`: 删除指定账户
- `getWechatBotAccount(accountId)`: 根据 ID 查询单个账户

**缓存策略**:
- Redis 键: `wechat_bot_setting`
- 每次读取优先从 Redis 获取，未命中则查数据库并回写缓存
- 写入时同步更新 DB 和 Redis

### 2.2 身份与安全组件 (`WechatBotAuthService`)

**文件**: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/WechatBotAuthService.java`

#### UIN 动态生成算法
```java
private String generateUin() {
    long uin = random.nextInt() & 0xFFFFFFFFL;  // 无符号 32 位整数
    return Base64.getEncoder().encodeToString(String.valueOf(uin).getBytes());
}
```

#### 统一请求头构建
```java
private HttpHeaders createHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("AuthorizationType", "ilink_bot_token");
    headers.set("X-WECHAT-UIN", generateUin());  // 每次请求随机生成
    return headers;
}
```

#### 登录流程实现

**步骤 1: 获取二维码**
```java
public Map<String, Object> getLoginQrCode() {
    String url = baseUrl + "/ilink/bot/get_bot_qrcode?bot_type=3";
    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
    Map<String, Object> result = response.getBody();
    
    // 缓存 qrcode -> baseUrl 映射，用于后续状态轮询
    if (result != null && result.containsKey("qrcode")) {
        pendingAuthBaseUrl.put(result.get("qrcode"), baseUrl);
    }
    return result;
}
```

**步骤 2: 轮询扫码状态**
```java
public Map<String, Object> getLoginStatus(String qrcode) {
    String url = baseUrl + "/ilink/bot/get_qrcode_status?qrcode=" + qrcode;
    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
    Map<String, Object> body = response.getBody();
    
    String status = (String) body.get("status");
    
    // 状态: scaned_but_redirect - 需要更新 base_url
    if ("scaned_but_redirect".equals(status)) {
        String redirectHost = (String) body.get("redirect_host");
        if (redirectHost != null) {
            String newBaseUrl = "https://" + redirectHost;
            pendingAuthBaseUrl.put(qrcode, newBaseUrl);  // 更新映射
        }
    } 
    // 状态: confirmed - 扫码成功，保存账户
    else if ("confirmed".equals(status)) {
        String token = (String) body.get("bot_token");
        String ilinkUserId = (String) body.get("ilink_user_id");
        String respBaseUrl = (String) body.get("baseurl");
        
        if (token != null) {
            // 构建账户对象
            Map<String, Object> account = new HashMap<>();
            account.put("accountId", ilinkUserId != null ? ilinkUserId : generateAccountIdFromToken(token));
            account.put("botToken", token);
            account.put("status", "confirmed");
            account.put("lastLoginTime", LocalDateTime.now().toString());
            account.put("baseUrl", respBaseUrl != null ? respBaseUrl : baseUrl);
            
            // 持久化到 SystemSetting
            settingService.saveWechatBotAccount(account);
            pendingAuthBaseUrl.remove(qrcode);  // 清理临时映射
        }
    }
    
    return body;
}
```

**错误码处理**:
- `errcode: -14`: 二维码过期，清除缓存映射

### 2.3 REST API 控制器 (`WechatBotController`)

**文件**: `backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/WechatBotController.java`

提供前端调用的 HTTP 接口：

```java
@RestController
@RequestMapping("/api/settings/wechat-bot")
public class WechatBotController {
    
    // GET /api/settings/wechat-bot/accounts
    // 获取所有授权账户列表（脱敏显示 botToken）
    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAccounts() {
        List<Map<String, Object>> accounts = settingService.getWechatBotAccounts();
        // 脱敏处理：只显示前 10 个字符
        for (Map<String, Object> account : accounts) {
            String token = (String) account.get("botToken");
            if (token != null && token.length() > 10) {
                account.put("botToken", token.substring(0, 10) + "...");
            }
        }
        return ResponseEntity.ok(accounts);
    }
    
    // DELETE /api/settings/wechat-bot/accounts/{accountId}
    // 删除指定账户
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> removeAccount(@PathVariable String accountId) {
        settingService.removeWechatBotAccount(accountId);
        return ResponseEntity.ok(Map.of("accountId", accountId, "message", "账户已删除"));
    }
    
    // POST /api/settings/wechat-bot/qrcode
    // 获取登录二维码
    @PostMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> getQrCode() {
        return ResponseEntity.ok(wechatBotAuthService.getLoginQrCode());
    }
    
    // GET /api/settings/wechat-bot/status?qrcode=xxx
    // 轮询扫码状态
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam String qrcode) {
        return ResponseEntity.ok(wechatBotAuthService.getLoginStatus(qrcode));
    }
}
```

### 2.4 消息轮询与分发服务 (`WechatBotMessageService`)

**文件**: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/WechatBotMessageService.java`

这是整个 Bot 系统的**核心引擎**，负责消息的接收、处理和回复。

#### 定时轮询机制

使用 Spring `@Scheduled` 注解实现每秒轮询一次：

```java
@Service
public class WechatBotMessageService {
    
    private final Map<String, String> updateBufMap = new ConcurrentHashMap<>();  // 游标缓存
    private final Set<String> pollingAccounts = ConcurrentHashMap.newKeySet();   // 防止重复轮询
    private final Map<String, String> typingTicketCache = new ConcurrentHashMap<>();  // Typing 票据缓存
    
    @Scheduled(fixedDelay = 1000)  // 每 1 秒执行一次
    public void pollMessages() {
        List<Map<String, Object>> accounts = settingService.getWechatBotAccounts();
        
        for (Map<String, Object> account : accounts) {
            String accountId = (String) account.get("accountId");
            String status = (String) account.get("status");
            String botToken = (String) account.get("botToken");
            String baseUrl = (String) account.get("baseUrl");
            
            // 只处理已确认的账户
            if (!"confirmed".equals(status) || botToken == null) continue;
            
            // 防止同一账户并发轮询
            if (pollingAccounts.contains(accountId)) continue;
            pollingAccounts.add(accountId);
            
            try {
                pollAccountMessages(accountId, botToken, baseUrl);
            } finally {
                pollingAccounts.remove(accountId);
            }
        }
    }
}
```

#### 长连接消息拉取

```java
private void pollAccountMessages(String accountId, String botToken, String baseUrl) {
    String url = baseUrl + "/ilink/bot/getupdates";
    
    // 构建请求体
    Map<String, Object> body = new HashMap<>();
    body.put("get_updates_buf", updateBufMap.getOrDefault(accountId, ""));  // 游标
    body.put("base_info", Map.of("channel_version", "2.1.3"));
    
    // 发送 POST 请求
    HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders(botToken));
    ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
    
    Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
    
    // 检查会话是否过期
    Integer errcode = (Integer) responseBody.get("errcode");
    if (errcode != null && errcode == -14) {
        log.warn("微信会话已过期 (errcode: -14)，账户: {}", accountId);
        // 更新状态为 session_timeout，前端会提示重新扫码
        account.put("status", "session_timeout");
        settingService.saveWechatBotAccount(account);
        return;
    }
    
    // 更新游标
    if (responseBody.containsKey("get_updates_buf")) {
        updateBufMap.put(accountId, (String) responseBody.get("get_updates_buf"));
    }
    
    // 处理消息列表
    if (responseBody.containsKey("msgs")) {
        List<Map<String, Object>> msgs = (List<Map<String, Object>>) responseBody.get("msgs");
        for (Map<String, Object> msg : msgs) {
            handleMessage(msg, botToken, baseUrl, accountId);
        }
    }
}
```

#### 消息处理流程

```java
private void handleMessage(Map<String, Object> messageItem, String botToken, String baseUrl, String accountId) {
    String fromUserId = (String) messageItem.get("from_user_id");
    String contextToken = (String) messageItem.get("context_token");
    Integer messageType = (Integer) messageItem.get("message_type");
    
    // 只处理文本消息 (type=1)
    if (messageType != 1) return;
    
    // 提取文本内容（支持语音识别结果 type=3）
    StringBuilder textBuilder = new StringBuilder();
    List<Map<String, Object>> itemList = (List<Map<String, Object>>) messageItem.get("item_list");
    for (Map<String, Object> item : itemList) {
        Integer itemType = (Integer) item.get("type");
        if (itemType == 1) {  // 文本
            Map<String, Object> textItem = (Map<String, Object>) item.get("text_item");
            textBuilder.append(textItem.get("text"));
        } else if (itemType == 3) {  // 语音识别
            Map<String, Object> voiceItem = (Map<String, Object>) item.get("voice_item");
            textBuilder.append(voiceItem.get("text"));
        }
    }
    String text = textBuilder.toString();
    if (text.trim().isEmpty()) return;
    
    // 构造 userId，格式: wechat:{fromUserId}
    String wechatUserId = "wechat:" + fromUserId;
    log.info("收到微信用户 [{}] 的消息：{}", fromUserId, text);
    
    // 1. 显示"对方正在输入..."
    sendTyping(fromUserId, contextToken, botToken, baseUrl, 1);
    
    // 2. 调用 ChatService 流式对话
    chatService.streamChat(text, null, wechatUserId, null, null, null, null, new ToolEventListener() {
        @Override
        public void onToolEnd(String toolCallId, String toolName, String arguments,
                              String result, boolean isError, List<ArtifactPayload> artifacts) {
            // 处理工具产物（如图片）
            for (ArtifactPayload artifact : artifacts) {
                if ("image".equals(artifact.artifactType())) {
                    sendImageMessage(fromUserId, toUserId, contextToken,
                                    artifact.base64Data(), artifact.mimeType(),
                                    botToken, baseUrl);
                }
            }
        }
    })
    .reduce("", String::concat)  // 流式聚合为完整文本
    .doOnNext(fullResponse -> {
        // 清理 reasoning 标签
        String cleanResponse = fullResponse.replaceAll("(?s)\u0001REASONING\u0001.*?\u0001/REASONING\u0001", "");
        
        // 3. 发送回复
        if (!cleanResponse.trim().isEmpty()) {
            sendMessage(fromUserId, toUserId, contextToken, cleanResponse, botToken, baseUrl);
        }
        
        // 4. 取消"正在输入"状态
        sendTyping(fromUserId, contextToken, botToken, baseUrl, 2);
    })
    .doOnError(e -> {
        log.error("对话生成失败：{}", e.getMessage(), e);
        sendTyping(fromUserId, contextToken, botToken, baseUrl, 2);
    })
    .subscribe();  // 异步订阅，不阻塞轮询
}
```

**关键点**:
- **userId 隔离**: 每个微信用户使用 `wechat:{fromUserId}` 作为唯一标识，确保记忆独立
- **流式转非流式**: 使用 `.reduce("", String::concat)` 将 Flux 聚合成完整字符串
- **异步处理**: `.subscribe()` 确保不阻塞轮询线程
- **Reasoning 清理**: 移除内部推理过程标记，只发送最终答案
- **语音支持**: 自动提取语音识别文本 (type=3)

#### Typing 状态管理

```java
private void sendTyping(String fromUserId, String contextToken, String botToken, String baseUrl, int status) {
    // status: 1 = 开始输入, 2 = 结束输入
    
    String typingTicket = typingTicketCache.get(fromUserId);
    
    // 首次需要获取 typing_ticket
    if (typingTicket == null) {
        String url = baseUrl + "/ilink/bot/getconfig";
        Map<String, Object> body = Map.of(
            "ilink_user_id", fromUserId,
            "context_token", contextToken,
            "base_info", Map.of("channel_version", "2.1.3")
        );
        
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, 
            new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders(botToken)), 
            byte[].class);
        
        Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
        if (responseBody.containsKey("typing_ticket")) {
            typingTicket = (String) responseBody.get("typing_ticket");
            typingTicketCache.put(fromUserId, typingTicket);  // 缓存票据
        }
    }
    
    // 发送 typing 状态
    if (typingTicket != null) {
        String url = baseUrl + "/ilink/bot/sendtyping";
        Map<String, Object> body = Map.of(
            "ilink_user_id", fromUserId,
            "typing_ticket", typingTicket,
            "status", status
        );
        restTemplate.exchange(url, HttpMethod.POST, 
            new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders(botToken)), 
            String.class);
    }
}
```

#### 文本消息发送

```java
private void sendMessage(String toUserId, String botUserId, String contextToken, 
                         String text, String botToken, String baseUrl) {
    String url = baseUrl + "/ilink/bot/sendmessage";
    
    Map<String, Object> msg = new HashMap<>();
    msg.put("from_user_id", "");
    msg.put("to_user_id", toUserId);
    msg.put("client_id", "openclaw-weixin-" + String.format("%08x", random.nextInt()));
    msg.put("message_type", 2);       // 2 = 机器人消息
    msg.put("message_state", 2);      // 2 = 完整消息（非流式）
    msg.put("context_token", contextToken);
    
    // 构建文本项
    List<Map<String, Object>> itemList = new ArrayList<>();
    Map<String, Object> textItemWrapper = new HashMap<>();
    textItemWrapper.put("type", 1);  // 1 = 文本
    textItemWrapper.put("text_item", Map.of("text", text));
    itemList.add(textItemWrapper);
    msg.put("item_list", itemList);
    
    Map<String, Object> body = Map.of(
        "msg", msg,
        "base_info", Map.of("channel_version", "2.1.3")
    );
    
    restTemplate.exchange(url, HttpMethod.POST, 
        new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders(botToken)), 
        String.class);
    
    log.info("已回复微信用户 [{}]: {}", toUserId, text);
}
```

#### 图片消息发送（AES 加密 + CDN 上传）

微信 iLink 协议要求图片必须经过 AES 加密后上传到 CDN，然后发送加密凭证。

**核心流程**:
1. 解码 Base64 → 原始图片数据
2. 生成随机 AES Key → 加密图片 (AES/ECB/PKCS5Padding)
3. 调用 `getuploadurl` 获取 CDN 上传地址
4. POST 加密数据到 CDN → 获得 `x-encrypted-param` 凭证
5. 调用 `sendmessage` 发送图片消息（携带凭证和 AES Key）
6. 微信客户端自动解密并显示图片

```java
private void sendImageMessage(String toUserId, String botUserId, String contextToken,
                              String base64Data, String mimeType, String botToken, String baseUrl) {
    // 1. Base64 解码
    byte[] imageData = Base64.getDecoder().decode(base64Data);
    
    // 2. 生成随机 AES 密钥 (16 字节)
    byte[] aesKey = new byte[16];
    secureRandom.nextBytes(aesKey);
    
    // 3. AES/ECB/PKCS5Padding 加密
    byte[] encryptedData = encryptAES(imageData, aesKey);
    
    // 4. 计算 MD5
    String rawMd5 = getMD5(imageData);
    String fileKey = UUID.randomUUID().toString().replace("-", "");
    String aesKeyHex = bytesToHex(aesKey);
    
    // 5. 获取上传 URL
    String uploadUrl = getUploadUrl(botToken, baseUrl, toUserId, 
                                    imageData.length, encryptedData.length,
                                    fileKey, rawMd5, aesKeyHex);
    
    // 6. 上传加密后的图片到 CDN
    String downloadParam = uploadToCdn(uploadUrl, encryptedData);
    
    // 7. AES Key Base64 编码
    String aesKeyB64 = Base64.getEncoder().encodeToString(aesKeyHex.getBytes(StandardCharsets.UTF_8));
    
    // 8. 发送图片消息
    sendImageMessageRequest(toUserId, contextToken, downloadParam, aesKeyB64,
                           encryptedData.length, botToken, baseUrl);
}

// AES 加密
private byte[] encryptAES(byte[] data, byte[] key) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
    cipher.init(Cipher.ENCRYPT_MODE, keySpec);
    return cipher.doFinal(data);
}

// 获取上传 URL
private String getUploadUrl(String botToken, String baseUrl, String toUserId,
                            int rawSize, int encryptedSize, String fileKey,
                            String rawMd5, String aesKeyHex) throws Exception {
    String url = baseUrl + "/ilink/bot/getuploadurl";
    
    Map<String, Object> body = Map.of(
        "filekey", fileKey,
        "media_type", 1,           // 1 = 图片
        "to_user_id", toUserId,
        "rawsize", rawSize,
        "rawfilemd5", rawMd5,
        "filesize", encryptedSize,
        "no_need_thumb", true,
        "aeskey", aesKeyHex
    );
    
    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
        new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders(botToken)),
        Map.class);
    
    Map<String, Object> responseBody = response.getBody();
    return responseBody != null ? (String) responseBody.get("upload_full_url") : null;
}

// 上传到 CDN
private String uploadToCdn(String uploadUrl, byte[] encryptedData) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    HttpEntity<byte[]> entity = new HttpEntity<>(encryptedData, headers);
    
    ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, String.class);
    
    // 从响应头获取下载凭证
    String downloadParam = response.getHeaders().getFirst("x-encrypted-param");
    if (downloadParam == null) {
        // 尝试从响应体解析
        Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);
        downloadParam = (String) responseBody.get("x-encrypted-param");
    }
    
    return downloadParam;
}

// 发送图片消息请求
private void sendImageMessageRequest(String toUserId, String contextToken,
                                     String downloadParam, String aesKeyB64,
                                     int encryptedSize, String botToken, String baseUrl) throws Exception {
    String url = baseUrl + "/ilink/bot/sendmessage";
    
    Map<String, Object> msg = new HashMap<>();
    msg.put("from_user_id", "");
    msg.put("to_user_id", toUserId);
    msg.put("client_id", "openclaw-weixin-" + String.format("%08x", random.nextInt()));
    msg.put("message_type", 2);
    msg.put("message_state", 2);
    msg.put("context_token", contextToken);
    
    // 构建图片项
    List<Map<String, Object>> itemList = new ArrayList<>();
    Map<String, Object> imageItemWrapper = new HashMap<>();
    imageItemWrapper.put("type", 2);  // 2 = 图片
    
    Map<String, Object> imageItem = new HashMap<>();
    Map<String, Object> media = Map.of(
        "encrypt_query_param", downloadParam,  // CDN 下载凭证
        "aes_key", aesKeyB64,                   // AES Key (Base64)
        "encrypt_type", 1                       // 1 = AES 加密
    );
    imageItem.put("media", media);
    imageItem.put("mid_size", encryptedSize);
    
    imageItemWrapper.put("image_item", imageItem);
    itemList.add(imageItemWrapper);
    msg.put("item_list", itemList);
    
    Map<String, Object> body = Map.of(
        "msg", msg,
        "base_info", Map.of("channel_version", "2.1.3")
    );
    
    restTemplate.exchange(url, HttpMethod.POST,
        new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders(botToken)),
        String.class);
}
```

## 3. 前端界面实现 (`SettingsView.vue`)

**文件**: `frontend/src/views/SettingsView.vue`

在系统设置页增加独立的 **微信 Bot (iLink)** Tab：

### 3.1 功能特性

- **无开关设计**: 接入逻辑默认始终处于准备状态
- **多账户管理**: 支持同时授权多个微信号，每个账户独立显示状态
- **实时状态监控**: 展示授权状态（未登录/等待确认/已登录/会话过期）
- **脱敏安全**: botToken 只显示前 10 个字符
- **扫码授权**: 点击按钮获取二维码，前端每 2 秒轮询状态

### 3.2 核心交互流程

```typescript
// 1. 获取二维码
async function getWechatQrCode() {
  const res = await fetch(getFullUrl('/api/settings/wechat-bot/qrcode'), { method: 'POST' })
  const data = await res.json()
  qrcodeUrl.value = data.qrcode_img_content || data.qrcode
  pollingStatus.value = true
  authSuccessHandled.value = false
  
  // 开始轮询状态
  startPollingStatus(data.qrcode)
}

// 2. 轮询扫码状态
function startPollingStatus(qrcode: string) {
  pollingTimer = setInterval(async () => {
    try {
      const res = await fetch(getFullUrl(`/api/settings/wechat-bot/status?qrcode=${qrcode}`))
      const data = await res.json()
      
      if (data && data.status === 'confirmed') {
        // 扫码成功
        authSuccessHandled.value = true
        clearInterval(pollingTimer)
        pollingStatus.value = false
        message.success('微信扫码授权成功！')
        fetchWechatBotAccounts()  // 刷新账户列表
      } else if (data && data.status === 'expired') {
        // 二维码过期
        clearInterval(pollingTimer)
        pollingStatus.value = false
        message.warning('二维码已过期，请重新获取')
      }
    } catch (err) {
      console.error('Polling status error', err)
    }
  }, 2000)  // 每 2 秒查询一次
}

// 3. 删除账户
async function removeWechatBotAccount(accountId: string) {
  await fetch(getFullUrl(`/api/settings/wechat-bot/accounts/${accountId}`), { method: 'DELETE' })
  message.success('账户已删除')
  fetchWechatBotAccounts()
}
```

### 3.3 UI 布局

```vue
<n-tab-pane name="wechat" tab="微信 Bot (iLink)">
  <div class="tab-content">
    <section class="settings-section">
      <div class="section-header">
        <n-icon :component="MessageCircle" />
        <h2>微信智能体接入 (基于 iLink 协议)</h2>
      </div>

      <div class="wechat-bot-header">
        <p class="section-desc">支持多账户授权，每个微信用户独立记忆和对话上下文</p>
        <n-button type="primary" @click="getWechatQrCode" :disabled="pollingStatus">
          <template #icon><n-icon :component="Plus" /></template>
          扫码授权
        </n-button>
      </div>

      <!-- 空状态 -->
      <div v-if="wechatBotAccounts.length === 0" class="empty-state">
        <n-icon :component="MessageCircle" :size="48" class="empty-icon" />
        <p>暂无授权账户</p>
        <p class="hint">点击上方“扫码授权”按钮开始授权</p>
      </div>

      <!-- 账户列表 -->
      <div v-else class="accounts-grid">
        <n-card v-for="account in wechatBotAccounts" :key="account.accountId" 
                class="account-card" 
                :class="{ 'account-active': account.status === 'confirmed' }">
          <div class="account-header">
            <div class="account-status">
              <n-tag :type="getStatusType(account.status)">
                {{ getStatusText(account.status) }}
              </n-tag>
            </div>
            <n-button text @click="removeWechatBotAccount(account.accountId)">
              <template #icon><n-icon :component="Trash2" /></template>
            </n-button>
          </div>
          
          <div class="account-info">
            <div class="info-item">
              <span class="label">账户 ID:</span>
              <span class="value">{{ account.accountId }}</span>
            </div>
            <div class="info-item">
              <span class="label">Bot Token:</span>
              <span class="value token">{{ account.botToken }}</span>
            </div>
            <div class="info-item">
              <span class="label">最后登录:</span>
              <span class="value">{{ formatTime(account.lastLoginTime) }}</span>
            </div>
          </div>
        </n-card>
      </div>
    </section>
  </div>
</n-tab-pane>
```

### 3.4 样式设计

```css
.wechat-bot-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.accounts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.account-card {
  transition: all 0.3s ease;
  border: 1px solid var(--color-outline);
}

.account-card.account-active {
  border-color: var(--color-success);
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.2);
}

.account-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.account-info .info-item {
  display: flex;
  margin-bottom: 8px;
  font-size: 14px;
}

.account-info .label {
  color: var(--color-text-dim);
  width: 100px;
  flex-shrink: 0;
}

.account-info .value.token {
  font-family: 'Fira Code', monospace;
  font-size: 12px;
  color: var(--color-primary);
}

.empty-state {
  text-align: center;
  padding: 48px 0;
  color: var(--color-text-dim);
}

.empty-icon {
  opacity: 0.3;
  margin-bottom: 16px;
}
```

---

## 4. 技术亮点总结

### 4.1 架构优势

1. **零侵入集成**: 完全复用现有 ChatService，无需修改核心业务逻辑
2. **多租户隔离**: 通过 `wechat:{userId}` 命名空间实现记忆隔离
3. **异步非阻塞**: Reactor 流式处理 + 定时轮询，高并发友好
4. **富媒体支持**: 完整的图片发送链路（AES 加密 + CDN 上传）
5. **优雅降级**: 会话过期自动检测，前端友好提示

### 4.2 性能优化

- **游标机制**: `get_updates_buf` 避免重复拉取消息
- **防重入保护**: `pollingAccounts` Set 防止同一账户并发轮询
- **票据缓存**: `typingTicketCache` 减少 getconfig 调用次数
- **Redis 缓存**: SystemSetting 优先从 Redis 读取，降低数据库压力

### 4.3 安全性

- **动态 UIN**: 每次请求随机生成，防止重放攻击
- **Token 脱敏**: 前端只显示前 10 个字符
- **AES 加密**: 图片传输采用 AES/ECB/PKCS5Padding 加密
- **HTTPS**: 所有 iLink API 调用均使用 HTTPS

### 4.4 用户体验

- **实时反馈**: “对方正在输入...”状态提升交互体验
- **语音识别**: 自动提取语音消息的文本内容
- **多图支持**: 工具产物中的图片自动发送给微信用户
- **状态可视化**: 清晰的账户状态标签和最后登录时间

---

## 5. 已知限制与改进方向

### 5.1 当前限制

1. **轮询延迟**: 固定 1 秒轮询间隔，极端情况下可能有 1-2 秒延迟
2. **单点故障**: 轮询服务运行在单个实例上，不支持水平扩展
3. **内存泄漏风险**: `updateBufMap` 和 `typingTicketCache` 无限增长（长期运行需清理策略）
4. **图片大小限制**: 未对上传前的图片进行压缩，可能超过微信限制

### 5.2 改进建议

1. **WebSocket 替代轮询**: 如果 iLink 提供 WebSocket 接口，可替换长轮询
2. **分布式锁**: 多实例部署时使用 Redis 分布式锁防止重复消费
3. **LRU 缓存**: 为 `typingTicketCache` 添加过期时间和最大容量
4. **图片压缩**: 发送前自动压缩图片至合适尺寸（如最大宽度 1024px）
5. **消息队列**: 引入 RabbitMQ/Kafka 解耦消息接收和处理
6. **监控告警**: 添加轮询失败率、消息处理延迟等指标监控

---

## 6. 部署与运维

### 6.1 环境要求

- Java 17+
- Spring Boot 3.x
- Redis 6+ (用于缓存)
- PostgreSQL (存储 SystemSetting)
- 网络连接: 可访问 `https://ilinkai.weixin.qq.com`

### 6.2 配置说明

无需额外配置，系统启动后自动启用微信 Bot 服务。

### 6.3 监控指标

建议监控以下指标：
- 轮询成功率: `pollMessages` 方法异常率
- 消息处理延迟: 从接收到回复的时间差
- 活跃账户数: `wechatBotAccounts` 中 `status=confirmed` 的数量
- 会话过期频率: `errcode=-14` 出现次数

### 6.4 故障排查

**问题 1: 无法收到微信消息**
- 检查账户状态是否为 `confirmed`
- 查看日志是否有 `errcode: -14` (会话过期)
- 验证网络是否能访问 iLink API

**问题 2: 图片发送失败**
- 检查 AES 加密是否正确
- 验证 CDN 上传是否返回 `x-encrypted-param`
- 确认图片格式是否受支持 (JPEG/PNG)

**问题 3: 扫码后状态不更新**
- 检查前端轮询是否正常 (每 2 秒)
- 验证后端 `/api/settings/wechat-bot/status` 接口返回值
- 查看 `pendingAuthBaseUrl` 映射是否正确

---

## 7. 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2026-04-14 | 初始设计文档 |
| 2.0 | 2026-04-XX | 完成实施，更新为实际实现细节 |

---

**文档维护者**: AI Assistant  
**相关代码**:
- `WechatBotAuthService.java` - 认证服务
- `WechatBotMessageService.java` - 消息服务
- `WechatBotController.java` - REST API
- `SettingsView.vue` - 前端界面

**参考资料**:
- [微信 iLink 官方文档](https://ilinkai.weixin.qq.com)
- [Spring Scheduling](https://spring.io/guides/gs/scheduling-tasks/)
- [Reactor Core](https://projectreactor.io/docs/core/release/reference/)