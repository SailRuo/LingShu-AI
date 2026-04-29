import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { THEMES, cyberPurple, buildNaiveOverrides } from '@/theme/themes'
import type { ThemeConfig, ThemeKey } from '@/types'
import { useLocalStorage } from '@vueuse/core'

const STORAGE_KEY = 'lingshu-theme'
const ANIMATION_KEY = 'lingshu-animation-effect'

export const useThemeStore = defineStore('theme', () => {
  // 主题持久化
  const themeKey = useLocalStorage<ThemeKey>(STORAGE_KEY, 'cyberPurple')
  
  const current = ref<ThemeConfig>(THEMES[themeKey.value] || cyberPurple)
  const availableThemes = Object.values(THEMES)

  // 动画特效持久化
  const animationEffect = useLocalStorage(ANIMATION_KEY, 'off')

  const naiveOverrides = computed(() => buildNaiveOverrides(current.value))

  function setTheme(key: ThemeKey) {
    const theme = THEMES[key]
    if (theme) {
      current.value = theme
      themeKey.value = key
    }
  }

  function applyCSS(theme: ThemeConfig) {
    if (typeof window === 'undefined' || !window.document) return
    
    const root = document.documentElement
    Object.entries(theme.cssVars).forEach(([key, value]) => {
      root.style.setProperty(key, value)
    })
    
    if (theme.isDark) {
      root.classList.add('dark')
      root.classList.remove('light')
    } else {
      root.classList.add('light')
      root.classList.remove('dark')
    }
  }

  // 监听主题变化并应用
  watch(current, (theme) => {
    applyCSS(theme)
  }, { immediate: true })

  return {
    current,
    availableThemes,
    naiveOverrides,
    animationEffect,
    setTheme
  }
})
