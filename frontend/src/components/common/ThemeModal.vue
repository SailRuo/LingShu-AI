<script setup lang="ts">
import { NModal } from 'naive-ui'
import { useThemeStore } from '@/stores/themeStore'
import { Sun, Sparkles, Cpu, Check, Zap, Moon } from 'lucide-vue-next'
import { computed, ref } from 'vue'
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
const hoveredTheme = ref<string | null>(null)

// 从实际主题 CSS 变量动态获取颜色
function getThemePreviewColors(key: ThemeKey) {
  // 根据主题类型返回不同的颜色组合 - 使用实色而非透明度
  if (key === 'polarLight') {
    return {
      bg: '#e0f2f1', // 浅青绿色背景
      surface: '#ffffff',
      primary: '#0d9488', // 深青绿色
      primaryLight: '#99f6e4', // 浅青绿色用于气泡
      text: '#0f172a',
      outline: 'rgba(13, 148, 136, 0.3)',
      glow: 'rgba(13, 148, 136, 0.4)',
      bubbleAi: '#ccfbf1', // AI 气泡背景
      bubbleUser: '#14b8a6', // 用户气泡背景
      bubbleUserText: '#ffffff'
    }
  } else if (key === 'cyberPurple') {
    return {
      bg: '#1e1b4b', // 深紫背景
      surface: '#312e81', // 靛蓝表面
      primary: '#c084fc', // 亮紫色
      primaryLight: '#e9d5ff', // 浅紫色
      text: '#faf5ff',
      outline: 'rgba(192, 132, 252, 0.4)',
      glow: 'rgba(192, 132, 252, 0.6)',
      bubbleAi: 'rgba(192, 132, 252, 0.25)', // AI 气泡
      bubbleUser: '#a855f7', // 用户气泡 - 实色紫
      bubbleUserText: '#ffffff'
    }
  } else if (key === 'midnightBlue') {
    return {
      bg: '#0f172a', // 深蓝背景
      surface: '#1e293b', // 深蓝表面
      primary: '#38bdf8', // 天蓝色
      primaryLight: '#7dd3fc', // 浅蓝色
      text: '#f0f9ff',
      outline: 'rgba(56, 189, 248, 0.4)',
      glow: 'rgba(56, 189, 248, 0.5)',
      bubbleAi: 'rgba(56, 189, 248, 0.2)', // AI 气泡
      bubbleUser: '#0284c7', // 用户气泡 - 实色蓝
      bubbleUserText: '#ffffff'
    }
  } else {
    return {
      bg: '#022c22', // 深绿背景
      surface: '#064e3b', // 深绿表面
      primary: '#34d399', // 亮绿色
      primaryLight: '#6ee7b7', // 浅绿色
      text: '#ecfdf5',
      outline: 'rgba(52, 211, 153, 0.4)',
      glow: 'rgba(52, 211, 153, 0.6)',
      bubbleAi: 'rgba(52, 211, 153, 0.2)', // AI 气泡
      bubbleUser: '#10b981', // 用户气泡 - 实色绿
      bubbleUserText: '#ffffff'
    }
  }
}

type ThemeOption = {
  key: ThemeKey
  label: string
  icon: typeof Sun
  isDark: boolean
  description: string
}

const themes = computed<ThemeOption[]>(() => [
  {
    key: 'polarLight',
    label: '极地白',
    icon: Sun,
    isDark: false,
    description: '清新明亮 · 极简主义'
  },
  {
    key: 'cyberPurple',
    label: '星空',
    icon: Sparkles,
    isDark: true,
    description: '深邃宇宙 · 星辰大海'
  },
  {
    key: 'deepTechFuturistic',
    label: '深色·科技感',
    icon: Cpu,
    isDark: true,
    description: '深邃科技 · 未来主义'
  },
  {
    key: 'midnightBlue',
    label: '午夜蓝',
    icon: Moon,
    isDark: true,
    description: '深邃宁静 · 优雅蓝色'
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
    style="width: 680px"
  >
    <div class="theme-grid">
      <div
        v-for="theme in themes"
        :key="theme.key"
        class="theme-card"
        :class="{ 
          active: themeStore.current.key === theme.key,
          hovering: hoveredTheme === theme.key 
        }"
        @click="selectTheme(theme.key)"
        @mouseenter="hoveredTheme = theme.key"
        @mouseleave="hoveredTheme = null"
      >
        <div class="theme-preview" :style="{
          backgroundColor: getThemePreviewColors(theme.key).bg
        }">
          <!-- 背景光效 -->
          <div 
            v-if="theme.isDark" 
            class="preview-glow"
            :style="{
              background: `radial-gradient(circle at 50% 50%, ${getThemePreviewColors(theme.key).glow} 0%, transparent 70%)`
            }"
          ></div>
          
          <div class="preview-ui" :style="{
            backgroundColor: getThemePreviewColors(theme.key).surface,
            borderColor: getThemePreviewColors(theme.key).outline
          }">
            <div class="preview-header" :style="{
              borderColor: getThemePreviewColors(theme.key).outline
            }">
              <div class="preview-dots">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
              <div class="preview-title" :style="{
                color: getThemePreviewColors(theme.key).text
              }">灵枢 AI</div>
            </div>
            <div class="preview-body">
              <div 
                class="preview-bubble ai" 
                :style="{
                  backgroundColor: getThemePreviewColors(theme.key).bubbleAi,
                  borderColor: getThemePreviewColors(theme.key).outline,
                  color: getThemePreviewColors(theme.key).text
                }"
              >
                <span>你好，我是灵枢</span>
              </div>
              <div 
                class="preview-bubble user" 
                :style="{
                  backgroundColor: getThemePreviewColors(theme.key).bubbleUser,
                  borderColor: getThemePreviewColors(theme.key).primary,
                  color: getThemePreviewColors(theme.key).bubbleUserText
                }"
              >
                <span>今天天气怎么样？</span>
              </div>
            </div>
            <div 
              class="preview-primary-indicator"
              :style="{
                backgroundColor: getThemePreviewColors(theme.key).primary,
                boxShadow: themeStore.current.key === theme.key 
                  ? `0 0 20px ${getThemePreviewColors(theme.key).glow}` 
                  : hoveredTheme === theme.key
                    ? `0 0 12px ${getThemePreviewColors(theme.key).glow}`
                    : 'none'
              }"
            >
              <component :is="theme.icon" :size="18" :color="theme.isDark ? '#000' : '#fff'" />
            </div>
          </div>
        </div>
        
        <div class="theme-info">
          <div class="info-content">
            <component :is="theme.icon" :size="20" class="theme-icon" />
            <div class="info-text">
              <div class="theme-label">{{ theme.label }}</div>
              <div class="theme-description">{{ theme.description }}</div>
            </div>
          </div>
          <div v-if="themeStore.current.key === theme.key" class="active-badge">
            <Check :size="16" />
            <span>当前使用</span>
          </div>
          <div v-else-if="hoveredTheme === theme.key" class="hover-hint">
            <Zap :size="16" />
            <span>点击切换</span>
          </div>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
.theme-modal :deep(.n-card__content) {
  padding: 24px;
}

.theme-modal :deep(.n-card-header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-outline);
  margin-bottom: 8px;
  background: var(--color-glass-bg);
}

.theme-modal :deep(.n-card-header__title) {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--color-text), var(--color-primary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}

.theme-card {
  cursor: pointer;
  border-radius: 20px;
  overflow: hidden;
  border: 2px solid var(--color-outline);
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  background: var(--color-surface);
  position: relative;
}

/* 卡片边框渐变光效 */
.theme-card::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 20px;
  padding: 2px;
  background: linear-gradient(135deg, 
    var(--color-primary), 
    var(--color-accent), 
    var(--color-primary)
  );
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  opacity: 0;
  transition: opacity 0.4s ease;
}

.theme-card.hovering::before,
.theme-card.active::before {
  opacity: 1;
}

.theme-card:hover {
  transform: translateY(-8px) scale(1.02);
  border-color: var(--color-primary);
  box-shadow: 
    0 20px 40px var(--color-primary-dim),
    0 0 60px rgba(0, 0, 0, 0.3);
}

.theme-card.active {
  border-color: var(--color-primary);
  border-width: 3px;
  box-shadow: 
    0 12px 32px var(--color-primary-dim),
    0 0 40px rgba(0, 0, 0, 0.2);
}

.theme-preview {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  position: relative;
  overflow: hidden;
}

/* 深色主题背景光晕 */
.preview-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transition: opacity 0.4s ease;
  opacity: 0.6;
}

.preview-ui {
  width: 100%;
  max-width: 480px;
  height: 140px;
  background: var(--color-surface, rgba(255,255,255,0.9));
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  position: relative;
  box-shadow: 
    0 8px 24px rgba(0,0,0,0.15),
    0 0 40px rgba(0,0,0,0.1);
  backdrop-filter: blur(12px);
  border: 1px solid;
  /* 边框颜色通过内联样式动态绑定 */
  border-color: transparent;
  transition: all 0.4s ease;
}

.theme-card.hovering .preview-ui {
  box-shadow: 
    0 12px 32px rgba(0,0,0,0.2),
    0 0 60px rgba(0,0,0,0.15);
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid;
  /* 边框颜色通过内联样式动态绑定 */
  border-color: transparent;
  transition: border-color 0.3s ease;
}

.preview-dots {
  display: flex;
  gap: 5px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(0,0,0,0.2);
  transition: transform 0.3s ease;
}

.theme-card.hovering .dot {
  transform: scale(1.2);
}

.dot:nth-child(1) { background: #ff5f57; }
.dot:nth-child(2) { background: #febc2e; }
.dot:nth-child(3) { background: #28c840; }

.preview-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text, #333);
  flex: 1;
  letter-spacing: 0.03em;
}

.preview-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
}

.preview-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 12px;
  line-height: 1.5;
  transition: all 0.3s ease;
  border: 1px solid;
  /* 移除硬编码颜色，改用内联样式动态绑定 */
  background: transparent;
  border-color: transparent;
}

.theme-card.hovering .preview-bubble {
  transform: translateX(4px);
}

.preview-bubble.ai {
  align-self: flex-start;
  border-bottom-left-radius: 4px;
  /* 颜色通过内联样式动态绑定 */
}

.preview-bubble.user {
  align-self: flex-end;
  border-bottom-right-radius: 4px;
  /* 颜色通过内联样式动态绑定 */
}

.preview-primary-indicator {
  position: absolute;
  bottom: 16px;
  right: 16px;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  z-index: 10;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.theme-card.hovering .preview-primary-indicator {
  transform: scale(1.15) rotate(8deg);
}

.theme-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  background: var(--color-glass-bg);
  border-top: 1px solid var(--color-outline);
  backdrop-filter: blur(8px);
}

.info-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.theme-icon {
  color: var(--color-primary);
  flex-shrink: 0;
  filter: drop-shadow(0 0 8px var(--color-primary-dim));
}

.info-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.theme-label {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.theme-description {
  font-size: 12px;
  color: var(--color-text-dim);
  font-weight: 500;
}

.active-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  color: var(--color-text-inverse);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 4px 12px var(--color-primary-dim);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 4px 12px var(--color-primary-dim);
  }
  50% {
    box-shadow: 0 4px 20px var(--color-primary-dim), 0 0 24px var(--color-primary);
  }
}

.active-badge svg {
  width: 14px;
  height: 14px;
}

.hover-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: var(--color-primary-dim);
  color: var(--color-primary);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid var(--color-primary);
  animation: flash 1.5s infinite;
}

@keyframes flash {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.hover-hint svg {
  width: 14px;
  height: 14px;
  filter: drop-shadow(0 0 4px var(--color-primary));
}

/* 响应式布局 */
@media (max-width: 768px) {
  .theme-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .theme-modal :deep(.n-card__content) {
    padding: 16px;
  }
  
  .theme-preview {
    height: 160px;
  }
}
</style>
