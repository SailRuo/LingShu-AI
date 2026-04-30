# 灵枢之境 - 微信风格三栏式聊天布局设计文档

> 版本：v2.0（根据代码实现逆向更新）
> 日期：2026-04-27
> 技术栈：Vue 3 + TypeScript + @arco-design/web-vue + Tauri 2

---

## 一、整体架构概览

### 1.1 布局结构（实际实现）

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用窗口 (1366×768)                      │
├──────┬─────────────────────────────────────────────────────────┤
│      │  TitleBar (28px) - 窗口标题栏（最小化/最大化/关闭）        │
│      ├─────────────────────────────────────────────────────────┤
│ 导航栏 │ ┌──────────────────┬────────────────────────────────┐ │
│ (54px)│ │                  │                                │ │
│      │ │   会话列表        │           聊天窗口               │ │
│ ─── │ │  (280-400px)     │         (flex: 1)                │ │
│ 头像  │ │                  │                                │ │
│ (顶部)│ │ ─────────────── │ ─────────────────────────────────│ │
│      │ │ 搜索框           │ 聊天头部（44px）                   │ │
│ 对话  │ │ 会话项列表        │ 智能体选择 / AI徽章 / TTS开关    │ │
│ 设置  │ │                  │                                │ │
│      │ │                  │ ─────────────────────────────────│ │
│      │ │                  │                                  │ │
│      │ │                  │          消息列表区域              │ │
│      │ │                  │    （可滚动，自动滚动到底部）        │ │
│      │ │                  │                                  │ │
│      │ │                  │ ─────────────────────────────────│ │
│      │ │                  │                                  │ │
│      │ │                  │          输入区域                 │ │
│      │ │                  │  工具栏 + 文本输入框 + 发送按钮    │ │
│      │ └──────────────────┴────────────────────────────────┘ │
└──────┴─────────────────────────────────────────────────────────┘
```

### 1.2 组件层级树（实际实现）

```
App.vue (根组件)
└── MainLayout.vue (主布局容器)
    ├── SidebarNav.vue (左侧导航栏)
    │   ├── UserAvatar.vue (用户头像，顶部分布)
    │   ├── NavItem.vue (导航项) × 2
    │   └── BottomNavItem.vue (底部导航项) × 1
    ├── TitleBar.vue (窗口标题栏，Tauri 窗口控制)
    └── ChatView.vue (聊天视图，router-view)
        ├── ConversationList.vue (会话列表)
        │   ├── SearchBar.vue (搜索框，内联实现)
        │   ├── ConversationItem.vue (会话项) × N
        │   ├── EmptyState.vue (空状态)
        │   └── NewChatModal.vue (新建会话弹窗)
        └── ChatWindow.vue (聊天窗口)
            ├── ChatHeader.vue (聊天头部)
            │   ├── AgentSelector.vue (智能体选择器)
            │   └── ActionButtons.vue (操作按钮组：TTS、设置)
            ├── MessageList.vue (消息列表)
            │   ├── MessageBubble.vue (消息气泡) × N
            │   ├── TimeDivider.vue (时间分割线)
            │   └── ScrollToBottomButton.vue (回到底部按钮)
            └── InputArea.vue (输入区域)
                ├── ToolBar.vue (工具栏)
                │   ├── EmojiPicker.vue (表情选择器，预留)
                │   ├── FileUploader.vue (文件上传)
                │   └── VoiceRecorder.vue (语音录制，预留)
                ├── TextInput.vue (文本输入框)
                └── SendButton.vue (发送按钮)
```

---

## 二、详细布局规范

### 2.1 左侧导航栏 (SidebarNav)

**尺寸规格（实际实现）：**
```
宽度：54px（固定）
高度：100vh（全屏高度）
背景色：#EDEDED（浅灰）
padding：15px 0
```

**图标规格：**
```
图标尺寸：26×26 px（material-symbols-outlined）
点击区域：38×38 px
图标间距：垂直分布，gap 12px
激活态：#07C160 颜色 + FILL 1（填充样式）
悬停态：#191919 颜色
默认态：#666666 图标颜色
```

**导航项定义（实际实现）：**

| 序号 | 图标名称 | 功能说明 | 路由/状态 |
|------|---------|---------|----------|
| 1 | `mode_comment` | 对话（聊天） | 默认选中，路由 `/` |
| 2 | `settings` | 设置 | 路由 `/settings` |

**用户头像区（实际实现）：**
```
位置：固定在顶部
距顶部：0
头像尺寸：38×38 px
圆角：6px
点击效果：目前无菜单弹出
图片来源：/linger2.png
```

**TypeScript 接口定义（实际实现）：**
```typescript
// 导航项接口
interface NavItem {
  id: string;
  icon: string;           // material-symbols-outlined 图标名
  label: string;          // 显示文本（用于 tooltip）
  path: string;           // 路由路径
}

// 组件内使用
const mainNavItems = [
  { id: 'chat', icon: 'mode_comment', label: '对话', path: '/' },
];

const bottomNavItems = [
  { id: 'settings', icon: 'settings', label: '设置', path: '/settings' },
];

const activeNavId = computed(() => {
  const currentPath = route.path;
  const matchedItem = [...mainNavItems, ...bottomNavItems].find(item => item.path === currentPath);
  return matchedItem ? matchedItem.id : 'chat';
});
```

---

### 2.2 会话列表 (ConversationList)

**尺寸规格（实际实现）：**
```
默认宽度：280px
最小宽度：280px
最大宽度：400px（可拖拽调整）
背景色：#F7F7F7
边框：无（取消右侧边框线）
```

**搜索栏 (SearchBar，实际实现)：**
```
位置：顶部固定
padding：12px 10px
布局：水平排列（搜索框 + 添加按钮）

搜索框：
  高度：26px
  圆角：6px
  背景色：#E2E2E2（var(--bg-hover)）
  占位符："搜索"
  左侧图标：IconSearch（Arco Design）
  聚焦态：背景色变为 #FFFFFF，边框色 #07C160

添加按钮：
  尺寸：26×26px
  图标：IconPlus
  悬停态：背景色变为 #C6C6C6（var(--bg-selected)）
```

**会话项 (ConversationItem，实际实现)：**
```
高度：约56px
padding：6px 12px
布局：水平排列（左头像 + 中间内容 + 右时间）

左侧：
  头像尺寸：34×34 px（不是48×48）
  圆角：4px（不是6px）
  未读标记：数字角标（红色圆形，18px，白色文字）

中间内容区：
  昵称：
    字体大小：12px（var(--font-size-sm)）
    字重：400（不是500）
    颜色：#191919
    最大宽度：160px（单行截断）
  预览消息：
    字体大小：11px
    颜色：#999999（var(--text-tertiary)）
    最大宽度：180px（单行截断）

右侧时间区：
  格式："刚刚"/"X分钟前"/"X小时前"/"X天前"/"月/日"
  字体大小：10px
  颜色：#B2B2B2（var(--text-placeholder)）
  未读计数：
    背景：#FA5151（var(--color-unread-bg)）
    文字：白色
    尺寸：18px 圆形
    内边距：2px 6px
```

**会话项状态变体（实际实现）：**

| 状态 | 背景色 | 边框 | 特殊标识 |
|------|-------|------|---------|
| 选中 | #07C160（var(--color-item-active)） | 无 | 文字变白色 |
| 悬停 | #E2E2E2（var(--bg-hover)） | 无 | - |
| 未读 | 默认 | 无 | 预览文字不加粗 |
| 置顶 | 未实现 | - | - |
| 免打扰 | 未实现 | - | - |

**右键菜单（实际实现）：**
```
触发方式：右键点击（contextMenu）
菜单项：删除会话
图标：IconDelete
```

**TypeScript 接口定义（实际实现）：**
```typescript
// types/conversation.ts
export type OnlineStatus = 'online' | 'offline' | 'away';
export type ConversationType = 'chat' | 'group' | 'system';

export interface Conversation {
  id: string;
  avatar: string;             // 头像 URL 或 base64
  name: string;              // 显示名称
  lastMessage: string;        // 最后一条消息预览
  timestamp: Date;            // 最后消息时间
  unreadCount: number;        // 未读数（0 则不显示）
  isPinned: boolean;          // 是否置顶（预留）
  isMuted: boolean;           // 是否免打扰（预留）
  onlineStatus?: OnlineStatus; // 在线状态（预留）
  type: ConversationType;
  metadata?: Record<string, unknown>;  // 扩展元数据（包含 agentId）
}
```

---

### 2.3 聊天窗口 (ChatWindow)

**尺寸规格（实际实现）：**
```
宽度：flex: 1（占据剩余空间）
最小宽度：0（允许在 Flex 容器中正常收缩）
背景色：#FFFFFF（var(--bg-chat-window)）
```

#### 2.3.1 聊天头部 (ChatHeader，实际实现)

```
高度：44px（不是56px）
背景色：transparent（无背景色）
边框：底部 1px solid rgba(0, 0, 0, 0.05)
padding：0 16px
布局：水平居中（标题居中，两侧按钮）

左侧区域：
  AgentSelector（智能体选择器）
    头像：24px
    下拉菜单：显示所有可用智能体
  用户名/群名（如非默认会话）
  AI徽章：灰色小标签 "AI"

右侧操作按钮组：
  按钮1：TTS语音开关
    - 未开启：IconMute
    - 已开启：IconSound（带脉冲动画）
    - 播放中：IconSound + playing-anim 动画
  按钮2：设置
    图标：IconSettings
  图标尺寸：18px
  点击区域：36×36 px
  悬停态：#E2E2E2 背景
```

**TypeScript 接口定义（实际实现）：**
```typescript
// ChatHeaderProps
interface ChatHeaderProps {
  conversation: Conversation | null;
  // 无其他回调函数，通过 chatStore 调用
}
```

#### 2.3.2 消息列表 (MessageList，实际实现)

**尺寸规格：**
```
高度：flex: 1，min-height: 0
背景色：transparent（不是 #F5F5F5）
溢出：auto（可滚动）
padding：20px 0（上下20px）
gap：8px（消息间距，不是12px）
滚动条：鼠标悬停时显示，默认隐藏
```

**消息气泡 (MessageBubble，实际实现)：**

**发送方（右侧）：**
```
对齐方式：flex-end（右对齐）
气泡样式：
  最大宽度：85%（不是60%）
  背景色：#95EC69（var(--bg-message-self)）
  圆角：8px
  内边距：12px 16px
  文字：
    字体大小：14px（var(--font-size-md)）
    颜色：#000000（var(--text-message-self)）
    行高：1.6
  右边距：12px

气泡三角箭头：
  位置：右侧
  颜色：同气泡背景色

双击效果：触发 TTS 语音播放
```

**接收方（左侧）：**
```
对齐方式：flex-start（左对齐）
布局：水平排列（头像 + 气泡 + 三角箭头）

头像：
  尺寸：38×38 px
  圆角：6px
  与气泡间距：8px
  margin-top：2px

气泡样式：
  最大宽度：85%
  背景色：#EDEDED（var(--bg-message-other)，不是 #FFFFFF）
  圆角：8px
  内边距：12px 16px
  阴影：无
  文字：
    字体大小：14px
    颜色：#191919
    行高：1.6

发送者昵称（群聊时显示）：
  字体大小：11px
  颜色：#999999
  margin-bottom：2px

气泡三角箭头：
  位置：左侧
  颜色：同气泡背景色
```

**时间分割线 (TimeDivider，实际实现)：**
```
样式：居中显示
显示条件：与上一条消息时间间隔 > 5 分钟
文字格式：
  今日消息："HH:mm"
  非今日："M/D HH:mm"
字体大小：11px（var(--font-size-xs)）
颜色：#B2B2B2
margin：30px 0 10px（上下间距更大）
```

**消息类型支持（实际实现）：**
| 消息类型 | 状态 | 组件 |
|---------|------|------|
| 文本 | ✅ 已实现 | 直接渲染 |
| 图片 | ❌ 未实现 | 显示 "[image 消息]" |
| 文件 | ❌ 未实现 | 显示 "[file 消息]" |
| 语音 | ❌ 未实现 | 显示 "[voice 消息]" |
| 视频 | ❌ 未实现 | 显示 "[video 消息]" |
| 链接 | ❌ 未实现 | 显示 "[link 消息]" |
| 引用 | ❌ 未实现 | 显示 "[quote 消息]" |
| 系统 | ❌ 未实现 | 显示 "[system 消息]" |

**TypeScript 接口定义（实际实现）：**
```typescript
// types/message.ts
export type MessageType =
  | 'text'
  | 'image'
  | 'file'
  | 'voice'
  | 'video'
  | 'link'
  | 'quote'
  | 'system';

export type MessageStatus = 'sending' | 'sent' | 'delivered' | 'read' | 'failed';

export interface BaseMessage {
  id: string;
  type: MessageType;
  senderId: string;
  senderName: string;
  senderAvatar?: string;
  timestamp: Date;
  status: MessageStatus;
  isSelf: boolean;
  metadata?: any;
}

export interface TextMessage extends BaseMessage {
  type: 'text';
  content: string;
}

// ... 其他消息类型定义（未实现）
```

#### 2.3.3 输入区域 (InputArea，实际实现)

**尺寸规格：**
```
最小高度：120px
最大高度：300px（不是200px）
背景色：transparent
padding：0 8px 8px
调整方式：顶部拖拽手柄
```

**工具栏 (ToolBar)：**
```
高度：40px（含在 footer 内）
布局：水平排列，左对齐
按钮间距：8px
图标尺寸：18px
颜色：#999999
悬停：#191919

按钮列表：
  😊 表情（预留，IconFaceSmileFill）
  📎 文件（IconFolder）
  ✂️ 截图（IconScissor）+ 下拉箭头（IconDown）
  🎤 语音（IconVoice）
```

**文本输入框 (TextInput)：**
```
最小高度：40px
背景色：transparent
边框：无
placeholder：空（不是"输入消息..."）
字体大小：14px
行高：1.5
颜色：#191919
resize: none（禁止手动调整大小）
支持粘贴图片（handlePaste 事件）
```

**发送按钮 (SendButton)：**
```
位置：右下角
高度：32px
padding：0 20px
背景色：var(--bg-hover) = #E2E2E6
文字："发送"
字体大小：14px
颜色：#07C160（var(--color-primary)）
圆角：4px
禁用态：降低透明度，cursor: not-allowed
悬停态：背景色变为 var(--bg-selected) = #C6C6C6
```

**附件预览（实际实现）：**
```
支持：图片、文件
图片预览：80×80px，删除按钮覆盖
文件卡片：180×60px，显示文件名和大小
```

---

## 三、主题与色彩系统

### 3.1 设计令牌 (Design Tokens，实际实现)

```css
/* ====== 全局 CSS 变量 ====== */

/* 布局尺寸 */
--sidebar-width: 54px;                  /* 实际 54px，不是 60px */
--conversation-list-width: 280px;        /* 实际 280px，不是 320px */
--conversation-list-min-width: 280px;
--conversation-list-max-width: 400px;
--chat-header-height: 44px;              /* 实际 44px，不是 56px */
--input-area-min-height: 120px;
--input-area-max-height: 300px;          /* 实际 300px，不是 200px */

/* 主色调 */
--color-primary: #07C160;           /* 微信绿 */
--color-primary-hover: #06AD56;
--color-primary-active: #069A4D;
--color-primary-light: #E8F8EE;

/* 语义化选中项背景（实际实现） */
--color-item-active: #07C160;       /* 选中态背景 */
--color-item-active-hover: #06AD56;
--color-item-active-text: #FFFFFF;  /* 选中态文字颜色 */

/* 背景色 */
--bg-sidebar: #EDEDED;
--bg-conversation-list: #F7F7F7;
--bg-chat-window: #FFFFFF;          /* 实际白色，不是 #F5F5F5 */
--bg-input-area: #FFFFFF;
--bg-chat-header: #FFFFFF;
--bg-hover: #E2E2E6;
--bg-selected: #C6C6C6;
--bg-input: #FFFFFF;
--bg-message-self: #95EC69;         /* 微信绿 */
--bg-message-other: #EDEDED;        /* 实际灰色，不是白色 */
--text-message-self: #000000;

/* 文字色 */
--text-primary: #191919;
--text-secondary: #666666;
--text-tertiary: #999999;
--text-placeholder: #B2B2B2;
--text-link: #576B95;
--text-on-primary: #FFFFFF;

/* 边框色 */
--border-color: #E0E0E0;
--border-color-dark: #D9D9D9;
--border-input: #E0E0E0;
--border-input-focus: #07C160;
--header-border-color: rgba(0, 0, 0, 0.05);

/* 状态色 */
--color-success: #07C160;
--color-warning: #FFA940;
--color-error: #FA5151;
--color-info: #1890FF;
--color-unread-bg: #FA5151;
--color-online: #07C160;
--color-offline: #B2B2B2;

/* 阴影 */
--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 12px rgba(0, 0, 0, 0.08);
--shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.12);
--shadow-message: 0 1px 2px rgba(0, 0, 0, 0.05);

/* 圆角 */
--radius-xs: 2px;
--radius-sm: 4px;
--radius-md: 6px;
--radius-lg: 8px;
--radius-xl: 12px;
--radius-full: 50%;

/* 过渡动画 */
--transition-fast: 0.15s ease;
--transition-normal: 0.25s ease;
--transition-slow: 0.35s ease;

/* 字体 */
--font-scale: 1;
--font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
  "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB",
  "Microsoft YaHei UI", "Microsoft YaHei", sans-serif;
--font-size-xs: calc(11px * var(--font-scale));
--font-size-sm: calc(12px * var(--font-scale));
--font-size-base: calc(13px * var(--font-scale));
--font-size-md: calc(14px * var(--font-scale));
--font-size-lg: calc(15px * var(--font-scale));
--font-size-xl: calc(17px * var(--font-scale));
--font-size-xxl: calc(20px * var(--font-scale));
--line-height-tight: 18px;
--line-height-normal: 22px;
--line-height-relaxed: 26px;
```

### 3.2 暗黑模式支持（实际实现）

```css
[data-theme="dark"] {
  --bg-sidebar: #2E2E2E;
  --bg-conversation-list: #222222;
  --bg-chat-window: #171717;
  --bg-input-area: #222222;
  --bg-chat-header: #222222;
  --bg-message-self: #3D8C41;
  --bg-message-other: #2E2E2E;
  --text-message-self: #E5E5E5;
  --bg-hover: #333333;
  --bg-selected: #3A3A3A;
  --bg-input: #2A2A2A;

  --text-primary: #E5E5E5;
  --text-secondary: #A6A6A6;
  --text-tertiary: #737373;
  --text-placeholder: #555555;

  --border-color: #404040;
  --border-color-dark: #4A4A4A;
  --border-input: #444444;
  --header-border-color: rgba(255, 255, 255, 0.05);

  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.2);
  --shadow-md: 0 4px 12px rgba(0, 0, 0, 0.3);
  --shadow-message: 0 1px 2px rgba(0, 0, 0, 0.15);

  /* 语义化选中项背景 */
  --color-item-active: #3D8C41;
  --color-item-active-hover: #459A49;
  --color-item-active-text: #E5E5E5;
}
```

---

## 四、交互规范

### 4.1 动画时长（实际实现）

| 交互类型 | 时长 | 缓动函数 |
|---------|------|---------|
| 悬停反馈 | 150ms | ease |
| 页面切换 | 150ms | ease（fade 过渡） |
| 消息出现 | 无 | - |
| 模态框弹出 | Arco Design 默认 | - |
| TTS 脉冲动画 | 1500ms | infinite ease-in-out |

### 4.2 微交互细节

**TTS 播放脉冲动画：**
```css
@keyframes pulse {
  0% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.1); opacity: 0.8; }
  100% { transform: scale(1); opacity: 1; }
}

.playing-anim {
  animation: pulse 1.5s infinite ease-in-out;
}
```

**头像悬停动画：**
```css
.user-avatar:hover {
  transform: scale(1.05);
}

.user-avatar:active {
  transform: scale(0.95);
}
```

**导航项点击动画：**
```css
.nav-item:active {
  transform: scale(0.92);
}
```

### 4.3 快捷键绑定（实际实现）

| 快捷键 | 功能 | 作用域 |
|-------|------|-------|
| `Enter` | 发送消息 | 输入框聚焦时 |
| `Shift+Enter` | 换行 | 输入框聚焦时 |
| `双击气泡` | TTS 语音播放 | 接收到的 AI 消息 |

**未实现的快捷键：**
- `Ctrl+F` 聚焦搜索框
- `Ctrl+N` 新建会话
- `Escape` 关闭弹窗
- `↑/↓` 切换会话
- `PageUp/PageDown` 滚动消息历史

---

## 五、响应式与自适应

### 5.1 断点定义（未使用）

设计文档定义了断点，但实际代码中未使用响应式布局。

### 5.2 自适应策略（未完全实现）

**宽屏 (>1024px)：**
- ✅ 三栏完整展示
- ✅ 会话列表可拖拽调整宽度（280px - 400px）

**平板/手机：**
- ❌ 未实现响应式适配

### 5.3 会话列表宽度可调整（实际实现）

```vue
<!-- ConversationList.vue -->
<template>
  <section
    class="conversation-list"
    :style="{ width: uiStore.conversationListWidth + 'px' }"
  >
    <!-- 内容 -->
    <div class="resize-handle" @mousedown="startResize"></div>
  </section>
</template>

<script setup lang="ts">
const isResizing = ref(false);

function startResize(e: MouseEvent) {
  isResizing.value = true;
  document.addEventListener('mousemove', handleResize);
  document.addEventListener('mouseup', stopResize);
  e.preventDefault();
}

function handleResize(e: MouseEvent) {
  if (!isResizing.value) return;
  const newWidth = e.clientX - 54; // 减去 sidebar 宽度
  uiStore.setConversationListWidth(newWidth);
}

function stopResize() {
  isResizing.value = false;
  document.removeEventListener('mousemove', handleResize);
  document.removeEventListener('mouseup', stopResize);
}
</script>
```

---

## 六、状态管理架构

### 6.1 Pinia Store 定义（实际实现）

#### ChatStore (stores/chat.ts)

```typescript
// stores/chat.ts
export const useChatStore = defineStore('chat', () => {
  // 状态
  const conversations = ref<Conversation[]>([]);
  const currentConversationId = ref<string | null>(null);
  const messagesMap = ref<Record<string, AnyMessage[]>>({});
  const isLoadingMessages = ref(false);
  const searchQuery = ref('');
  const activeNav = ref('message');
  const userId = ref(getClientUserId());
  const currentAgentId = ref<number | null>(null);

  // 计算属性
  const currentConversation = computed(() =>
    conversations.value.find(c => c.id === currentConversationId.value) || null
  );

  const currentMessages = computed<AnyMessage[]>(
    () => currentConversationId.value
      ? messagesMap.value[currentConversationId.value] || []
      : []
  );

  const filteredConversations = computed<Conversation[]>(() => {
    if (!searchQuery.value) return conversations.value;
    const q = searchQuery.value.toLowerCase();
    return conversations.value.filter(
      c =>
        c.name.toLowerCase().includes(q) ||
        c.lastMessage.toLowerCase().includes(q)
    );
  });

  const totalUnreadCount = computed<number>(
    () => conversations.value.reduce((sum, c) => sum + c.unreadCount, 0)
  );

  // Actions
  async function loadConversations() { /* 从 API 加载会话列表 */ }
  async function selectConversation(id: string) { /* 选择会话 */ }
  async function createNewConversation(agentId: number) { /* 创建新会话 */ }
  async function deleteConversation(sessionId: string) { /* 删除会话 */ }
  async function loadMessages(conversationId: string) { /* 加载历史消息 */ }
  async function sendMessage(content: string, attachments: any[]) { /* 流式发送消息 */ }
  function retrySendMessage(_messageId: string) { /* TODO: 重发失败消息 */ }
  function setSearchQuery(query: string) { /* 设置搜索关键词 */ }
  function setActiveNav(nav: string) { /* 设置导航状态 */ }
  function setAgentId(agentId: number | null) { /* 设置当前智能体 */ }

  return {
    conversations,
    currentConversationId,
    messagesMap,
    isLoadingMessages,
    searchQuery,
    activeNav,
    userId,
    currentAgentId,
    currentConversation,
    currentMessages,
    filteredConversations,
    totalUnreadCount,
    loadConversations,
    selectConversation,
    createNewConversation,
    deleteConversation,
    loadMessages,
    sendMessage,
    retrySendMessage,
    setSearchQuery,
    setActiveNav,
    setAgentId,
  };
});
```

#### UIStore (stores/ui.ts)

```typescript
// stores/ui.ts
export type ThemeMode = 'light' | 'dark' | 'system';

export const useUIStore = defineStore('ui', () => {
  const theme = ref<ThemeMode>('light');
  const fontSize = ref(2);
  const conversationListWidth = ref(280);

  function toggleTheme() { /* 切换主题 */ }
  function setTheme(mode: ThemeMode) { /* 设置主题 */ }
  function setFontSize(size: number) { /* 设置字体大小 */ }
  function setConversationListWidth(width: number) { /* 设置会话列表宽度 */ }

  return {
    theme,
    fontSize,
    conversationListWidth,
    toggleTheme,
    setTheme,
    setFontSize,
    setConversationListWidth,
  };
});
```

#### AgentsStore (stores/agents.ts)

```typescript
// stores/agents.ts
export interface AgentConfig {
  id?: number;
  name: string;
  displayName: string;
  systemPrompt: string;
  factExtractionPrompt?: string;
  behaviorPrinciples?: string;
  decisionMechanism?: string;
  toolCallRules?: string;
  emotionalStrategy?: string;
  greetingTriggers?: string;
  hiddenRules?: string;
  avatar?: string;
  color?: string;
  isDefault: boolean;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export const useAgentsStore = defineStore('agents', () => {
  const agents = ref<AgentConfig[]>([]);
  const isLoading = ref(false);
  const isLoaded = ref(false);

  async function fetchAgents(force = false) { /* 获取智能体列表 */ }
  async function createAgent(agentData: Partial<AgentConfig>) { /* 创建智能体 */ }
  async function updateAgent(id: number, agentData: Partial<AgentConfig>) { /* 更新智能体 */ }
  async function deleteAgent(id: number) { /* 删除智能体 */ }
  async function setDefaultAgent(id: number) { /* 设置默认智能体 */ }
  async function getAgentDefaults() { /* 获取默认配置 */ }

  return {
    agents,
    isLoading,
    isLoaded,
    fetchAgents,
    createAgent,
    updateAgent,
    deleteAgent,
    setDefaultAgent,
    getAgentDefaults,
  };
});
```

### 6.2 WebSocket 连接管理（实际实现）

```typescript
// composables/useWebSocket.ts
export function useWebSocket() {
  const socket = ref<WebSocket | null>(null);
  const isConnected = ref(false);
  const handlers = new Map<string, Set<MessageHandler>>();
  const reconnectCount = ref(0);
  const maxReconnects = 5;

  function connect(url: string = 'ws://localhost:8080/ws/chat') { /* 连接 */ }
  function handleReconnect(url: string) { /* 重连（指数退避） */ }
  function disconnect() { /* 断开连接 */ }
  function send(message: WebSocketMessage) { /* 发送消息 */ }
  function on(type: string, handler: MessageHandler) { /* 注册处理器 */ }
  function off(type: string, handler: MessageHandler) { /* 注销处理器 */ }
  function emit(type: string, message: WebSocketMessage) { /* 触发事件 */ }
  function register(userId: string, sessionId?: number | null) { /* 注册用户 */ }
  function sendChat(params: {...}) { /* 发送聊天消息 */ }

  return {
    isConnected,
    connect,
    disconnect,
    send,
    on,
    off,
    register,
    sendChat
  };
}
```

### 6.3 TTS 语音合成（实际实现，文档未提及）

```typescript
// composables/useTts.ts
export function useTts() {
  const isPlaying = ref(false);
  const currentPlayingId = ref<string | null>(null);
  const autoTtsEnabled = ref(localStorage.getItem('lingshu_auto_tts') === 'true');

  function speak(text: string, messageId?: string): Promise<void> { /* 播放 TTS */ }
  function appendText(text: string, messageId: string, isFinished: boolean) { /* 流式追加 */ }
  function stop() { /* 停止播放 */ }
  function toggleAutoTts() { /* 切换自动 TTS */ }

  return {
    isPlaying: readonly(isPlaying),
    currentPlayingId: readonly(currentPlayingId),
    autoTtsEnabled: readonly(autoTtsEnabled),
    speak,
    appendText,
    stop,
    toggleAutoTts
  };
}
```

**TTS 特性：**
- 自动将文本按标点符号分段
- 支持 Markdown 格式 stripping
- 流式输出时增量追加
- 本地 localStorage 持久化自动播放状态

---

## 七、目录结构规划

### 实际项目结构 (lingshu-gui/src)

```
src/
├── App.vue                          # 根组件
├── main.ts                         # 入口文件
├── vite-env.d.ts                   # Vite 类型声明
│
├── assets/                         # 静态资源
│   ├── images/                     # 图片资源
│   │   ├── linger.png              # 默认 AI 头像
│   │   └── linger2.png             # 用户头像
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
│   │   └── TitleBar.vue             # 窗口标题栏（Tauri）
│   │
│   ├── chat/                       # 聊天相关组件
│   │   ├── ChatView.vue            # 聊天视图（路由页面）
│   │   ├── ChatWindow.vue          # 聊天窗口容器
│   │   ├── ChatHeader.vue          # 聊天头部
│   │   ├── MessageList.vue         # 消息列表
│   │   ├── InputArea.vue           # 输入区域
│   │   └── ConversationList.vue    # 会话列表
│   │
│   ├── settings/                   # 设置相关组件
│   │   └── AgentManager.vue        # 智能体管理
│   │
│   └── common/                     # 通用组件（预留）
│       └── (暂无)
│
├── composables/                    # 组合式函数
│   ├── useWebSocket.ts             # WebSocket 管理
│   └── useTts.ts                   # TTS 语音合成
│
├── stores/                         # Pinia 状态管理
│   ├── index.ts                    # Store 导出
│   ├── chat.ts                     # 聊天状态
│   ├── ui.ts                       # UI 状态
│   └── agents.ts                   # 智能体状态
│
├── types/                          # TypeScript 类型定义
│   ├── index.ts                    # 类型导出
│   ├── chat.ts                     # 聊天相关类型
│   ├── message.ts                  # 消息类型
│   └── conversation.ts             # 会话类型
│
├── api/                            # API 接口（预留）
│   └── (暂无具体文件)
│
└── router/                         # 路由配置（预留）
    └── (暂无具体文件)
```

---

## 八、组件接口契约

### 8.1 组件通信模式（实际实现）

**父子通信：** Props down, Events up
```vue
<!-- ConversationList.vue -->
<template>
  <div
    class="conversation-item"
    :class="{ selected: conv.id === chatStore.currentConversationId }"
    @click="chatStore.selectConversation(conv.id)"
  >
</template>
```

**跨级通信：** Provide / Inject（预留）

**全局状态：** Pinia Store
```typescript
const chatStore = useChatStore();
const uiStore = useUIStore();
const agentsStore = useAgentsStore();
```

---

## 九、性能优化策略

### 9.1 消息分页加载（实际实现）

```typescript
// chat.ts store
async function loadMessages(conversationId: string) {
  // 通过 HTTP API 请求该会话的历史记录
  const params = new URLSearchParams({
    size: '50',  // 每次加载 50 条
    userId: userId.value,
    sessionId: conversationId,
  });
  const res = await fetch(getFullUrl(`/api/chat/turns?${params}`));
  // ...
}
```

### 9.2 虚拟滚动（未实现）

设计文档描述了虚拟滚动，但实际代码未实现。

### 9.3 图片懒加载（未实现）

---

## 十、Tauri 集成点

### 10.1 窗口控制（实际实现）

```typescript
// TitleBar.vue
import { getCurrentWindow } from '@tauri-apps/api/window';

const appWindow = getCurrentWindow();

// 窗口拖拽移动
const handleMouseDown = (e: MouseEvent) => {
  if (e.buttons === 1) {
    appWindow.startDragging();
  }
};

// 双击最大化/还原
const handleDoubleClick = () => {
  appWindow.toggleMaximize();
};

// 窗口控制按钮
const minimize = () => appWindow.minimize();
const toggleMaximize = async () => {
  await appWindow.toggleMaximize();
  checkMaximized();
};
const close = () => appWindow.close();
```

### 10.2 前端调用后端 API

```typescript
// chat.ts store
function getFullUrl(path: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  return `${baseUrl}${path}`;
}

// 流式聊天 API
const res = await fetch(getFullUrl('/api/chat/stream'), {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(payload),
});
// 处理流式响应...
```

---

## 十一、实际实现的功能特性

### 11.1 已实现但文档未描述的功能

| 功能 | 描述 | 位置 |
|------|------|------|
| **智能体选择** | 支持切换不同 AI 角色对话 | ChatHeader + NewChatModal |
| **TTS 语音播报** | 双击气泡播放语音，自动播报 AI 回复 | useTts + ChatWindow |
| **自动语音合成** | AI 回复自动触发 TTS 播放（可开关） | ChatWindow watch |
| **右键删除会话** | contextMenu 实现 | ConversationList |
| **新建会话弹窗** | 选择智能体创建会话 | NewChatModal |
| **流式消息输出** | AI 回复逐字显示 | sendMessage 流式处理 |
| **消息状态追踪** | sending/sent/failed 状态 | message.status |
| **时间分组显示** | 5分钟间隔显示时间分割线 | shouldShowTime |
| **附件上传** | 图片和文件上传支持 | InputArea |
| **粘贴上传** | Ctrl+V 直接粘贴图片 | handlePaste |
| **输入框高度自适应** | 拖拽调整，最小120px，最大300px | InputArea |

### 11.2 文档描述但未实现的功能

| 功能 | 文档描述位置 |
|------|------------|
| 通讯录导航 | 导航项定义 |
| 收藏导航 | 导航项定义 |
| 文件导航 | 导航项定义 |
| 语音/视频通话按钮 | ChatHeader |
| 快捷键绑定 | 交互规范 |
| 虚拟滚动 | 性能优化 |
| 消息类型组件 | message-types 目录 |
| 深色模式切换动画 | 微交互细节 |
| 在线状态指示器 | ChatHeader |
| 置顶/免打扰会话 | ConversationList |

---

## 十二、实施优先级

### Phase 1：基础框架 ✅ 已完成
- [x] 项目脚手架搭建
- [x] 三栏布局骨架实现
- [x] 导航栏实现
- [x] 会话列表实现

### Phase 2：核心功能 ✅ 已完成
- [x] 消息列表实现
- [x] 消息气泡（仅文本）
- [x] 输入区域实现
- [x] 发送/接收消息流程
- [x] WebSocket 集成
- [x] 流式输出

### Phase 3：完善体验 ⚠️ 部分完成
- [x] 搜索功能
- [ ] 消息类型扩展（文件、语音等）
- [ ] 动画和微交互
- [x] 暗黑模式
- [ ] 响应式适配

### Phase 4：高级特性 ⚠️ 部分完成
- [x] AI 对话集成（流式输出）
- [ ] 记忆图谱可视化
- [ ] 性能优化（虚拟滚动）
- [ ] 键盘快捷键
- [ ] Tauri 原生能力集成（通知、托盘等）

---

## 十三、参考资源

### 设计参考
- **WeChat Desktop**: 目标复刻对象
- **Arco Design Vue**: 当前使用的 UI 库
- **Material Symbols**: 图标库

### 技术文档
- [Vue 3 Composition API](https://vuejs.org/guide/extras/composition-api-faq.html)
- [Pinia 官方文档](https://pinia.vuejs.org/)
- [Tauri 2 开发文档](https://v2.tauri.app/start/)
- [@arco-design/web-vue](https://arco.design/vue/component/overview)

---

*文档更新于 2026-04-27（根据代码实现逆向更新）*
