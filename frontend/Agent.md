# LingShu-AI Frontend Agent 指南

这是给进入 `frontend/` 目录的 Codex 用的工作手册。先读它，再看相关文档和实现。

## 1. 这个前端在做什么

这里是 LingShu-AI 的用户交互层，负责承接聊天、记忆洞察、系统设置、主动关怀、图谱展示和桌面端适配。

前端技术栈：

- Vue 3 + TypeScript + Vite
- Naive UI + Tailwind CSS
- Pinia + VueUse
- MarkdownIt + Highlight.js
- Neovis.js / Three.js / v-network-graph
- Tauri 2 桌面端适配

## 2. 先看哪些内容

优先阅读：

- `../doc/architecture/UI_UX设计文档.md`
- `../doc/implementation/Tauri实施文档.md`
- `../doc/implementation/记忆图谱3D银河系实施文档.md`
- `../doc/architecture/对话调用链路详解.md`
- `../README.md`
- `./README.md`

然后再看这些关键文件：

- `src/main.ts`
- `src/App.vue`
- `src/style.css`
- `src/utils/request.ts`
- `src/composables/useWebSocket.ts`
- `src/composables/useChat.ts`
- `src/composables/useProactive.ts`
- `src/composables/useTts.ts`
- `src/composables/useAsr.ts`
- `src/stores/themeStore.ts`
- `src/stores/settingsStore.ts`
- `src/stores/agentsStore.ts`
- `src/views/ResonanceView.vue`
- `src/views/InsightView.vue`
- `src/views/SettingsView.vue`
- `src/views/SystemLogView.vue`
- `src/components/chat/ChatView.vue`
- `src/components/chat/ChatMessage.vue`
- `src/components/chat/ChatInput.vue`
- `src/components/McpSettings.vue`
- `src/components/common/*`
- `src/components/layout/*`
- `src-tauri/*`

## 3. 视觉和交互原则

- 保留项目既有的 Cyber-Zen 氛围，不要把它改成普通后台管理界面。
- 优先用玻璃感、星空、微光、渐变、层次和空间感表达品牌气质。
- 聊天页面要有“活着”的感觉，但不要为了炫技牺牲可读性。
- 记忆图谱、共鸣页、设置页之间的视觉语言要统一。
- 所有改动都要考虑桌面端窗口尺寸和 Tauri 兼容性。

## 4. 前端工作方式

你在做前端改动时，优先遵守这条顺序：

1. 先理解当前页面已有结构和动效。
2. 再判断改动会不会影响 WebSocket、状态管理或 Tauri。
3. 只做最小且安全的修改。
4. 需要加新视觉效果时，先复用现有效果组件。
5. 最后验证构建和桌面端运行。

## 5. 常见约束

- 前端不要直连数据库，也不要把后端逻辑搬到页面里。
- 图谱页的数据应来自后端 API，而不是在浏览器里拼数据库连接。
- WebSocket 连接、重连、心跳、用户注册和消息类型要和后端协议保持一致。
- Markdown 渲染、代码高亮、流式消息和工具调用展示要一起检查。
- 不要轻易改动主题状态、菜单状态、LocalStorage 键名和路由式视图切换逻辑。

## 6. 常用验证

- 开发运行：`npm run dev`
- 构建检查：`npm run build`
- 桌面端开发：`npm run tauri:dev`

改动后至少检查：

- UI 布局是否在桌面和窄屏下都正常
- 流式聊天是否还能平滑显示
- WebSocket 断线重连和注册流程是否正常
- 主题、动画和图谱页面是否仍然协调

## 7. 容易踩坑的地方

- `App.vue` 不是普通壳组件，它承载了全局布局、主题和页面切换。
- `useWebSocket.ts` 是聊天协议的核心，不要随便改消息结构。
- `request.ts` 的 baseUrl 处理会影响本地、后端和 Tauri 环境。
- `InsightView.vue` 的图谱是强视觉页面，改动要同时考虑性能和可读性。
- `src-tauri/` 的改动要和 Web 端接口变化一起验证。

## 8. 输出要求

如果你在这个目录里做代码改动，最后用中文说明：

- 改了什么
- 为什么这么改
- 哪些文件受影响
- 你验证了什么

