<script setup lang="ts">
import { NModal } from 'naive-ui'
import { useThemeStore } from '@/stores/themeStore'
import { Sun, Sparkles, Cpu, Check } from 'lucide-vue-next'
import { computed } from 'vue'
import type { ThemeKey } from '@/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

const show = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

const themeStore = useThemeStore()

type ThemeOption = {
  key: ThemeKey
  label: string
  icon: typeof Sun
  isDark: boolean
  previewColors: {
    bg: string
    surface: string
    primary: string
    text: string
  }
}

const themes = computed<ThemeOption[]>(() => [
  {
    key: 'polarLight',
    label: '极地白',
    icon: Sun,
    isDark: false,
    previewColors: {
      bg: '#f8fafc',
      surface: '#ffffff',
      primary: '#0f766e',
      text: '#0f172a'
    }
  },
  {
    key: 'cyberPurple',
    label: '赛博紫',
    icon: Sparkles,
    isDark: true,
    previewColors: {
      bg: '#0c0a1a',
      surface: '#1e1432',
      primary: '#c084fc',
      text: '#faf5ff'
    }
  },
  {
    key: 'deepTechFuturistic',
    label: '深色·科技感',
    icon: Cpu,
    isDark: true,
    previewColors: {
      bg: '#050505',
      surface: '#0a0f14',
      primary: '#00ff88',
      text: '#e0e0e0'
    }
  }
])

function selectTheme(key: ThemeKey) {
  themeStore.setTheme(key)
  emit('update:open', false)
}
</script>

<template>
  <n-modal
    v-model:show="show"
    preset="card"
    title="选择主题"
    class="theme-modal"
    :closable="true"
    style="width: 420px"
  >
    <div class="theme-grid">
      <div
        v-for="theme in themes"
        :key="theme.key"
        class="theme-card"
        :class="{ active: themeStore.current.key === theme.key }"
        @click="selectTheme(theme.key)"
      >
        <div class="theme-preview" :style="{
          backgroundColor: theme.previewColors.bg
        }">
          <div class="preview-surface" :style="{
            backgroundColor: theme.previewColors.surface,
            border: `1px solid ${themeStore.current.key === theme.key ? theme.previewColors.primary : 'rgba(0,0,0,0.1)'}`
          }">
            <div class="preview-text" :style="{ color: theme.previewColors.text }">
              <span style="font-size: 12px">灵枢 AI</span>
            </div>
            <div class="preview-primary" :style="{
              backgroundColor: theme.previewColors.primary,
              boxShadow: themeStore.current.key === theme.key ? `0 0 12px ${theme.previewColors.primary}` : 'none'
            }">
              <component :is="theme.icon" :size="16" :color="theme.isDark ? '#000' : '#fff'" />
            </div>
          </div>
        </div>
        
        <div class="theme-info">
          <component :is="theme.icon" :size="18" class="theme-icon" />
          <span class="theme-label">{{ theme.label }}</span>
          <div v-if="themeStore.current.key === theme.key" class="active-indicator">
            <Check :size="16" />
          </div>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
.theme-modal :deep(.n-card__content) {
  padding: 16px;
}

.theme-grid {
  display: grid;
  gap: 16px;
}

.theme-card {
  cursor: pointer;
  border-radius: 12px;
  overflow: hidden;
  border: 2px solid var(--color-outline);
  transition: all 0.3s ease;
}

.theme-card:hover {
  transform: translateY(-2px);
  border-color: var(--color-primary);
}

.theme-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 4px 16px var(--color-primary-dim);
}

.theme-preview {
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.preview-surface {
  width: 100%;
  height: 64px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  transition: all 0.3s ease;
}

.preview-text {
  font-weight: 600;
  letter-spacing: 0.02em;
}

.preview-primary {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.theme-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--color-surface);
  border-top: 1px solid var(--color-outline);
}

.theme-icon {
  color: var(--color-text-dim);
}

.theme-label {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.active-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: var(--color-primary);
  border-radius: 50%;
  color: var(--color-text-inverse);
}
</style>
