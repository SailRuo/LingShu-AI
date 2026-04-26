# 灵枢之境 - 微信风格三栏式聊天布局设计文档

> 版本：v1.0  
> 日期：2026-04-25  
> 技术栈：Vue 3 + TypeScript + @arco-design/web-vue + Tauri 2

---

## 一、整体架构概览

### 1.1 布局结构（三栏式）

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用窗口 (1366×768)                      │
├──────┬──────────────────┬──────────────────────────────────────┤
│      │                  │                                      │
│ 导航栏 │   会话列表       │           聊天窗口                   │
│ (60px)│  (320-360px)     │         (剩余空间)                   │
│      │                  │                                      │
│ ───  │ ─────────────── │ ────────────────────────────────────  │
│ 图标  │ 搜索框           │ 聊天头部（标题栏）                     │
│ 列表  │ 会话项列表        │ 用户名 / 状态 / 操作按钮              │
│      │                  │                                      │
│      │                  │ ────────────────────────────────────  │
│      │                  │                                      │
│      │                  │          消息列表区域                 │
│      │                  │    （可滚动，自动滚动到底部）            │
│      │                  │                                      │
│      │                  │ ────────────────────────────────────  │
│      │                  │                                      │
│      │                  │          输入区域                    │
│      │                  │  工具栏 + 文本输入框 + 发送按钮         │
└──────┴──────────────────┴──────────────────────────────────────┘
```

### 1.2 组件层级树

```
App.vue (根组件)
└── MainLayout.vue (主布局容器)
    ├── SidebarNav.vue (左侧导航栏)
    │   ├── NavItem.vue (导航项)
    │   └── UserAvatar.vue (用户头像)
    ├── ConversationList.vue (会话列表)
    │   ├── SearchBar.vue (搜索框)
    │   ├── ConversationItem.vue (会话项) × N
    │   └── EmptyState.vue (空状态)
    └── ChatWindow.vue (聊天窗口)
        ├── ChatHeader.vue (聊天头部)
        │   ├── UserInfo.vue (用户信息)
        │   └── ActionButtons.vue (操作按钮组)
        ├── MessageList.vue (消息列表)
        │   ├── MessageBubble.vue (消息气泡) × N
        │   ├── SystemMessage.vue (系统消息)
        │   ├── TimeDivider.vue (时间分割线)
        │   └── ScrollToBottomButton.vue (回到底部按钮)
        └── InputArea.vue (输入区域)
            ├── ToolBar.vue (工具栏)
            │   ├── EmojiPicker.vue (表情选择器)
            │   ├── FileUploader.vue (文件上传)
            │   ├── ImageUploader.vue (图片上传)
            │   └── VoiceRecorder.vue (语音录制)
            ├── TextInput.vue (文本输入框)
            └── SendButton.vue (发送按钮)
```

---

## 二、详细布局规范

### 2.1 左侧导航栏 (SidebarNav)

**尺寸规格：**
```
宽度：60px（固定）
高度：100vh（全屏高度）
背景色：#EDEDED（浅灰）
边框：右侧 1px solid #D9D9D9
```

**图标规格：**
```
图标尺寸：26×26 px
点击区域：48×48 px（居中）
图标间距：垂直居中分布
激活态：绿色 #07C160 背景 + 白色图标
悬停态：#D9D9D9 背景
默认态：#333333 图标颜色
```

**导航项定义：**

| 序号 | 图标名称 | 功能说明 | 路由/状态 |
|------|---------|---------|----------|
| 1 | `message` | 消息（聊天） | 默认选中 |
| 2 | `contacts` | 通讯录 | - |
| 3 | `starred` | 收藏 | - |
| 4 | `folder` | 文件 | - |
| 5 | `settings` | 设置 | - |

**底部用户头像区：**
```
位置：固定在底部
距底部：20px
头像尺寸：36×36 px
点击效果：弹出个人菜单
未读标记：右上角红点（4px）
```

**TypeScript 接口定义：**
```typescript
interface NavItem {
  id: string;
  icon: string;           // icon 名称或 SVG path
  label: string;          // 显示文本（用于 tooltip）
  badge?: number;         // 未读数量
  active?: boolean;       // 是否激活
  route?: string;         // 路由路径
}

interface SidebarProps {
  items: NavItem[];
  activeId: string;
  userAvatar?: string;
  onItemSelect: (id: string) => void;
}
```

---

### 2.2 会话列表 (ConversationList)

**尺寸规格：**
```
默认宽度：320px
最小宽度：280px
最大宽度：400px（可拖拽调整）
背景色：#F7F7F7
边框：右侧 1px solid #E0E0E0
```

**搜索栏 (SearchBar)：**
```
位置：顶部固定
高度：54px
内边距：8px 12px
搜索框：
  高度：34px
  圆角：6px
  背景色：#E6E6E6
  占位符："搜索"
  左侧图标：🔍（16px）
  右侧图标：➕（添加新会话）
```

**会话项 (ConversationItem)：**
```
高度：72px（含 padding）
内边距：12px 16px
布局：水平排列（左头像 + 中间内容 + 右时间）

左侧：
  头像尺寸：48×48 px
  圆角：6px（方形圆角）
  未读标记：
    类型1：数字角标（红色圆形，18px，白色文字）
    类型2：静音图标（灰色小喇叭）

中间内容区：
  昵称：
    字体大小：15px
    字重：500
    颜色：#191919
    最大宽度：180px（单行截断）
  预览消息：
    字体大小：13px
    颜色：#999999（已读）/ #191919（未读）
    最大宽度：200px（单行截断）
    行高：20px

右侧时间区：
  时间：
    字体大小：11px
    颜色：#B2B2B2
  未读计数：
    背景：#FA5151
    文字：白色
    尺寸：18px 圆形
    最小宽度：18px
    内边距：2px 6px
    圆形显示
```

**会话项状态变体：**

| 状态 | 背景色 | 边框 | 特殊标识 |
|------|-------|------|---------|
| 选中 | #EBEBEB | 无 | 左侧 3px 绿色竖线 |
| 悬停 | #F0F0F0 | 无 | - |
| 未读 | #FFFFFF | 无 | 预览文字加粗 |
| 置顶 | #F5F5F5 | 底部分割线 | 顶部置顶标记 |
| 免打扰 | 同普通 | - | 预览消息后加 🔕 |

**TypeScript 接口定义：**
```typescript
interface Conversation {
  id: string;
  avatar: string;             // 头像 URL 或 base64
  name: string;               // 显示名称
  lastMessage: string;        // 最后一条消息预览
  timestamp: Date;            // 最后消息时间
  unreadCount: number;        // 未读数（0 则不显示）
  isPinned: boolean;          // 是否置顶
  isMuted: boolean;           // 是否免打扰
  onlineStatus?: 'online' | 'offline' | 'away';  // 在线状态
  type: 'chat' | 'group' | 'system';  // 会话类型
  metadata?: Record<string, any>;  // 扩展元数据
}

interface ConversationListProps {
  conversations: Conversation[];
  selectedId: string | null;
  searchQuery: string;
  onSelect: (id: string) => void;
  onSearch: (query: string) => void;
  onContextMenu: (id: string, event: MouseEvent) => void;
}
```

---

### 2.3 聊天窗口 (ChatWindow)

**尺寸规格：**
```
宽度：flex: 1（占据剩余空间）
最小宽度：500px
背景色：#F5F5F5（消息区） / #FFFFFF（输入区）
```

#### 2.3.1 聊天头部 (ChatHeader)

```
高度：56px
背景色：#F7F7F7
边框：底部 1px solid #E0E0E0
内边距：0 20px
布局：水平居中（标题居中，两侧按钮）

左侧区域：
  返回按钮（仅移动端/窄屏显示）
  用户名/群名
    字体大小：17px
    字重：600
    颜色：#191919
  在线状态指示器
    在线：绿点（8px）
    离线：灰点（8px）

右侧操作按钮组：
  按钮：语音通话、视频通话、更多（...）
  图标尺寸：24px
  点击区域：36×36 px
  颜色：#666666
  悬停：#07C160
```

**TypeScript 接口定义：**
```typescript
interface ChatHeaderProps {
  conversation: Conversation | null;
  showBackButton: boolean;
  onVoiceCall?: () => void;
  onVideoCall?: () => void;
  onMoreActions?: () => void;
  onBack?: () => void;
}
```

#### 2.3.2 消息列表 (MessageList)

**尺寸规格：**
```
高度：calc(100vh - 56px - 150px)（减去头部和输入区）
背景色：#F5F5F5
溢出：auto（可滚动）
padding：16px 20px
gap：12px（消息间距）
```

**消息气泡 (MessageBubble)：**

**发送方（右侧）：**
```
对齐方式：flex-end（右对齐）
气泡样式：
  最大宽度：60%
  背景色：#95EC69（微信绿）
  圆角：8px（左上 8px，其余 4px）
  内边距：10px 14px
  文字：
    字体大小：15px
    颜色：#000000
    行高：22px
  时间戳：
    位置：气泡下方右侧
    字体大小：11px
    颜色：#B2B2B2
  发送状态：
    成功：无标记
    发送中：⏳（旋转动画）
    失败：❗（红色感叹号，可点击重发）
```

**接收方（左侧）：**
```
对齐方式：flex-start（左对齐）
布局：水平排列（头像 + 气泡）

头像：
  尺寸：40×40 px
  圆角：6px
  与气泡间距：10px
  margin-top：4px（与气泡顶部对齐）

气泡样式：
  最大宽度：60%
  背景色：#FFFFFF
  圆角：8px（右上 8px，其余 4px）
  内边距：10px 14px
  边框：1px solid #E5E5E5
  阴影：0 1px 2px rgba(0,0,0,0.05)
  文字：
    字体大小：15px
    颜色：#000000
    行高：22px
  发送者昵称（群聊时显示）：
    字体大小：13px
    颜色：#576B95
    margin-bottom：4px
    可点击跳转
```

**特殊消息类型：**

| 消息类型 | 样式描述 | 组件 |
|---------|---------|------|
| 图片 | 缩略图 + 点击放大 | `ImageMessage.vue` |
| 文件 | 图标 + 文件名 + 大小 | `FileMessage.vue` |
| 语音 | 波形图 + 时长 + ▶️ | `VoiceMessage.vue` |
| 视频 | 缩略图 + ▶️ + 时长 | `VideoMessage.vue` |
| 链接 | 标题 + 描述 + 缩略图卡片 | `LinkMessage.vue` |
| 引用 | 被引用消息摘要 + 新内容 | `QuoteMessage.vue` |
| 系统 | 居中灰色文字 | `SystemMessage.vue` |

**时间分割线 (TimeDivider)：**
```
样式：居中显示
文字格式："HH:mm" 或 "昨天 HH:mm" 或 "MM月DD日 HH:mm"
字体大小：12px
颜色：#B2B2B2
背景：半透明白色（可选）
margin：16px auto
padding：4px 12px
border-radius：4px
```

**TypeScript 接口定义：**
```typescript
type MessageType = 'text' | 'image' | 'file' | 'voice' | 'video' 
                | 'link' | 'quote' | 'system';

interface BaseMessage {
  id: string;
  type: MessageType;
  senderId: string;
  senderName: string;
  senderAvatar?: string;
  timestamp: Date;
  status: 'sending' | 'sent' | 'delivered' | 'read' | 'failed';
  isSelf: boolean;  // 是否是自己发的
}

interface TextMessage extends BaseMessage {
  type: 'text';
  content: string;
}

interface ImageMessage extends BaseMessage {
  type: 'image';
  url: string;
  width: number;
  height: number;
  thumbnailUrl?: string;
}

interface FileMessage extends BaseMessage {
  type: 'file';
  fileName: string;
  fileSize: number;
  fileType: string;
  url: string;
}

// ... 其他消息类型类似扩展

interface MessageListProps {
  messages: BaseMessage[];
  currentUserId: string;
  isLoading?: boolean;
  onLoadMore?: () => void;  // 加载更多历史消息
  onRetrySend?: (id: string) => void;  // 重发失败消息
  onImageClick?: (url: string) => void;
}
```

#### 2.3.3 输入区域 (InputArea)

**尺寸规格：**
```
高度：自适应（最小 120px，最大 200px）
背景色：#F7F7F7
边框：顶部 1px solid #E0E0E0
padding：12px 20px
```

**工具栏 (ToolBar)：**
```
高度：32px
布局：水平排列，左对齐
按钮间距：8px
图标尺寸：24px
颜色：#666666
悬停：#07C160

按钮列表：
  😊 表情（打开表情面板）
  📎 文件（选择文件上传）
  🖼️ 图片（选择图片上传）
  ✂️ 截图（调用 Tauri 截图 API）
  🎤 语音（长按录音）
```

**文本输入框 (TextInput)：**
```
最小高度：36px
最大高度：120px
背景色：#FFFFFF
圆角：4px
边框：1px solid #E0E0E0
聚焦边框：1px solid #07C160
placeholder："输入消息..."
字体大小：15px
行高：22px
padding：8px 12px
resize: none（禁止手动调整大小）
自动增高（随内容增加高度）
```

**发送按钮 (SendButton)：**
```
位置：右下角
尺寸：50×36 px
背景色：#07C160
文字："发送"
字体大小：14px
颜色：#FFFFFF
圆角：4px
禁用态：#C0C0C0（无内容时）
悬停：#06AD56
点击：#069A4D
```

**TypeScript 接口定义：**
```typescript
interface InputAreaProps {
  value: string;
  disabled?: boolean;
  placeholder?: string;
  maxLength?: number;
  onChange: (value: string) => void;
  onSend: (message: string) => void;
  onEmojiSelect?: (emoji: string) => void;
  onFileUpload?: (files: File[]) => void;
  onImageUpload?: (images: File[]) => void;
  onScreenshot?: () => void;
  onStartRecording?: () => void;
  onStopRecording?: () => void;
}
```

---

## 三、主题与色彩系统

### 3.1 设计令牌 (Design Tokens)

```css
/* ====== 全局 CSS 变量 ====== */

/* 布局尺寸 */
--sidebar-width: 60px;
--conversation-list-width: 320px;
--chat-header-height: 56px;
--input-area-min-height: 120px;

/* 主色调 */
--color-primary: #07C160;           /* 微信绿 */
--color-primary-hover: #06AD56;
--color-primary-active: #069A4D;
--color-primary-light: #E8F8EE;

/* 背景色 */
--bg-sidebar: #EDEDED;
--bg-conversation-list: #F7F7F7;
--bg-chat-window: #F5F5F5;
--bg-input-area: #F7F7F7;
--bg-message-self: #95EC69;
--bg-message-other: #FFFFFF;

/* 文字色 */
--text-primary: #191919;
--text-secondary: #666666;
--text-tertiary: #999999;
--text-placeholder: #B2B2B2;
--text-link: #576B95;

/* 边框 */
--border-color: #E0E0E0;
--border-color-dark: #D9D9D9;

/* 状态色 */
--color-success: #07C160;
--color-warning: #FFA940;
--color-error: #FA5151;
--color-info: #1890FF;

/* 阴影 */
--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 12px rgba(0, 0, 0, 0.08);
--shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.12);

/* 圆角 */
--radius-sm: 4px;
--radius-md: 6px;
--radius-lg: 8px;
--radius-xl: 12px;
--radius-full: 50%;

/* 过渡动画 */
--transition-fast: 0.15s ease;
--transition-normal: 0.25s ease;
--transition-slow: 0.35s ease;
```

### 3.2 暗黑模式支持

```css
[data-theme="dark"] {
  --bg-sidebar: #2E2E2E;
  --bg-conversation-list: #222222;
  --bg-chat-window: #171717;
  --bg-input-area: #222222;
  --bg-message-self: #3D8C41;
  --bg-message-other: #2E2E2E;
  
  --text-primary: #E5E5E5;
  --text-secondary: #A6A6A6;
  --text-tertiary: #737373;
  
  --border-color: #404040;
  --border-color-dark: #4A4A4A;
}
```

---

## 四、交互规范

### 4.1 动画时长

| 交互类型 | 时长 | 缓动函数 |
|---------|------|---------|
| 悬停反馈 | 150ms | ease-out |
| 展开/收起 | 250ms | cubic-bezier(0.4, 0, 0.2, 1) |
| 页面切换 | 300ms | cubic-bezier(0.4, 0, 0.2, 1) |
| 消息出现 | 300ms | ease-out（带弹性） |
| 模态框弹出 | 350ms | cubic-bezier(0.4, 0, 0.2, 1) |
| 加载动画 | 1000ms | linear（循环） |

### 4.2 微交互细节

**消息气泡进入动画：**
```css
@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-bubble {
  animation: messageSlideIn 0.3s ease-out both;
}
```

**打字指示器动画：**
```css
.typing-indicator span {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  margin: 0 2px;
  animation: typingBounce 1.4s infinite both;
}

.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}
```

### 4.3 快捷键绑定

| 快捷键 | 功能 | 作用域 |
|-------|------|-------|
| `Enter` | 发送消息 | 输入框聚焦时 |
| `Shift+Enter` | 换行 | 输入框聚焦时 |
| `Ctrl+F` | 聚焦搜索框 | 全局 |
| `Ctrl+N` | 新建会话 | 全局 |
| `Escape` | 关闭弹窗/取消 | 全局 |
| `↑/↓` | 切换会话 | 会话列表聚焦时 |
| `PageUp/PageDown` | 滚动消息历史 | 聊天窗口聚焦时 |

---

## 五、响应式与自适应

### 5.1 断点定义

```typescript
const BREAKPOINTS = {
  mobile: 480,       // 手机
  tablet: 768,       // 平板
  desktop: 1024,     // 桌面
  wide: 1366,        // 宽屏（当前默认）
} as const;
```

### 5.2 自适应策略

**宽屏 (>1024px)：**
- 三栏完整展示
- 会话列表可拖拽调整宽度（280px - 400px）

**平板 (768px - 1024px)：**
- 会话列表收窄至 280px
- 聊天窗口保持正常

**手机 (<768px)：**
- 单栏模式（只显示一个面板）
- 导航栏变为底部 TabBar
- 会话列表和聊天窗口切换显示
- 返回按钮可见

### 5.3 会话列表宽度可调整

```vue
<template>
  <div class="conversation-list" :style="{ width: listWidth + 'px' }">
    <!-- 内容 -->
    <div 
      class="resize-handle" 
      @mousedown="startResize"
    ></div>
  </div>
</template>

<script setup lang="ts">
const listWidth = ref(320);
const MIN_WIDTH = 280;
const MAX_WIDTH = 400;
const isResizing = ref(false);

function startResize(e: MouseEvent) {
  isResizing.value = true;
  document.addEventListener('mousemove', handleResize);
  document.addEventListener('mouseup', stopResize);
}

function handleResize(e: MouseEvent) {
  if (!isResizing.value) return;
  const newWidth = e.clientX - 60; // 减去 sidebar 宽度
  listWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, newWidth));
}

function stopResize() {
  isResizing.value = false;
  document.removeEventListener('mousemove', handleResize);
  document.removeEventListener('mouseup', stopResize);
}
</script>

<style scoped>
.resize-handle {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
}

.resize-handle:hover {
  background: var(--color-primary);
}
</style>
```

---

## 六、状态管理架构

### 6.1 Pinia Store 定义

```typescript
// stores/chat.ts
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useChatStore = defineStore('chat', () => {
  // 状态
  const conversations = ref<Conversation[]>([]);
  const currentConversationId = ref<string | null>(null);
  const messages = ref<Map<string, BaseMessage[]>>(new Map());
  const isLoadingMessages = ref(false);
  const searchQuery = ref('');
  const activeNav = ref('message');

  // 计算属性
  const currentConversation = computed(() =>
    conversations.value.find(c => c.id === currentConversationId.value) || null
  );
  
  const currentMessages = computed(() =>
    currentConversationId.value 
      ? messages.value.get(currentConversationId.value) || [] 
      : []
  );

  const filteredConversations = computed(() => {
    if (!searchQuery.value) return conversations.value;
    const query = searchQuery.value.toLowerCase();
    return conversations.value.filter(c =>
      c.name.toLowerCase().includes(query) ||
      c.lastMessage.toLowerCase().includes(query)
    );
  });

  // Actions
  async function loadConversations() { /* ... */ }
  async function selectConversation(id: string) { /* ... */ }
  async function loadMessages(conversationId: string) { /* ... */ }
  async function sendMessage(content: string) { /* ... */ }
  async function retrySendMessage(messageId: string) { /* ... */ }
  function setSearchQuery(query: string) { /* ... */ }

  return {
    conversations,
    currentConversationId,
    messages,
    isLoadingMessages,
    searchQuery,
    activeNav,
    currentConversation,
    currentMessages,
    filteredConversations,
    loadConversations,
    selectConversation,
    loadMessages,
    sendMessage,
    retrySendMessage,
    setSearchQuery,
  };
});
```

### 6.2 WebSocket 连接管理

```typescript
// composables/useWebSocket.ts
export function useWebSocket(url: string) {
  const socket = ref<WebSocket | null>(null);
  const isConnected = ref(false);
  const reconnectAttempts = ref(0);
  const maxReconnectAttempts = 5;

  function connect() {
    socket.value = new WebSocket(url);
    
    socket.value.onopen = () => {
      isConnected.value = true;
      reconnectAttempts.value = 0;
    };

    socket.value.onclose = () => {
      isConnected.value = false;
      if (reconnectAttempts.value < maxReconnectAttempts) {
        setTimeout(connect, 1000 * (reconnectAttempts.value + 1));
        reconnectAttempts.value++;
      }
    };
    
    socket.value.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  function send(data: unknown) {
    if (socket.value?.readyState === WebSocket.OPEN) {
      socket.value.send(JSON.stringify(data));
    }
  }

  function disconnect() {
    socket.value?.close();
    socket.value = null;
  }

  onUnmounted(disconnect);

  return { socket, isConnected, connect, send, disconnect };
}
```

---

## 七、目录结构规划

```
src/
├── App.vue                          # 根组件
├── main.ts                         # 入口文件
├── vite-env.d.ts                   # Vite 类型声明
│
├── assets/                         # 静态资源
│   ├── icons/                      # 图标资源
│   ├── images/                     # 图片资源
│   └── styles/                     # 全局样式
│       ├── variables.css           # CSS 变量（Design Tokens）
│       ├── reset.css               # 重置样式
│       ├── base.css                # 基础样式
│       └── animations.css          # 动画库
│
├── components/                     # 公共组件
│   ├── layout/                     # 布局组件
│   │   ├── MainLayout.vue          # 主布局容器
│   │   ├── SidebarNav.vue          # 左侧导航栏
│   │   ├── ConversationList.vue    # 会话列表
│   │   └── ChatWindow.vue          # 聊天窗口
│   │
│   ├── chat/                       # 聊天相关组件
│   │   ├── ChatHeader.vue          # 聊天头部
│   │   ├── MessageList.vue         # 消息列表
│   │   ├── MessageBubble.vue       # 消息气泡
│   │   ├── InputArea.vue           # 输入区域
│   │   ├── SearchBar.vue           # 搜索框
│   │   ├── ConversationItem.vue    # 会话项
│   │   ├── TimeDivider.vue         # 时间分割线
│   │   ├── TypingIndicator.vue     # 打字指示器
│   │   └── ScrollToBottom.vue      # 回到底部按钮
│   │
│   ├── message-types/              # 消息类型组件
│   │   ├── TextMessage.vue         # 文本消息
│   │   ├── ImageMessage.vue        # 图片消息
│   │   ├── FileMessage.vue         # 文件消息
│   │   ├── VoiceMessage.vue        # 语音消息
│   │   ├── VideoMessage.vue        # 视频消息
│   │   ├── LinkMessage.vue         # 链接消息
│   │   ├── QuoteMessage.vue        # 引用消息
│   │   └── SystemMessage.vue       # 系统消息
│   │
│   ├── input/                      # 输入相关组件
│   │   ├── ToolBar.vue             # 工具栏
│   │   ├── TextInput.vue           # 文本输入框
│   │   ├── SendButton.vue          # 发送按钮
│   │   ├── EmojiPicker.vue         # 表情选择器
│   │   ├── FileUploader.vue        # 文件上传
│   │   ├── ImageUploader.vue       # 图片上传
│   │   └── VoiceRecorder.vue       # 语音录制
│   │
│   └── common/                     # 通用组件
│       ├── Avatar.vue              # 头像组件
│       ├── Badge.vue               # 徽标组件
│       ├── EmptyState.vue          # 空状态
│       ├── LoadingSpinner.vue      # 加载动画
│       └── ContextMenu.vue         # 右键菜单
│
├── composables/                    # 组合式函数
│   ├── useChat.ts                  # 聊天逻辑
│   ├── useWebSocket.ts             # WebSocket 管理
│   ├── useTheme.ts                 # 主题切换
│   ├── useKeyboardShortcuts.ts     # 快捷键
│   └── useAutoResize.ts            # 自动调整大小
│
├── stores/                         # Pinia 状态管理
│   ├── chat.ts                     # 聊天状态
│   ├── ui.ts                       # UI 状态
│   └── user.ts                     # 用户状态
│
├── types/                          # TypeScript 类型定义
│   ├── chat.ts                     # 聊天相关类型
│   ├── message.ts                  # 消息类型
│   ├── conversation.ts             # 会话类型
│   └── api.ts                      # API 类型
│
├── utils/                          # 工具函数
│   ├── date.ts                     # 日期格式化
│   ├── emoji.ts                    # 表情解析
│   ├── file.ts                     # 文件处理
│   └── validators.ts               # 验证函数
│
├── api/                            # API 接口
│   ├── chatApi.ts                  # 聊天 API
│   ├── uploadApi.ts                # 上传 API
│   └── userApi.ts                  # 用户 API
│
└── views/                          # 页面视图（如需要）
    ├── ChatView.vue                # 聊天页面
    ├── ContactsView.vue            # 通讯录页面
    └── SettingsView.vue            # 设置页面
```

---

## 八、组件接口契约

### 8.1 必须实现的 Props 和 Events

每个核心组件必须遵循以下接口约定：

```typescript
// 所有组件必须导出 Props 和 Emits 类型
export interface ComponentProps {
  // ...
}

export interface ComponentEmits {
  // ...
}

// 使用 defineComponent 或 <script setup generic> 明确类型
```

### 8.2 组件通信模式

**父子通信：** Props down, Events up
```vue
<!-- 父组件 -->
<ConversationList 
  :conversations="store.filteredConversations"
  :selected-id="store.currentConversationId"
  @select="store.selectConversation"
/>
```

**跨级通信：** Provide / Inject
```typescript
// MainLayout.vue
provide('theme', themeRef);

// 子组件中
const theme = inject<Ref<ThemeMode>>('theme');
```

**全局状态：** Pinia Store
```typescript
const chatStore = useChatStore();
```

---

## 九、性能优化策略

### 9.1 虚拟滚动

对于大量消息列表，使用虚拟滚动：

```vue
<script setup lang="ts">
import { useVirtualList } from '@vueuse/core';

const { list, containerProps, wrapperProps } = useVirtualList(
  computed(() => store.currentMessages),
  {
    itemHeight: 80,  // 估算高度
    overscan: 5,
  }
);
</script>
```

### 9.2 图片懒加载

```vue
<img 
  v-lazy="message.url" 
  loading="lazy"
  decoding="async"
/>
```

### 9.3 消息分页加载

```typescript
const PAGE_SIZE = 50;

async function loadMoreMessages() {
  const oldestMessage = currentMessages.value[0];
  await chatStore.loadMessagesBefore(
    currentConversationId.value!,
    oldestMessage.id,
    PAGE_SIZE
  );
}
```

---

## 十、扩展性设计原则

### 10.1 插件化消息渲染

通过动态组件实现消息类型的可扩展：

```vue
<!-- MessageBubble.vue -->
<template>
  <component 
    :is="messageComponentMap[message.type]" 
    v-bind="messageProps"
  />
</template>

<script setup lang="ts">
import TextMessage from './message-types/TextMessage.vue';
import ImageMessage from './message-types/ImageMessage.vue';
// ...

const messageComponentMap = {
  text: TextMessage,
  image: ImageMessage,
  file: FileMessage,
  // 可以轻松注册新的消息类型
};

// 注册自定义消息类型
function registerMessageType(type: string, component: Component) {
  messageComponentMap[type] = component;
}
</script>
```

### 10.2 主题插件系统

```typescript
// themes/index.ts
export interface ThemeConfig {
  name: string;
  tokens: Record<string, string>;
  components?: Record<string, Component>;
}

const themes: Record<string, ThemeConfig> = {
  wechat: { /* 微信风格 */ },
  dark: { /* 暗黑模式 */ },
  custom: { /* 自定义主题 */ },
};
```

### 10.3 功能模块化

每个功能模块可以独立开发、测试和启用：

```
plugins/
├── voice-call/          # 语音通话
├── video-call/          # 视频通话
├── screen-share/        # 屏幕共享
├── whiteboard/          # 白板协作
├── ai-assistant/        # AI 助手集成
└── memory-graph/        # 记忆图谱可视化
```

---

## 十一、Tauri 集成点

### 11.1 后端命令注册

```rust
// src-tauri/src/lib.rs
#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! Welcome to Lingshi AI.", name)
}

#[tauri::command]
async fn stream_chat(
    state: State<'_, AppState>,
    user_id: String,
    message: String,
) -> Result<String, String> {
    // 调用后端流式聊天 API
}

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            greet,
            stream_chat,
            // ... 其他命令
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### 11.2 前端调用示例

```typescript
import { invoke } from '@tauri-apps/api/core';

async function sendMessage(message: string) {
  try {
    const response = await invoke<string>('stream_chat', {
      userId: 'User',
      message,
    });
    // 处理流式响应...
  } catch (error) {
    console.error('Failed to send message:', error);
  }
}
```

---

## 十二、实施优先级

### Phase 1：基础框架（第 1 周）
- [ ] 项目脚手架搭建（路由、状态管理）
- [ ] 三栏布局骨架实现
- [ ] 基础组件库（Avatar、Badge、EmptyState）
- [ ] 导航栏实现
- [ ] 会话列表骨架

### Phase 2：核心功能（第 2-3 周）
- [ ] 消息列表实现
- [ ] 消息气泡（文本、图片）
- [ ] 输入区域实现
- [ ] 发送/接收消息流程
- [ ] WebSocket 集成

### Phase 3：完善体验（第 4 周）
- [ ] 搜索功能
- [ ] 消息类型扩展（文件、语音等）
- [ ] 动画和微交互
- [ ] 暗黑模式
- [ ] 响应式适配

### Phase 4：高级特性（第 5-6 周）
- [ ] AI 对话集成（流式输出）
- [ ] 记忆图谱可视化
- [ ] 性能优化（虚拟滚动）
- [ ] 键盘快捷键
- [ ] Tauri 原生能力集成（通知、托盘等）

---

## 十三、参考资源

### 设计参考
- **WeChat Desktop**: 目标复刻对象
- **Element Plus**: 企业级 Vue 3 UI 库（参考组件 API 设计）
- **Arco Design Vue**: 当前使用的 UI 库

### 技术文档
- [Vue 3 Composition API](https://vuejs.org/guide/extras/composition-api-faq.html)
- [Pinia 官方文档](https://pinia.vuejs.org/)
- [Tauri 2 开发文档](https://v2.tauri.app/start/)
- [@arco-design/web-vue](https://arco.design/vue/component/overview)

---

*文档结束*
