<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider, darkTheme } from 'naive-ui'
import { computed, onMounted, markRaw, h } from 'vue'
import { useThemeStore } from '@/stores/themeStore'
import { useLocalStorage } from '@vueuse/core'
import AppSider from '@/components/layout/AppSider.vue'
import LatencyBar from '@/components/layout/LatencyBar.vue'
import SystemStatusBar from '@/components/layout/SystemStatusBar.vue'
import ResonanceView from '@/views/ResonanceView.vue'
import InsightView from '@/views/InsightView.vue'
import SettingsView from '@/views/SettingsView.vue'
import ComingSoonView from '@/views/ComingSoonView.vue'
import SystemLogView from '@/views/SystemLogView.vue'

const themeStore = useThemeStore()

// 使用 LocalStorage 持久化当前菜单状态，解决刷新重置问题
const activeMenu = useLocalStorage('lingshu-active-menu', 'resonance')

// 视图注册表，优化架构，方便后续扩展
const viewMap: Record<string, any> = {
  resonance: markRaw(ResonanceView),
  insight: markRaw(InsightView),
  pocket: markRaw(() => h(ComingSoonView, { title: '全维口袋' })),
  settings: markRaw(SettingsView),
  security: markRaw(() => h(ComingSoonView, { title: '安全保障' })),
  logs: markRaw(SystemLogView)
}

const currentView = computed(() => viewMap[activeMenu.value] || viewMap.resonance)

onMounted(() => {
  themeStore.initTheme()
})

const naiveTheme = computed(() => themeStore.current.isDark ? darkTheme : null)
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
                  <!-- Header - Only in Right Area -->
                  <header class="app-header">
                    <LatencyBar />
                  </header>
                  
                  <!-- Main Content -->
                  <main class="main-content">
                    <router-view v-if="false" /> <!-- Placeholder for future vue-router -->
                    <transition name="fade-slide" mode="out-in">
                      <component :is="currentView" :key="activeMenu" />
                    </transition>
                  </main>
                </div>
                
                <!-- Footer - Full Width Across Sidebar and Content -->
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
  min-height: 0;
  background: transparent;
}

.app-header {
  height: 64px;
  flex-shrink: 0;
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
