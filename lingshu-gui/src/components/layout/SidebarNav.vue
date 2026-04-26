<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router';
import { computed } from 'vue';
import { getCurrentWindow } from '@tauri-apps/api/window';
const router = useRouter();
const route = useRoute();

const mainNavItems = [
  { id: 'chat', icon: 'mode_comment', label: '对话', path: '/' },
];

const bottomNavItems = [
  { id: 'settings', icon: 'settings', label: '设置', path: '/settings' },
];

const activeNavId = computed(() => {
  const currentPath = route.path;
  const matchedItem = [...mainNavItems, ...bottomNavItems].find(item => item.path === currentPath);
  return matchedItem ? matchedItem.id : 'chat';
});

function handleSelect(item: any) {
  router.push(item.path);
}
</script>

<template>
  <aside class="sidebar-nav">
    <!-- Top: User Avatar (Also a drag region) -->
    <div 
      class="user-area" 
      data-tauri-drag-region
      @mousedown="() => getCurrentWindow().startDragging()"
    >
      <div class="user-avatar" title="个人设置">
        <img src="/linger.png" alt="avatar" />
      </div>
    </div>

    <!-- Middle: Main Navigation -->
    <nav class="nav-list main-nav">
      <div
        v-for="item in mainNavItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeNavId === item.id }"
        :title="item.label"
        @click="handleSelect(item)"
      >
        <span class="material-symbols-outlined">{{ item.icon }}</span>
      </div>
    </nav>

    <!-- Bottom: Secondary Navigation -->
    <nav class="nav-list bottom-nav">
      <div
        v-for="item in bottomNavItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeNavId === item.id }"
        :title="item.label"
        @click="handleSelect(item)"
      >
        <span class="material-symbols-outlined">{{ item.icon }}</span>
      </div>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar-nav {
  width: var(--sidebar-width);
  height: 100%;
  background-color: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 15px 0;
  flex-shrink: 0;
  z-index: 1000;
}

.user-area {
  margin-bottom: 18px;
  display: flex;
  justify-content: center;
  width: 100%;
}

.user-avatar {
  width: 38px;
  height: 38px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--transition-fast);
}

.user-avatar:hover {
  transform: scale(1.05);
}

.user-avatar:active {
  transform: scale(0.95);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  align-items: center;
}

.main-nav {
  flex: 1;
}

.nav-item {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  color: var(--text-secondary);
}

.nav-item:active {
  transform: scale(0.92);
}

.material-symbols-outlined {
  font-size: 26px;
  font-variation-settings:
    'FILL' 0,
    'wght' 300,
    'GRAD' 0,
    'opsz' 24;
  transition: font-variation-settings 0.2s;
}

.nav-item:hover {
  color: var(--text-primary);
}

.nav-item.active {
  color: var(--color-primary);
}

.nav-item.active .material-symbols-outlined {
  font-variation-settings:
    'FILL' 1,
    'wght' 300,
    'GRAD' 0,
    'opsz' 24;
}

.bottom-nav {
  margin-top: auto;
  padding-bottom: 10px;
}
</style>


