<script setup lang="ts">
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider, darkTheme } from 'naive-ui'
import { computed, onMounted, markRaw, watch, ref } from 'vue'
import { useThemeStore } from '@/stores/themeStore'
import { useLocalStorage } from '@vueuse/core'
import { Menu, PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'
import AppSider from '@/components/layout/AppSider.vue'
import ResonanceView from '@/views/ResonanceView.vue'
import InsightView from '@/views/InsightView.vue'
import SettingsView from '@/views/SettingsView.vue'
import SystemLogView from '@/views/SystemLogView.vue'
import GovernanceView from '@/views/GovernanceView.vue'
import StarField from '@/components/common/StarField.vue'
import RainEffect from '@/components/common/RainEffect.vue'
import AuroraEffect from '@/components/common/AuroraEffect.vue'
import FireflyEffect from '@/components/common/FireflyEffect.vue'
import MistEffect from '@/components/common/MistEffect.vue'
import SecurityView from '@/views/SecurityView.vue'

const themeStore = useThemeStore()

// 渚ц竟鏍忕姸鎬佺鐞?
const sidebarCollapsed = ref(false)
const mobileSidebarVisible = ref(false)
const isMobile = ref(false)

// 鍔ㄧ敾鐗规晥锛屼娇鐢?LocalStorage 鎸佷箙鍖?
const animationEffect = useLocalStorage('lingshu-animation-effect', 'off')

// 鐩戝惉绐楀彛澶у皬鍙樺寲
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



// 浣跨敤 LocalStorage 鎸佷箙鍖栧綋鍓嶈彍鍗曠姸鎬侊紝瑙ｅ喅鍒锋柊閲嶇疆闂
const activeMenu = useLocalStorage('lingshu-active-menu', 'resonance')

// 鐩戝惉鑿滃崟鍙樺寲锛屽鏋滄槸 settings-* 鍒欒嚜鍔ㄥ垏鎹㈠埌 settings
watch(activeMenu, (newVal, oldVal) => {
  if (newVal === 'pocket') {
    activeMenu.value = 'resonance'
    return
  }
  const settingsKeys = ['settings-model', 'settings-agents', 'settings-proactive', 'settings-mcp']
  if (settingsKeys.includes(newVal) && oldVal !== 'settings' && !settingsKeys.includes(oldVal || '')) {
    // 浠庡閮ㄨ繘鍏ヨ缃〉闈紝淇濇寔褰撳墠 key 涓嶅彉
  } else if (settingsKeys.includes(newVal) && oldVal === 'settings') {
    // 宸茬粡鍦ㄨ缃〉闈㈠唴鍒囨崲锛屼繚鎸佸綋鍓?key 涓嶅彉
  } else if (newVal === 'settings') {
    // 鐐瑰嚮绯荤粺璁剧疆鑿滃崟锛屽垏鎹㈠埌 settings-model
    activeMenu.value = 'settings-model'
  }
}, { immediate: true })

// 瑙嗗浘娉ㄥ唽琛紝浼樺寲鏋舵瀯锛屾柟渚垮悗缁墿灞?
const viewMap: Record<string, any> = {
  resonance: markRaw(ResonanceView),
  insight: markRaw(InsightView),
  governance: markRaw(GovernanceView),
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

// 鍔ㄦ€佽绠楃粍浠跺睘鎬?
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
              <!-- Mesh Background (鏍规嵁寮€鍏虫帶鍒? -->
              <div class="mesh-bg" v-if="animationEffect !== 'off'">
                <div class="mesh-blob mesh-1"></div>
                <div class="mesh-blob mesh-2"></div>
                <div class="mesh-blob mesh-3"></div>
              </div>
              
              <!-- Background Effects -->
              <StarField v-if="animationEffect === 'starfield'" />
              <RainEffect v-if="animationEffect === 'rain'" />
              <AuroraEffect v-if="animationEffect === 'aurora'" />
              <FireflyEffect v-if="animationEffect === 'firefly'" />
              <MistEffect v-if="animationEffect === 'mist'" />

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
  background: transparent; /* 淇濇寔閫忔槑浠ユ樉绀哄簳灞傛槦绌?*/
  transition: grid-template-columns 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 1; /* 鍦ㄦ槦绌轰箣涓?*/
}

/* Desktop Collapsed State */
.main-layout.sidebar-collapsed {
  grid-template-columns: 0 1fr;
}

/* Global Collapse Toggle */
.global-collapse-toggle {
  position: fixed;
  top: 16px;
  left: 212px; /* 260px(渚ц竟鏍忓搴? - 32px(鎸夐挳瀹藉害) - 16px(鍙宠竟璺? */
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
  background: transparent; /* 鏀逛负閫忔槑浠ユ樉绀烘槦绌?*/
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

/* 瑙嗗浘鍒囨崲鍔ㄧ敾 - 绗﹀悎 frontend-design 楂樼骇鎰熻姹?*/
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
