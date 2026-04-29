import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

export type ThemeMode = 'light' | 'dark' | 'system';

export const useUIStore = defineStore('ui', () => {
  const theme = ref<ThemeMode>((localStorage.getItem('lingshu-theme') as ThemeMode) || 'light');
  const fontSize = ref(Number(localStorage.getItem('lingshu-font-size')) || 2); // 1: 小, 2: 标准, 3: 中, 4: 大, 5: 特大
  const conversationListWidth = ref(Number(localStorage.getItem('lingshu-sidebar-width')) || 280);

  // 监听变化并持久化
  watch(theme, (val) => localStorage.setItem('lingshu-theme', val));
  watch(fontSize, (val) => localStorage.setItem('lingshu-font-size', val.toString()));
  watch(conversationListWidth, (val) => localStorage.setItem('lingshu-sidebar-width', val.toString()));
  
  function toggleTheme() {
    const newMode = theme.value === 'light' ? 'dark' : 'light';
    setTheme(newMode);
  }

  function setTheme(mode: ThemeMode) {
    theme.value = mode;
    let actualMode = mode;
    
    if (mode === 'system') {
      actualMode = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    document.documentElement.setAttribute('data-theme', actualMode);
    if (actualMode === 'dark') {
      document.body.setAttribute('arco-theme', 'dark');
    } else {
      document.body.removeAttribute('arco-theme');
    }
  }

  function setFontSize(size: number) {
    fontSize.value = size;
    document.documentElement.style.setProperty('--font-scale', (0.8 + size * 0.1).toString());
  }

  function setConversationListWidth(width: number) {
    conversationListWidth.value = Math.min(400, Math.max(280, width));
  }

  return {
    theme,
    fontSize,
    conversationListWidth,
    toggleTheme,
    setTheme,
    setFontSize,
    setConversationListWidth,
  };
});
