# 灵枢 AI - UI/UX 设计详细文档

> 基于代码逆向工程生成的设计文档  
> 最后更新：2026-04-14

---

## 📋 目录

1. [项目概述](#项目概述)
2. [技术栈](#技术栈)
3. [设计系统](#设计系统)
4. [布局架构](#布局架构)
5. [主题系统](#主题系统)
6. [核心页面](#核心页面)
7. [组件库](#组件库)
8. [交互设计](#交互设计)
9. [动画效果](#动画效果)
10. [响应式设计](#响应式设计)

---

## 项目概述

### 产品名称
**灵枢 AI (LingShu AI)** - 本地化智能 AI 伴侣系统

### 设计理念
- **沉浸式体验**：采用星空背景和动态特效，营造宇宙般的沉浸感
- **毛玻璃美学**：多层次毛玻璃效果，打造现代科技感界面
- **极简主义**：简洁的视觉层次，突出内容本身
- **情感化设计**：通过颜色、动效传递温暖与科技感并存的情感

### 目标用户
- 需要本地化 AI 助手的个人用户
- 注重隐私和数据安全的知识工作者
- 追求高品质 UI/UX 体验的科技爱好者

---

## 技术栈

### 前端框架
- **Vue 3.5+** - Composition API + `<script setup>`
- **TypeScript 5.9+** - 类型安全
- **Vite 6.0+** - 快速构建工具

### UI 组件库
- **Naive UI 2.44+** - 现代化 Vue 3 组件库
- **Tailwind CSS 4.2+** - 原子化 CSS 框架
- **Lucide Icons** - 优雅的图标系统

### 状态管理
- **Pinia 3.0+** - Vue 官方状态管理
- **@vueuse/core** - Vue 组合式工具集

### 3D 可视化
- **Three.js 0.183+** - 3D 图形渲染
- **OrbitControls** - 相机控制
- **CSS2DRenderer** - HTML 标签渲染

### 其他依赖
- **Markdown-it** - Markdown 渲染
- **Highlight.js** - 代码高亮
- **Axios** - HTTP 客户端

---

## 设计系统

### 颜色系统

#### 全局 CSS 变量

```css
/* 基础颜色 */
--color-background      /* 背景色 */
--color-surface         /* 表面色（半透明） */
--color-surface-elevated /* 提升表面色 */
--color-primary         /* 主色 */
--color-primary-dim     /* 主色淡化 */
--color-accent          /* 强调色 */

/* 文字系统 */
--color-text            /* 主要文字 */
--color-text-dim        /* 次要文字 */
--color-text-inverse    /* 反色文字 */

/* 边框和轮廓 */
--color-outline         /* 边框色 */

/* 状态色 */
--color-success         /* 成功 */
--color-warning         /* 警告 */
--color-error           /* 错误 */

/* 毛玻璃效果 */
--color-glass-bg        /* 毛玻璃背景 */
--color-glass-border    /* 毛玻璃边框 */

/* Mesh 渐变背景 */
--color-bg-mesh-1       /* Mesh 渐变色 1 */
--color-bg-mesh-2       /* Mesh 渐变色 2 */
--color-bg-mesh-3       /* Mesh 渐变色 3 */
--color-bg-mesh-4       /* Mesh 渐变色 4 */

/* 气泡组件 */
--color-bubble-ai-bg    /* AI 气泡背景 */
--color-bubble-ai-border /* AI 气泡边框 */
--color-bubble-user-bg  /* 用户气泡背景 */
--color-bubble-user-border /* 用户气泡边框 */

/* 脉冲效果 */
--color-pulse-core      /* 脉冲核心 */
--color-pulse-ring      /* 脉冲环 */

/* 知识图谱 */
--color-node-user       /* 用户节点 */
--color-node-fact       /* 事实节点 */
--color-edge            /* 边线 */

/* 发光效果 */
--color-glow            /* 光晕 */
```

### 字体系统

```css
font-family: 'Inter', system-ui, sans-serif;  /* 主字体 */
font-family: 'Fira Code', monospace;          /* 等宽字体 */
```

### 圆角规范

| 级别 | 值 | 应用场景 |
|------|-----|---------|
| Small | 8px | 小按钮、标签 |
| Medium | 10px | 中等按钮、菜单项 |
| Large | 12px | 输入框、卡片 |
| XLarge | 16px | 模态框、大卡片 |

### 间距系统

采用 Tailwind CSS 默认间距比例（4px 基准）：
- `p-2` = 8px
- `p-4` = 16px
- `p-6` = 24px
- `p-8` = 32px

### 阴影系统

```css
/* 基础阴影 */
box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

/* 悬浮阴影 */
box-shadow: 0 4px 12px var(--color-primary-dim);

/* 深度阴影 */
box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
```

---

## 布局架构

### 整体布局结构

```
┌─────────────────────────────────────────────┐
│  App Container (100vh × 100vw)              │
│  ┌──────────┬──────────────────────────────┐│
│  │          │                              ││
│  │ Sidebar  │   Right Area                 ││
│  │ (260px)  │   ┌──────────────────────┐   ││
│  │          │   │                      │   ││
│  │          │   │   Main Content       │   ││
│  │          │   │   (Flexible)         │   ││
│  │          │   │                      │   ││
│  │          │   └──────────────────────┘   ││
│  └──────────┴──────────────────────────────┘│
└─────────────────────────────────────────────┘
```

### Grid 布局定义

```css
.main-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  grid-template-rows: 1fr auto;
  grid-template-areas:
    "sidebar right"
    "footer footer";
}
```

### 侧边栏折叠状态

```css
.sidebar-collapsed {
  grid-template-columns: 0 1fr;
}
```

### 移动端布局

```css
@media (max-width: 767px) {
  .main-layout {
    grid-template-columns: 0 1fr;
  }
}
```

---

## 主题系统

### 主题架构

系统支持 **4 种预设主题**，每种主题包含完整的色彩系统和视觉效果配置。

### 主题列表

#### 1. 极地白 (Polar Light) ☀️
- **类型**: 浅色主题
- **主色**: `#0d9488` (青绿色)
- **背景**: `#f1f5f9` (浅灰白)
- **特点**: 清新明亮，适合日间使用
- **光晕强度**: 弱 (0.35)
- **Mesh 动画速度**: 60s

#### 2. 星空 (Cyber Purple) ✨
- **类型**: 深色主题（默认）
- **主色**: `#e2e8f0` (银白色)
- **背景**: `#000000` (纯黑宇宙)
- **特点**: 宇宙深邃感，如恒星般明亮
- **光晕强度**: 强 (0.55)
- **Mesh 动画速度**: 35s

#### 3. 霓虹绿境 (Deep Tech Futuristic) 🖥️
- **类型**: 深色主题
- **主色**: `#34d399` (亮绿色)
- **背景**: `#031f1a` (深绿)
- **特点**: 科技感强烈，赛博朋克风格
- **光晕强度**: 中 (0.55)
- **Mesh 动画速度**: 45s

#### 4. 午夜蓝 (Midnight Blue) 🌙
- **类型**: 深色主题
- **主色**: `#38bdf8` (天蓝色)
- **背景**: `#0a0f1c` (深蓝黑)
- **特点**: 静谧深邃，柔和舒适
- **光晕强度**: 柔 (0.45)
- **Mesh 动画速度**: 55s

### 主题切换机制

```typescript
// 持久化存储
const STORAGE_KEY = 'lingshu-theme'

// 主题应用流程
1. 读取 localStorage 中的保存主题
2. 应用 CSS 变量到 document.documentElement
3. 设置 dark/light class
4. 监听主题变化，自动保存到 localStorage
```

### Naive UI 主题覆盖

每个主题都会生成对应的 `GlobalThemeOverrides`，覆盖 Naive UI 的默认样式：

```typescript
{
  common: {
    primaryColor,
    borderRadius: '12px',
    fontFamily: "'Inter', 'Plus Jakarta Sans', system-ui, sans-serif",
  },
  Layout: { color: 'transparent' },
  Menu: { itemColorActive: 'transparent' },
  Card: { borderRadius: '16px' },
  // ... 更多组件覆盖
}
```

---

## 核心页面

### 1. 灵墟之境 (Resonance View) 💬

**路由键**: `resonance`  
**功能**: 主聊天界面，AI 对话核心区域

#### 布局结构

```
┌────────────────────────────────────────┐
│  ChatView (Full Height)                │
│  ┌──────────────────────────────────┐  │
│  │  Message List (Scrollable)       │  │
│  │  ├─ Welcome Message              │  │
│  │  ├─ User Messages (Right)        │  │
│  │  └─ AI Messages (Left)           │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │  ChatInput (Fixed Bottom)        │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
```

#### 关键特性

- **消息流**: 支持文本、图片、推理过程、工具调用
- **流式输出**: WebSocket 实时接收 AI 回复
- **语音交互**: ASR (语音识别) + TTS (语音合成)
- **历史加载**: 滚动到顶部自动加载更多历史
- **智能滚动**: 检测用户位置，避免干扰阅读

#### 侧边面板 (StreamPanel)

可展开的右侧面板，显示：
- 记忆检索过程
- 工具调用详情
- 推理思维链

```css
.side-panel {
  width: 400px;
  backdrop-filter: blur(20px);
  border-left: 1px solid var(--color-outline);
}
```

---

### 2. 记忆图谱 (Insight View) 🕸️

**路由键**: `insight`  
**功能**: 3D 银河系风格的记忆可视化

#### 核心技术

- **Three.js 渲染**: WebGL 3D 场景
- **力导向布局**: 节点自动排布算法
- **轨道控制**: OrbitControls 实现缩放、旋转
- **CSS2D 标签**: HTML 标签叠加在 3D 场景上

#### 节点类型

| 类型 | 颜色 | 大小 | 说明 |
|------|------|------|------|
| User | 黄色 | 大 | 用户核心节点 |
| Topic | 紫色 | 中 | 话题聚类 |
| Fact | 绿色 | 小 | 事实记忆 |

#### 交互功能

1. **搜索过滤**: 关键词实时搜索
2. **时间轴回放**: 按时间顺序展示记忆形成过程
3. **活跃度筛选**: active / stable / cool
4. **类型筛选**: User / Topic / Fact
5. **节点操作**: 右键菜单查看详情或删除
6. **缩放控制**: 鼠标滚轮缩放，拖拽旋转

#### 视觉特效

- **节点发光**: 根据活跃度调整光晕强度
- **连线动画**: 关系边的流动效果
- **出生闪光**: 新节点创建时的闪光动画
- **淡入淡出**: 节点出现/消失的过渡

---

### 3. 记忆治理 (Governance View) 🛠️

**路由键**: `governance`  
**功能**: 记忆管理系统，查看、编辑、删除记忆

#### 主要功能

- 记忆列表展示
- 批量操作
- 记忆编辑
- 冲突解决

---

### 4. 系统设置 (Settings View) ⚙️

**路由键**: `settings-model`, `settings-agents`, `settings-proactive`, `settings-mcp`

#### 子模块

1. **模型设置** (`settings-model`)
   - API 配置
   - 模型选择
   - 参数调整

2. **智能体设置** (`settings-agents`)
   - 智能体管理
   - 角色配置

3. **主动交互** (`settings-proactive`)
   - 主动提醒配置
   - 触发条件

4. **MCP 设置** (`settings-mcp`)
   - MCP 服务器配置
   - 工具管理

---

### 5. 系统状态 (Security View) 🔒

**路由键**: `security`  
**功能**: 系统健康监控、性能指标

---

### 6. 系统日志 (System Log View) 📋

**路由键**: `logs`  
**功能**: 运行日志查看、过滤、导出

---

## 组件库

### 布局组件

#### AppSider (侧边栏)

**文件**: `src/components/layout/AppSider.vue`

**结构**:
```vue
<aside class="app-sider">
  <div class="logo-section">
    <img src="/bot.png" />
    <span>灵枢</span>
    <span>LINGSHU.AI</span>
  </div>
  
  <div class="core-nav">
    <!-- 核心能力菜单 -->
  </div>
  
  <div class="infra-nav">
    <!-- 基础设施菜单 -->
  </div>
</aside>
```

**特性**:
- 桌面端可折叠 (260px → 0px)
- 移动端抽屉式 (从左侧滑入)
- 毛玻璃背景效果
- 平滑过渡动画

**导航菜单**:

核心能力:
- 灵墟之境 (Hexagon 图标)
- 记忆图谱 (Zap 图标)
- 记忆治理 (DatabaseBackup 图标)
- 全维口袋 (Layers 图标)

基础设施:
- 系统设置 (Settings 图标)
- 系统状态 (Activity 图标)
- 系统日志 (FileText 图标)

---

### 聊天组件

#### ChatView (聊天视图)

**文件**: `src/components/chat/ChatView.vue`

**职责**:
- 管理聊天消息列表
- WebSocket 连接管理
- 消息发送/接收
- 历史记录加载

**核心逻辑**:

```typescript
// WebSocket 消息处理
case 'chatStart':
  isTyping.value = true
  startAssistantMessage()
  
case 'chatChunk':
  appendAssistantChunk(msg.content)
  
case 'reasoningChunk':
  appendReasoningChunk(msg.content)
  
case 'toolStep':
  upsertToolStep(step)
```

**智能滚动策略**:

```typescript
// 检测用户是否在底部
function isAtBottom(el: HTMLElement): boolean {
  const threshold = 15 // 15px 误差范围
  return scrollHeight - scrollTop - clientHeight < threshold
}

// 仅在用户在底部时自动滚动
watch(() => messages.value.length, () => {
  if (autoScrollEnabled.value) {
    scrollToBottom()
  }
})
```

---

#### ChatMessage (消息组件)

**文件**: `src/components/chat/ChatMessage.vue`

**消息类型**:
1. **User Message**: 用户发送的消息
2. **Assistant Message**: AI 回复，包含多个 segment
   - Text Segment: 文本内容
   - Reasoning Segment: 推理过程（可折叠）
   - Tool Segment: 工具调用（可折叠）

**视觉设计**:

```css
/* AI 消息气泡 */
.ai-bubble {
  background: var(--color-bubble-ai-bg);
  border: 1px solid var(--color-bubble-ai-border);
  backdrop-filter: blur(12px);
}

/* 用户消息气泡 */
.user-bubble {
  background: var(--color-bubble-user-bg);
  border: 1px solid var(--color-bubble-user-border);
  backdrop-filter: blur(12px);
}
```

**Markdown 渲染**:
- 使用 `markdown-it` 解析
- 自定义链接渲染器（添加 `target="_blank"` 和 `rel="noopener noreferrer nofollow"`）
- 代码高亮（Highlight.js）

**TTS 集成**:
- 点击喇叭图标朗读消息
- 显示播放状态
- 支持中断播放

---

#### ChatInput (输入框)

**文件**: `src/components/chat/ChatInput.vue`

**特性**:
- 自适应高度 (最大 300px)
- 图片粘贴支持
- 图片压缩 (最大宽度 1024px, JPEG 质量 0.8)
- 语音输入 (ASR)
  - 点击模式: 开始/停止录音
  - 按住说话: mousedown/mouseup

**快捷键**:
- `Enter`: 发送消息
- `Shift + Enter`: 换行

**图片预览**:
```vue
<div class="image-preview-container">
  <div v-for="(img, index) in images" class="image-preview-item">
    <img :src="img" />
    <button @click="removeImage(index)">
      <X :size="14" />
    </button>
  </div>
</div>
```

---

#### StreamPanel (流式面板)

**文件**: `src/components/chat/StreamPanel.vue`

**功能**: 显示 AI 思考过程的详细信息
- 记忆检索日志
- 工具调用步骤
- 推理思维链

**交互**: 可从 ChatView 中展开/收起

---

### 特效组件

#### StarField (星空背景)

**文件**: `src/components/common/StarField.vue`

**实现**: Canvas 绘制动态星空
- 随机生成星星
- 闪烁动画
- 视差滚动效果

---

#### RainEffect (雨滴效果)

**文件**: `src/components/common/RainEffect.vue`

**实现**: Canvas 绘制雨滴下落
- 随机雨滴轨迹
- 速度变化
- 透明度渐变

---

#### AuroraEffect (极光效果)

**文件**: `src/components/common/AuroraEffect.vue`

**实现**: CSS 渐变 + 动画
- 流动的色彩带
- 模糊混合
- 缓慢移动

---

#### FireflyEffect (萤火虫效果)

**文件**: `src/components/common/FireflyEffect.vue`

**实现**: Canvas 绘制发光粒子
- 随机运动轨迹
- 呼吸光效
- 淡入淡出

---

#### MistEffect (雾气效果)

**文件**: `src/components/common/MistEffect.vue`

**实现**: CSS 渐变层叠
- 多层雾气
- 缓慢飘动
- 透明度变化

---

### 通用组件

#### ThemeModal (主题选择器)

**文件**: `src/components/common/ThemeModal.vue`

**功能**: 
- 展示所有可用主题
- 实时预览
- 一键切换

---

#### LatencyBar (延迟指示器)

**文件**: `src/components/layout/LatencyBar.vue`

**功能**: 显示网络延迟状态

---

## 交互设计

### 视图切换动画

```css
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
```

**特点**:
- 淡入淡出 + 垂直滑动
- 缓动函数: `cubic-bezier(0.4, 0, 0.2, 1)` (Material Design 标准)
- 持续时间: 400ms

---

### 侧边栏动画

**折叠/展开**:
```css
transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
```

**移动端抽屉**:
```css
transform: translateX(-100%) → translateX(0)
box-shadow: 4px 0 24px rgba(0, 0, 0, 0.3)
```

**遮罩层**:
```css
background: rgba(0, 0, 0, 0.5)
animation: fadeIn 0.3s ease forwards
```

---

### 消息入场动画

用户消息和 AI 消息采用不同的入场方式，增强对话的自然感。

---

### 按钮交互

**悬停效果**:
```css
.button:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px var(--color-primary-dim);
  backdrop-filter: blur(20px);
}
```

**点击反馈**:
- 轻微缩放
- 颜色变化
- 阴影加深

---

### 滚动条美化

```css
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-thumb {
  background: var(--color-outline);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-dim);
}
```

---

## 动画效果

### Mesh Background (网格背景)

**实现**: 3 个大型模糊圆形元素，缓慢浮动

```css
.mesh-blob {
  width: 80vmax;
  height: 80vmax;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.25;
  animation: float 50s infinite ease-in-out;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(3%, 8%) scale(1.05); }
  66% { transform: translate(-2%, 5%) scale(0.98); }
}
```

**主题专属速度**:
- CyberPurple: 35s (最快)
- DeepTechFuturistic: 45s
- MidnightBlue: 55s
- PolarLight: 60s (最慢)

---

### 毛玻璃效果层级

系统定义了 **4 级毛玻璃效果**，用于不同场景：

| 级别 | Blur 值 | 应用场景 |
|------|---------|---------|
| Light | 12px | 卡片、按钮 |
| Medium | 20px | 面板、侧边栏 |
| Heavy | 32px | 模态框、悬浮层 |
| Panel | 20px + Border | 通用面板 |

**CSS 类**:
```css
.glass-light { backdrop-filter: blur(12px); }
.glass-medium { backdrop-filter: blur(20px); }
.glass-heavy { backdrop-filter: blur(32px); }
.glass-panel { backdrop-filter: blur(20px); border: 1px solid var(--color-glass-border); }
```

---

### 发光效果

**文字发光**:
```css
.glow-text-primary {
  text-shadow: 0 0 12px var(--color-primary);
}
```

**盒子发光**:
```css
.glow-box-primary {
  box-shadow: 0 0 24px var(--color-primary-dim);
}
```

**光晕强度随主题变化**:
- PolarLight: 0.35 (弱)
- MidnightBlue: 0.45 (柔)
- DeepTechFuturistic: 0.55 (中)
- CyberPurple: 0.55 (强)

---

### 性能优化

**减少动画偏好**:
```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
  
  .glass-morphism {
    backdrop-filter: none !important;
  }
}
```

**硬件加速**:
```css
.mesh-blob {
  will-change: transform;
  contain: layout style;
}

.mesh-bg {
  contain: strict;
  transform: translateZ(0);
}
```

---

## 响应式设计

### 断点定义

| 断点 | 宽度 | 说明 |
|------|------|------|
| Mobile | < 768px | 手机竖屏 |
| Tablet | 768px - 1024px | 平板横屏 |
| Desktop | > 1024px | 桌面显示器 |

---

### 移动端适配

#### 侧边栏

```css
@media (max-width: 767px) {
  .app-sider {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: 280px;
    transform: translateX(-100%);
    z-index: 999;
  }
  
  .app-sider.mobile-visible {
    transform: translateX(0);
  }
}
```

**汉堡菜单按钮**:
```css
.mobile-menu-btn {
  position: fixed;
  top: 16px;
  left: 16px;
  z-index: 997;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
}
```

---

#### 聊天界面

**侧边面板**:
```css
@media (max-width: 768px) {
  .side-panel {
    position: absolute;
    right: 0;
    top: 0;
    width: 100%;
    max-width: 400px;
    box-shadow: -4px 0 24px rgba(0, 0, 0, 0.2);
  }
}
```

---

### 触摸优化

- 按钮最小点击区域: 44×44px
- 增加触摸反馈
- 禁用双击缩放

---

## 无障碍设计

### 键盘导航

- 所有交互元素可通过 Tab 键访问
- `focus-visible` 样式清晰可见
- ESC 键关闭模态框/抽屉

### 屏幕阅读器

- 语义化 HTML 标签
- ARIA 属性补充
- 图标添加 `aria-label`

### 对比度

遵循 WCAG 2.1 AA 标准：
- 正常文字: 至少 4.5:1
- 大号文字: 至少 3:1

---

## 设计规范总结

### ✅ 已实现的设计原则

1. **一致性**: 统一的圆角、间距、颜色系统
2. **层次感**: 通过毛玻璃、阴影、透明度建立视觉层次
3. **反馈性**: 所有交互都有视觉反馈
4. **流畅性**: 平滑的过渡动画，无突兀跳转
5. **沉浸感**: 星空背景 + 动态特效营造氛围
6. **可访问性**: 支持键盘导航、屏幕阅读器、减少动画偏好

### 🎨 设计语言关键词

- **未来感**: 深色主题、发光效果、科技配色
- **温暖**: 柔和的光晕、圆润的边角、自然的动画
- **专业**: 清晰的排版、合理的留白、精准的间距
- **灵动**: 动态背景、流畅过渡、微妙的交互

---

## 附录

### 文件结构

```
frontend/src/
├── components/
│   ├── chat/           # 聊天相关组件
│   │   ├── ChatView.vue
│   │   ├── ChatMessage.vue
│   │   ├── ChatInput.vue
│   │   ├── StreamPanel.vue
│   │   └── EnergyPulse.vue
│   ├── common/         # 通用组件
│   │   ├── StarField.vue
│   │   ├── RainEffect.vue
│   │   ├── AuroraEffect.vue
│   │   ├── FireflyEffect.vue
│   │   ├── MistEffect.vue
│   │   ├── ThemeModal.vue
│   │   └── WaveEffect.vue
│   └── layout/         # 布局组件
│       ├── AppSider.vue
│       ├── LatencyBar.vue
│       └── SettingsDrawer.vue
├── views/              # 页面视图
│   ├── ResonanceView.vue
│   ├── InsightView.vue
│   ├── GovernanceView.vue
│   ├── SettingsView.vue
│   ├── SecurityView.vue
│   ├── SystemLogView.vue
│   └── ComingSoonView.vue
├── stores/             # Pinia Store
│   ├── themeStore.ts
│   └── settingsStore.ts
├── composables/        # 组合式函数
│   ├── useChat.ts
│   ├── useWebSocket.ts
│   ├── useAsr.ts
│   ├── useTts.ts
│   └── useSettings.ts
├── theme/              # 主题配置
│   └── themes.ts
├── types/              # TypeScript 类型
│   └── index.ts
└── utils/              # 工具函数
    └── request.ts
```

### 关键技术决策

1. **为什么选择 Three.js 而非 2D 图表库?**
   - 3D 银河系效果更符合产品定位
   - 更好的视觉冲击力
   - 支持复杂的交互（旋转、缩放）

2. **为什么使用毛玻璃效果?**
   - 现代设计趋势
   - 增强层次感
   - 与星空背景完美融合

3. **为什么实现多种背景特效?**
   - 满足用户个性化需求
   - 增强产品趣味性
   - LocalStorage 持久化提升用户体验

4. **为什么采用智能滚动策略?**
   - 避免干扰用户阅读历史消息
   - 提升长对话体验
   - 符合主流聊天应用习惯

---

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2026-04-14 | 初始版本，基于代码逆向生成 |

---

**文档维护者**: AI Assistant  
**联系方式**: 通过项目 Issue 反馈问题
