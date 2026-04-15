<script setup lang="ts">
import { NMenu, NIcon, useMessage } from 'naive-ui'
import {
  Activity as ActivityIcon,
  FileText,
  Settings,
  Zap,
  DatabaseBackup,
  Hexagon,
  Loader2,
  Plus,
  MessageSquare
} from 'lucide-vue-next'
import type { Component } from 'vue'
import { computed, h, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatSessionStore } from '@/stores/chatSessionStore'

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

const message = useMessage()
const sessionStore = useChatSessionStore()
const { sessions, activeSessionId, isLoadingSessions } = storeToRefs(sessionStore)
const isCreatingSession = ref(false)

const renderIcon = (c: Component) => () => h(NIcon, null, { default: () => h(c) })

const mainNav = [
  { label: '记忆图谱', key: 'insight', icon: renderIcon(Zap) },
  { label: '记忆治理', key: 'governance', icon: renderIcon(DatabaseBackup) }
]

const infraNav = [
  { label: '系统设置', key: 'settings', icon: renderIcon(Settings) },
  { label: '系统状态', key: 'security', icon: renderIcon(ActivityIcon) },
  { label: '系统日志', key: 'logs', icon: renderIcon(FileText) }
]

async function ensureSessionsLoaded() {
  try {
    await sessionStore.fetchSessions(activeSessionId.value)
  } catch (error) {
    console.error('Failed to load sessions:', error)
  }
}

async function handleCreateSession() {
  if (isCreatingSession.value) return
  isCreatingSession.value = true
  try {
    const created = await sessionStore.createSession()
    message.success(`已创建 ${created.title}`)
  } catch (error) {
    console.error('Failed to create session:', error)
    message.error('新建会话失败，请稍后重试')
  } finally {
    isCreatingSession.value = false
  }
}

function handleSelectSession(sessionId: number) {
  if (activeSessionId.value === sessionId) return
  sessionStore.setActiveSession(sessionId)
  if (props.mobileVisible) {
    emit('update:mobileVisible', false)
  }
}

onMounted(() => {
  ensureSessionsLoaded()
})

watch(() => props.activeMenu, () => {
  ensureSessionsLoaded()
})
</script>

<template>
  <div
    v-if="mobileVisible"
    class="mobile-overlay"
    @click="emit('update:mobileVisible', false)"
  ></div>

  <aside class="app-sider" :class="{ collapsed, 'mobile-visible': mobileVisible }">
    <div class="sider-inner">
      <div class="logo-section">
        <div class="logo-mark">
          <img src="/bot.png" alt="Logo" class="logo-image" />
        </div>
        <div class="logo-text">
          <span class="logo-title">灵枢</span>
          <span class="logo-sub">LINGSHU.AI</span>
        </div>
      </div>

      <div v-if="!collapsed" class="session-section">
        <div class="session-header">
          <span class="section-label">会话记录</span>
          <button
            class="session-create-btn"
            :disabled="isCreatingSession"
            @click="handleCreateSession"
            title="新建会话"
          >
            <Loader2 v-if="isCreatingSession" :size="14" class="spin" />
            <Plus v-else :size="14" />
          </button>
        </div>

        <div class="session-list">
          <button
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: activeSessionId === session.id }"
            @click="handleSelectSession(session.id)"
          >
            <span class="session-item-icon">
              <MessageSquare :size="14" />
            </span>
            <span class="session-item-body">
              <strong>{{ session.title }}</strong>
            </span>
          </button>

          <div v-if="isLoadingSessions && sessions.length === 0" class="session-empty">
            <Loader2 :size="14" class="spin" />
            <span>加载会话中...</span>
          </div>
          <div v-else-if="!isLoadingSessions && sessions.length === 0" class="session-empty">
            <span>暂无会话</span>
          </div>
        </div>
      </div>

      <div class="core-menus">
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
  background: transparent !important;
  border-right: 1px solid var(--color-glass-border) !important;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.app-sider.collapsed {
  width: 0;
  border-right: none !important;
  overflow: hidden;
}

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
  width: 260px;
}

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
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  overflow: hidden;
}

.logo-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

.session-section {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  margin-bottom: 16px;
}

.core-menus {
  flex-shrink: 0;
  margin-bottom: 16px;
}

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

.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 12px;
  margin-bottom: 8px;
}

.session-create-btn {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: 1px solid rgba(56, 189, 248, 0.3);
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.session-create-btn:hover:not(:disabled) {
  background: rgba(56, 189, 248, 0.2);
  transform: scale(1.05);
}

.session-create-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  padding: 0 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: transparent;
  color: var(--color-text);
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.session-item:hover {
  background: rgba(255, 255, 255, 0.04);
}

.session-item.active {
  background: linear-gradient(270deg, rgba(56, 189, 248, 0.12) 0%, transparent 100%);
  color: var(--color-primary);
}

.session-item.active .session-item-body strong {
  color: var(--color-primary);
}

.session-item-icon {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-text-dim);
}

.session-item.active .session-item-icon {
  color: var(--color-primary);
}

.session-item-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.session-item-body strong {
  font-size: 13px;
  color: var(--color-text);
  font-weight: normal;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.2s ease;
}

.session-empty {
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-dim);
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
