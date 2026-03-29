<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider, darkTheme } from 'naive-ui'
import { computed, onMounted, markRaw, h, watch, ref, onUnmounted } from 'vue'
import { useThemeStore } from '@/stores/themeStore'
import { useLocalStorage } from '@vueuse/core'
import { Menu, PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'
import AppSider from '@/components/layout/AppSider.vue'
import SystemStatusBar from '@/components/layout/SystemStatusBar.vue'
import ResonanceView from '@/views/ResonanceView.vue'
import InsightView from '@/views/InsightView.vue'
import SettingsView from '@/views/SettingsView.vue'
import ComingSoonView from '@/views/ComingSoonView.vue'
import SystemLogView from '@/views/SystemLogView.vue'
import StreamView from '@/views/StreamView.vue'
import GovernanceView from '@/views/GovernanceView.vue'
import StarField from '@/components/common/StarField.vue'
import SecurityView from '@/views/SecurityView.vue'

const themeStore = useThemeStore()

// 侧边栏状态管理
const sidebarCollapsed = ref(false)
const mobileSidebarVisible = ref(false)
const isMobile = ref(false)


// 监听窗口大小变化
function checkMobile() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    mobileSidebarVisible.value = false
  }
}



onMounted(() => {
  themeStore.initTheme()
  checkMobile()
  window.addEventListener('resize', checkMobile)
})



// 使用 LocalStorage 持久化当前菜单状态，解决刷新重置问题
const activeMenu = useLocalStorage('lingshu-active-menu', 'resonance')

// 监听菜单变化，如果是 settings-* 则自动切换到 settings
watch(activeMenu, (newVal, oldVal) => {
  const settingsKeys = ['settings-model', 'settings-agents', 'settings-proactive', 'settings-mcp']
  if (settingsKeys.includes(newVal) && oldVal !== 'settings' && !settingsKeys.includes(oldVal || '')) {
    // 从外部进入设置页面，保持当前 key 不变
  } else if (settingsKeys.includes(newVal) && oldVal === 'settings') {
    // 已经在设置页面内切换，保持当前 key 不变
  } else if (newVal === 'settings') {
    // 点击系统设置菜单，切换到 settings-model
    activeMenu.value = 'settings-model'
  }
}, { immediate: true })

// 视图注册表，优化架构，方便后续扩展
const viewMap: Record<string, any> = {
  resonance: markRaw(ResonanceView),
  insight: markRaw(InsightView),
  stream: markRaw(StreamView),
  governance: markRaw(GovernanceView),
  pocket: markRaw(() => h(ComingSoonView, { title: '全维口袋' })),
  'settings-model': markRaw(SettingsView),
  'settings-agents': markRaw(SettingsView),
  'settings-proactive': markRaw(SettingsView),
  'settings-mcp': markRaw(SettingsView),
  settings: markRaw(SettingsView),
  security: markRaw(SecurityView),
  logs: markRaw(SystemLogView)
}

const currentView = computed(() => viewMap[activeMenu.value] || viewMap.resonance)

const naiveTheme = computed(() => themeStore.current.isDark ? darkTheme : null)

// 动态计算组件属性
const currentViewComponentProps = computed(() => {
  const settingsKeys = ['settings-model', 'settings-agents', 'settings-proactive', 'settings-mcp', 'settings']
  if (settingsKeys.includes(activeMenu.value)) {
    return { 
      activeMenu: activeMenu.value, 
      'onUpdate:activeMenu': (v: string) => { activeMenu.value = v } 
    }
  }
  return {}
})

function getViewKey(menuKey: string): string {
  const settingsKeys = ['settings-model', 'settings-agents', 'settings-proactive', 'settings-mcp', 'settings']
  if (settingsKeys.includes(menuKey)) {
    return 'settings'
  }
  return menuKey
}
</script>

<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="themeStore.naiveOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <n-loading-bar-provider>
            <div class="app-container">
              <!-- Mesh Background -->
              <div class="mesh-bg">
                <div class="mesh-blob mesh-1"></div>
                <div class="mesh-blob mesh-2"></div>
                <div class="mesh-blob mesh-3"></div>
              </div>
              
              <!-- Star Field Background Component -->
              <StarField />

              <!-- Main Layout -->
              <div class="main-layout" :class="{ 'sidebar-collapsed': sidebarCollapsed && !isMobile }">
                <!-- Global Sidebar Toggle (Desktop) -->
                <button 
                  v-if="!isMobile"
                  class="global-collapse-toggle"
                  :class="{ 'is-collapsed': sidebarCollapsed }"
                  @click="sidebarCollapsed = !sidebarCollapsed"
                  title="折叠/展开侧边栏"
                >
                  <PanelLeftClose v-if="!sidebarCollapsed" :size="18" />
                  <PanelLeftOpen v-else :size="18" />
                </button>

                <!-- Mobile Header -->
                <button 
                  v-if="isMobile"
                  class="mobile-menu-btn"
                  @click="mobileSidebarVisible = true"
                  title="打开菜单"
                >
                  <Menu :size="24" />
                </button>
                
                <!-- Sidebar - Fixed Left -->
                <AppSider 
                  v-model:active-menu="activeMenu" 
                  v-model:collapsed="sidebarCollapsed"
                  v-model:mobile-visible="mobileSidebarVisible"
                />

                <!-- Right Area -->
                <div class="right-area">
                  <!-- Main Content -->
                  <main class="main-content">
                    <!-- router-view placeholder for future vue-router integration -->
                    <!-- <router-view v-if="false" /> -->
                    <transition name="fade-slide" mode="out-in">
                      <component 
                        :is="currentView" 
                        :key="getViewKey(activeMenu)" 
                        v-bind="currentViewComponentProps"
                      />
                    </transition>
                  </main>
                </div>
              </div>
            </div>
          </n-loading-bar-provider>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style scoped>
.app-container {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

.main-layout {
  height: 100%;
  display: grid;
  grid-template-columns: 260px 1fr;
  grid-template-rows: 1fr auto;
  grid-template-areas:
    "sidebar right"
    "footer footer";
  background: transparent; /* 保持透明以显示底层星空 */
  transition: grid-template-columns 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 1; /* 在星空之上 */
}

/* Desktop Collapsed State */
.main-layout.sidebar-collapsed {
  grid-template-columns: 0 1fr;
}

/* Global Collapse Toggle */
.global-collapse-toggle {
  position: fixed;
  top: 16px;
  left: 212px; /* 260px(侧边栏宽度) - 32px(按钮宽度) - 16px(右边距) */
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-dim);
  border: 1px solid var(--color-outline);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.global-collapse-toggle:hover {
  background: var(--color-glass-bg);
  color: var(--color-primary);
  border-color: var(--color-primary);
  transform: scale(1.05);
  box-shadow: 
    0 4px 12px var(--color-primary-dim),
    inset 0 0 20px rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.global-collapse-toggle.is-collapsed {
  left: 16px;
}

.global-collapse-toggle.is-collapsed:hover {
  transform: scale(1.05);
}

/* Mobile Layout */
@media (max-width: 767px) {
  .main-layout {
    grid-template-columns: 0 1fr;
  }
}

.right-area {
  grid-area: right;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: transparent; /* 改为透明以显示星空 */
  position: relative;
  overflow: hidden;
}

/* Mobile Menu Button */
.mobile-menu-btn {
  position: fixed;
  top: 16px;
  left: 16px;
  z-index: 997;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.mobile-menu-btn:hover {
  background: var(--color-primary);
  color: white;
  transform: scale(1.05);
}

@media (min-width: 768px) {
  .mobile-menu-btn {
    display: none;
  }
}

.main-content {
  flex: 1;
  min-height: 0;
  background: transparent;
  overflow: hidden;
  padding: 0;
  position: relative;
}

.app-footer {
  display: none;
}

/* 视图切换动画 - 符合 frontend-design 高级感要求 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
