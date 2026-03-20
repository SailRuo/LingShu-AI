import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { THEMES, abyssDark, buildNaiveOverrides } from '@/theme/themes'
import type { ThemeConfig, ThemeKey } from '@/types'

const STORAGE_KEY = 'lingshu-theme'

export const useThemeStore = defineStore('theme', () => {
  const current = ref<ThemeConfig>(abyssDark)
  const availableThemes = Object.values(THEMES)

  const naiveOverrides = computed(() => buildNaiveOverrides(current.value))

  function setTheme(key: ThemeKey) {
    const theme = THEMES[key]
    if (theme) {
      current.value = theme
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

  // 初始化主题
  function initTheme() {
    const saved = localStorage.getItem(STORAGE_KEY) as ThemeKey | null
    if (saved && THEMES[saved]) {
      current.value = THEMES[saved]
    }
    applyCSS(current.value)
  }

  // 监听主题变化
  watch(current, (theme) => {
    localStorage.setItem(STORAGE_KEY, theme.key)
    applyCSS(theme)
  }, { immediate: false })

  return {
    current,
    availableThemes,
    naiveOverrides,
    setTheme,
    initTheme
  }
})
