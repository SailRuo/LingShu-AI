<script setup lang="ts">
import { Palette } from 'lucide-vue-next'
import ThemeModal from '@/components/common/ThemeModal.vue'
import { ref } from 'vue'

const showThemeModal = ref(false)
</script>

<template>
  <div class="top-nav-bar">
    <div class="nav-content">
      <div class="nav-left">
        <span class="page-title">灵枢 AI</span>
      </div>
      <div class="nav-right">
        <button class="theme-btn" @click="showThemeModal = true" title="切换主题">
          <Palette :size="18" />
          <span>主题</span>
        </button>
      </div>
    </div>
  </div>

  <ThemeModal v-model:open="showThemeModal" />
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
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.theme-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  height: 36px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 8px;
  color: var(--color-text);
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 13px;
}

.theme-btn:hover {
  background: var(--color-surface-elevated);
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>
