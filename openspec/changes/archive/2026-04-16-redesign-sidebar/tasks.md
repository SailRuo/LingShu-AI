## 1. 菜单数据与逻辑重构

- [x] 1.1 在 `AppSider.vue` 中，从 `mainNav` 移除 `resonance`，移入 `insight` 和 `governance`。
- [x] 1.2 在 `AppSider.vue` 中，更新 `infraNav`，仅保留 `settings`、`security`、`logs`。
- [x] 1.3 在 `AppSider.vue` 中，移除 `showSessionModule` 的 computed 属性，让会话列表永久显示（因为不再依赖选中“灵墟之境”）。
- [x] 1.4 在 `AppSider.vue` 中，移除对 `resonance` 菜单的特殊处理逻辑（如 `ensureSessionsLoaded` 函数中对 `activeMenu !== 'resonance'` 的判断，应改为始终加载）。

## 2. DOM 结构与 Flex 布局重构

- [x] 2.1 在 `AppSider.vue` 的 `<template>` 中，重组 `.sider-inner` 为三段式：顶部会话列表区、中部 `.core-menus`、底部 `.infra-nav`。
- [x] 2.2 在 `AppSider.vue` 的 `<style>` 中，为 `.sider-inner` 添加 Flex 布局样式（`display: flex`, `flex-direction: column`）。
- [x] 2.3 将新的会话列表容器设置为自适应滚动（`flex: 1`, `overflow-y: auto`, `min-height: 0`）。
- [x] 2.4 将 `.core-menus` 和 `.infra-nav` 设置为不压缩（`flex-shrink: 0`），并调整必要的间距（如 `margin-top`）。

## 3. 会话列表扁平化样式调整

- [x] 3.1 在 `AppSider.vue` 的 `<style>` 中，移除 `.session-module` 的背景色（`background`、`backdrop-filter`）和边框（`border`、`border-radius`）。
- [x] 3.2 调整 `.session-header` 的样式，将 `[+]` 按钮放置在标题右侧，并移除多余内边距。
- [x] 3.3 在 `<template>` 中，移除会话卡片上的精确时间显示（`new Date(...).toLocaleString()`），使其仅保留标题。
- [x] 3.4 微调单条会话 `.session-item` 的高度、内边距，确保紧凑且在被选中时保持扁平的高亮样式。