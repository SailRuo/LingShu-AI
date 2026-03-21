<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider, darkTheme } from 'naive-ui'
import { computed, onMounted } from 'vue'
import { useThemeStore } from '@/stores/themeStore'
import AppSider from '@/components/layout/AppSider.vue'
import LatencyBar from '@/components/layout/LatencyBar.vue'
import SystemStatusBar from '@/components/layout/SystemStatusBar.vue'
import ResonanceView from '@/views/ResonanceView.vue'
import InsightView from '@/views/InsightView.vue'
import SettingsView from '@/views/SettingsView.vue'
import ConsoleHome from '@/views/ConsoleHome.vue'
import SystemLogView from '@/views/SystemLogView.vue'
import { ref } from 'vue'

const themeStore = useThemeStore()
const activeMenu = ref('resonance')

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
            <ResonanceView v-if="activeMenu === 'resonance'" />
            <InsightView v-else-if="activeMenu === 'insight'" />
            <ConsoleHome v-else-if="activeMenu === 'pocket'" />
            <SettingsView v-else-if="activeMenu === 'settings'" />
            <ConsoleHome v-else-if="activeMenu === 'security'" />
            <SystemLogView v-else-if="activeMenu === 'logs'" />
            <ResonanceView v-else />
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
}

.app-footer {
  grid-area: footer;
  height: 48px;
  background: transparent;
}
</style>
