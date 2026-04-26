import { defineStore } from 'pinia';
import { ref } from 'vue';

export type ThemeMode = 'light' | 'dark';

export const useUIStore = defineStore('ui', () => {
  const theme = ref<ThemeMode>('light');
  const conversationListWidth = ref(280);

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', theme.value);
  }

  function setTheme(mode: ThemeMode) {
    theme.value = mode;
    document.documentElement.setAttribute('data-theme', mode);
  }

  function setConversationListWidth(width: number) {
    conversationListWidth.value = Math.min(
      400,
      Math.max(280, width)
    );
  }

  return {
    theme,
    conversationListWidth,
    toggleTheme,
    setTheme,
    setConversationListWidth,
  };
});
