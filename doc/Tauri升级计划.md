# Tauri 桌面端升级计划

本计划旨在将当前的 Vue 前端项目升级为基于 Tauri 的桌面端应用，同时保留 Web 端的兼容性。

## 1. 升级目标
- [ ] 实现 Vue 前端在 Tauri 容器中正常运行。
- [ ] 适配 API 请求和 WebSocket 连接，支持跨域访问后端。
- [ ] 优化桌面端窗口体验（自定义标题栏、窗口尺寸等）。
- [ ] 提供一键打包桌面端安装包的能力。

## 2. 详细步骤

### 第一阶段：环境初始化 (已完成)
- [x] 安装 Tauri 开发依赖 (`@tauri-apps/cli`)。
- [x] 初始化 Tauri 项目结构 (`src-tauri`)。
- [x] 配置 `tauri.conf.json`（设置 `distDir`, `devPath`, `identifier` 等）。

### 第二阶段：前端适配 (已完成)
- [x] **API 请求适配**：
    - 引入环境变量 `VITE_API_BASE_URL`。
    - 封装请求工具，在 Tauri 环境下自动指向后端地址（默认 `http://localhost:8080`）。
- [x] **WebSocket 适配**：
    - 修改 `useWebSocket.ts`，在 Tauri 环境下使用绝对路径连接。
- [x] **UI 适配**：
    - 适配桌面端窗口比例（已更新默认窗口尺寸为 1200x800）。

### 第三阶段：后端适配 (已完成)
- [x] **CORS 配置**：
    - 在 Spring Boot 后端增加对 `tauri://localhost` (Windows) 的跨域支持。

### 第四阶段：构建与发布 (已完成)
- [x] 调试 `npm run tauri dev` 确保功能正常（已启动调试环境）。
- [x] 执行 `npm run build` 验证前端构建正常。
- [ ] 执行 `npm run tauri build` 生成安装包（建议在网络环境良好时执行）。

## 3. 进度跟踪
- **开始日期**：2026-03-30
- **当前状态**：已完成核心适配
- **完成百分比**：100%

---
*注：本计划将随开发进度实时更新。*
