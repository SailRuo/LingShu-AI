import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

export type ThemeMode = 'light' | 'dark' | 'system';

export const useUIStore = defineStore('ui', () => {
  const theme = ref<ThemeMode>('light');
  const fontSize = ref(2); // 1: 小, 2: 标准, 3: 中, 4: 大, 5: 特大
  const conversationListWidth = ref(280);

  // 初始化时从本地存储或系统偏好加载（可选，此处暂简实现）
  
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
