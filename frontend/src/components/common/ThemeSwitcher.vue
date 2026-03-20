<script setup lang="ts">
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
</script>

<template>
  <div class="theme-switcher">
    <button
      v-for="theme in themeStore.availableThemes"
      :key="theme.key"
      class="theme-btn"
      :class="{ active: themeStore.current.key === theme.key }"
      :title="theme.label"
      @click="themeStore.setTheme(theme.key)"
    >
      <component :is="theme.icon" :size="14" class="theme-icon" />
    </button>
  </div>
</template>

<style scoped>
.theme-switcher {
  display: flex;
  align-items: center;
  gap: 2px;
}

.theme-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  opacity: 0.5;
}

.theme-btn:hover {
  opacity: 0.8;
  background: var(--color-surface-elevated);
}

.theme-btn.active {
  opacity: 1;
  background: var(--color-primary-dim);
}

.theme-icon {
  color: var(--color-text);
}
</style>
