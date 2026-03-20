<script setup lang="ts">
import { Bell, Search, Maximize2, User, Command } from 'lucide-vue-next'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'
</script>

<template>
  <div class="top-nav-bar">
    <div class="nav-content">
      <div class="nav-left">
        <div class="search-box">
          <Search :size="16" class="search-icon" />
          <input type="text" placeholder="搜索..." class="search-input" />
          <kbd class="search-kbd"><Command :size="10" />K</kbd>
          <div class="divider"></div>
          <ThemeSwitcher />
        </div>
      </div>
      <div class="nav-right">
        <div class="action-btns">
          <button class="action-btn"><Bell :size="18" /></button>
          <button class="action-btn"><Maximize2 :size="18" /></button>
        </div>
        <div class="user-area">
          <div class="user-info">
            <span class="user-name">Dr. Neural</span>
            <span class="user-role">管理员</span>
          </div>
          <div class="avatar"><User :size="18" /></div>
        </div>
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
  display: flex;
  align-items: center;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  height: 36px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.search-box:focus-within {
  border-color: var(--color-primary);
  background: var(--color-surface-elevated);
}

.search-icon {
  color: var(--color-text-dim);
  flex-shrink: 0;
}

.search-input {
  width: 160px;
  background: transparent;
  border: none;
  outline: none;
  font-size: 13px;
  color: var(--color-text);
}

.search-input::placeholder {
  color: var(--color-text-dim);
}

.search-kbd {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 4px;
  font-size: 10px;
  color: var(--color-text-dim);
}

.divider {
  width: 1px;
  height: 20px;
  background: var(--color-outline);
  margin: 0 4px;
}

.action-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: transparent;
  border: 1px solid transparent;
  color: var(--color-text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: var(--color-surface);
  border-color: var(--color-outline);
  color: var(--color-primary);
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 12px 6px 6px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-area:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-elevated);
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.user-role {
  font-size: 10px;
  color: var(--color-text-dim);
}

.avatar {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-dim);
  border-radius: 50%;
  color: var(--color-primary);
}
</style>
