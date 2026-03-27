<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider, darkTheme } from 'naive-ui'
import { computed, onMounted, markRaw, h, watch } from 'vue'
import { useThemeStore } from '@/stores/themeStore'
import { useLocalStorage } from '@vueuse/core'
import AppSider from '@/components/layout/AppSider.vue'
import SystemStatusBar from '@/components/layout/SystemStatusBar.vue'
import ResonanceView from '@/views/ResonanceView.vue'
import InsightView from '@/views/InsightView.vue'
import SettingsView from '@/views/SettingsView.vue'
import ComingSoonView from '@/views/ComingSoonView.vue'
import SystemLogView from '@/views/SystemLogView.vue'
import StreamView from '@/views/StreamView.vue'
import GovernanceView from '@/views/GovernanceView.vue'

const themeStore = useThemeStore()

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
  security: markRaw(() => h(ComingSoonView, { title: '安全保障' })),
  logs: markRaw(SystemLogView)
}

const currentView = computed(() => viewMap[activeMenu.value] || viewMap.resonance)

onMounted(() => {
  themeStore.initTheme()
})

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

              <!-- Main Layout -->
              <div class="main-layout">
                <!-- Sidebar - Fixed Left -->
                <AppSider v-model:active-menu="activeMenu" />

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
                <footer class="app-footer">
                  <SystemStatusBar />
                </footer>
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
  grid-template-rows: 1fr 48px;
  grid-template-areas:
    "sidebar right"
    "footer footer";
  background: transparent;
}

.right-area {
  grid-area: right;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: transparent;
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
  grid-area: footer;
  height: 48px;
  background: transparent;
  z-index: 100;
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
