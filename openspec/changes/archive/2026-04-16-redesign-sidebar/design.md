## Context

当前的侧边栏 `AppSider.vue` 组件在 UI 布局和信息架构上存在不合理之处。
- **信息架构**：“灵墟之境”作为主页面，在侧边栏的“核心能力”菜单中冗余。而真正属于核心能力的“记忆图谱”和“记忆治理”目前被放置在底部的“基础设施”区域。
- **UI 布局**：“会话列表”部分采用了类似卡片的深色背景和边框设计（过度的盒子嵌套），这在整个偏平、极简的深色主题中显得极其突兀。此外，如果会话列表变得很长，向下滚动时会将底部的“基础设施”菜单挤出屏幕。
- **信息密度**：单条会话卡片上显示了精确到秒的时间戳，占用了过多垂直空间。

## Goals / Non-Goals

**Goals:**
- 将“记忆图谱”和“记忆治理”移动到“核心能力”区域。
- 从“核心能力”中移除“灵墟之境”菜单。
- 移除会话列表的深色外壳背景和边框，实现扁平化。
- 将“新建会话”按钮放置在“会话列表”标题右侧。
- 简化会话项内容，去除精确到秒的时间戳。
- 实现 Flex 局部滚动：侧边栏整体使用 Flex 布局，会话列表区域自适应占据剩余高度并在内部滚动，保证“核心能力”和“基础设施”菜单始终固定可见。

**Non-Goals:**
- 不涉及深色主题配色变量 `var(--color-...)` 的重构。
- 不修改现有的任何后端接口或会话数据获取逻辑。
- 不重构移动端侧边栏抽屉的交互逻辑。

## Decisions

- **DOM 结构重构**：
  将原本的 `.core-nav` 中的 `.session-module` 提取出来作为一个独立的 Flex 元素 (`flex: 1; overflow-y: auto`)。原来的 `.core-nav` 重命名为 `.core-menus`，并将 `.infra-nav` 和 `.core-menus` 设置为 `flex-shrink: 0`。
  
  ```vue
  <!-- 重构后的结构 -->
  <div class="sider-inner"> <!-- display: flex; flex-direction: column -->
    <div class="logo-section">...</div>
    <div class="session-section"> <!-- flex: 1; overflow-y: auto -->
       <!-- 会话列表 -->
    </div>
    <div class="core-menus"> <!-- flex-shrink: 0 -->
       <!-- 核心能力菜单 -->
    </div>
    <div class="infra-nav"> <!-- flex-shrink: 0 -->
       <!-- 基础设施菜单 -->
    </div>
  </div>
  ```

- **CSS 样式清理**：
  移除 `.session-module` 中的 `border`、`background` 和 `backdrop-filter` 样式。调整 `.session-header` 的结构，将 `[+]` 按钮嵌入其中。移除 `.session-item-body small` 中精确到秒的时间显示逻辑。

- **菜单项数据更新**：
  在 `AppSider.vue` 的 `<script setup>` 中，更新 `mainNav` 数组（移除 `resonance`，移入 `insight` 和 `governance`），更新 `infraNav` 数组（只保留 `settings`、`security`、`logs`）。

## Risks / Trade-offs

- **Risk: 响应式布局失效** → Mitigation: 在移动端模式（`mobileVisible=true`）下，侧边栏是固定高度的抽屉，由于我们保留了 `sider-inner` 的 `height: 100%`，Flex 布局的变更应该能天然兼容移动端，但需要在修改后在浏览器开发者工具中验证移动端视图。
- **Risk: 激活菜单状态（activeMenu）逻辑异常** → Mitigation: 由于移除了“灵墟之境”（`resonance`），在 `AppSider.vue` 中的 `showSessionModule` computed 属性需要更新。会话列表将始终显示，或者当激活其他菜单时（如在系统设置中）依然保持可见。我们需要移除 `showSessionModule` 逻辑，让会话列表永久显示。
