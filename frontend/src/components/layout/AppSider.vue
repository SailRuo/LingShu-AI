<script setup lang="ts">
import { NMenu, NIcon } from 'naive-ui'
import { Layers, Activity as ActivityIcon, FileText, Settings, Hexagon, Zap, Radio, DatabaseBackup } from 'lucide-vue-next'
import type { Component } from 'vue'
import { h } from 'vue'

const props = defineProps<{ 
  activeMenu: string,
  collapsed: boolean,
  mobileVisible: boolean
}>()
const emit = defineEmits<{ 
  (e: 'update:activeMenu', key: string): void,
  (e: 'update:collapsed', value: boolean): void,
  (e: 'update:mobileVisible', value: boolean): void
}>()

const renderIcon = (c: Component) => () => h(NIcon, null, { default: () => h(c) })

const mainNav = [
  { label: '灵墟之境', key: 'resonance', icon: renderIcon(Hexagon) },
  { label: '记忆图谱', key: 'insight', icon: renderIcon(Zap) },
  { label: '记忆流光', key: 'stream', icon: renderIcon(Radio) },
  { label: '记忆治理', key: 'governance', icon: renderIcon(DatabaseBackup) },
  { label: '全维口袋', key: 'pocket', icon: renderIcon(Layers) },
]

const infraNav = [
  { label: '系统设置', key: 'settings', icon: renderIcon(Settings) },
  { label: '系统状态', key: 'security', icon: renderIcon(ActivityIcon) },
  { label: '系统日志', key: 'logs', icon: renderIcon(FileText) },
]

</script>

<template>
  <!-- Mobile Overlay -->
  <div 
    v-if="mobileVisible" 
    class="mobile-overlay"
    @click="emit('update:mobileVisible', false)"
  ></div>
  
  <aside class="app-sider" :class="{ collapsed, 'mobile-visible': mobileVisible }">
    <div class="sider-inner">
      <!-- Logo Area -->
      <div class="logo-section">
        <div class="logo-mark">
          <div class="logo-glow"></div>
          <Hexagon :size="24" class="logo-icon" />
        </div>
        <div class="logo-text">
          <span class="logo-title">灵枢</span>
          <span class="logo-sub">LINGSHU.AI</span>
        </div>
      </div>

      <!-- Core Navigation -->
      <div class="core-nav">
        <div v-if="!collapsed" class="section-header">
          <span class="section-label">核心能力</span>
          <div class="section-line"></div>
        </div>
        <n-menu
          :value="activeMenu"
          :options="mainNav"
          :collapsed="collapsed"
          class="nav-menu"
          @update:value="(k: string) => {
            emit('update:activeMenu', k)
            if (mobileVisible) emit('update:mobileVisible', false)
          }"
        />
      </div>

      <!-- Infrastructure - Fixed to Bottom -->
      <div class="infra-nav">
        <div v-if="!collapsed" class="section-header">
          <span class="section-label">基础设施</span>
          <div class="section-line"></div>
        </div>
        <n-menu
          :value="activeMenu"
          :options="infraNav"
          :collapsed="collapsed"
          class="nav-menu infra-menu"
          @update:value="(k: string) => {
            emit('update:activeMenu', k)
            if (mobileVisible) emit('update:mobileVisible', false)
          }"
        />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.app-sider {
  grid-area: sidebar;
  width: 260px;
  height: 100%;
  background: transparent !important; /* 改为透明以显示星空 */
  border-right: 1px solid var(--color-glass-border) !important;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

/* Desktop Collapsed State */
.app-sider.collapsed {
  width: 0;
  border-right: none !important;
  overflow: hidden;
}

/* Mobile Styles */
@media (max-width: 767px) {
  .app-sider {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: 280px;
    transform: translateX(-100%);
    z-index: 999;
    box-shadow: 4px 0 24px rgba(0, 0, 0, 0.3);
  }
  
  .app-sider.mobile-visible {
    transform: translateX(0);
  }
  
  .mobile-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 998;
    opacity: 0;
    animation: fadeIn 0.3s ease forwards;
  }
}

@keyframes fadeIn {
  to {
    opacity: 1;
  }
}

.sider-inner {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  width: 260px; /* 保持内部宽度不变，防止折叠时内容挤压 */
}

/* Logo Section */
.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px 24px;
  border-bottom: 1px solid var(--color-outline);
  margin-bottom: 20px;
  flex-shrink: 0;
  overflow: hidden;
  height: 64px;
}

.logo-mark {
  position: relative;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-dim);
  border-radius: 12px;
  border: 1px solid var(--color-primary);
}

.logo-glow {
  position: absolute;
  inset: -4px;
  background: var(--color-primary);
  opacity: 0.15;
  border-radius: 16px;
  filter: blur(8px);
  animation: pulse-glow 3s ease-in-out infinite;
}

@keyframes pulse-glow {
  0%, 100% { opacity: 0.1; transform: scale(1); }
  50% { opacity: 0.2; transform: scale(1.05); }
}

.logo-icon {
  color: var(--color-primary);
  position: relative;
  z-index: 1;
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.logo-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.logo-sub {
  font-size: 10px;
  color: var(--color-text-dim);
  letter-spacing: 0.1em;
  font-family: 'Fira Code', monospace;
}

/* Core Navigation */
.core-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* Infrastructure Navigation - Fixed to Bottom */
.infra-nav {
  margin-top: auto;
  flex-shrink: 0;
  width: 100%;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  margin-bottom: 8px;
  transition: opacity 0.2s ease;
}

.app-sider.collapsed .section-header {
  display: none;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.section-line {
  flex: 1;
  height: 1px;
  background: var(--color-outline);
}

/* Menu Styles */
.nav-menu {
  background: transparent !important;
}

:deep(.n-menu-item) {
  margin: 2px 0;
  width: 100%;
}

:deep(.n-menu-item-content) {
  padding: 0 16px !important;
  height: 44px !important;
  border-radius: 0 10px 10px 0 !important;
  margin: 2px 0 !important;
  transition: all 0.3s ease !important;
  width: 100% !important;
  box-sizing: border-box !important;
  position: relative;
  overflow: hidden;
}

:deep(.n-menu-item-content::before) {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 0;
  background: linear-gradient(270deg, var(--color-surface) 0%, transparent 100%);
  transition: width 0.3s ease;
  border-radius: 0 10px 10px 0;
}

:deep(.n-menu-item-content:hover::before) {
  width: 100%;
}

:deep(.n-menu-item-content--selected::before) {
  width: 100%;
  background: linear-gradient(270deg, var(--color-primary-dim) 0%, transparent 80%);
}

:deep(.n-menu-item-content--selected .n-menu-item-content-header) {
  color: var(--color-primary) !important;
  font-weight: 600 !important;
}

:deep(.n-menu-item-content-header) {
  font-size: 14px !important;
  color: var(--color-text) !important;
  position: relative;
  z-index: 1;
}

:deep(.n-menu-icon) {
  color: var(--color-text-dim) !important;
  position: relative;
  z-index: 1;
  font-size: 20px !important;
}

:deep(.n-menu-item-content--selected .n-menu-icon) {
  color: var(--color-primary) !important;
}

.infra-menu :deep(.n-menu-item-content) {
  height: 40px !important;
}

.infra-menu :deep(.n-menu-item-content-header) {
  font-size: 13px !important;
  color: var(--color-text-dim) !important;
}

/* Fade Transition for Logo Text */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
