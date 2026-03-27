<script setup lang="ts">
// 顶部栏已简化，主题切换功能已移至系统设置
</script>

<template>
  <div class="top-nav-bar">
    <div class="nav-content">
      <div class="nav-left">
        <span class="page-title">灵枢 AI</span>
      </div>
      <div class="nav-right">
        <!-- 主题按钮已移除，请前往系统设置 > 基础设置中切换主题 -->
      </div>
    </div>
  </div>
</template>

<style scoped>
.top-nav-bar {
  height: 100%;
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--color-glass-border);
  width: 100%;
}

.nav-content {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  max-width: 100vw;
}

.nav-left {
  flex: 1;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
  /* 主题按钮已移至系统设置 */
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.02em;
}
</style>
