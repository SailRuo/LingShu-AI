<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router';
import { computed } from 'vue';
import { getCurrentWindow } from '@tauri-apps/api/window';
import IconMessage from '@arco-design/web-vue/es/icon/icon-message';
import IconUser from '@arco-design/web-vue/es/icon/icon-user';
import IconApps from '@arco-design/web-vue/es/icon/icon-apps';
import IconCompass from '@arco-design/web-vue/es/icon/icon-compass';
import IconMobile from '@arco-design/web-vue/es/icon/icon-mobile';
import IconMenu from '@arco-design/web-vue/es/icon/icon-menu';
import IconStar from '@arco-design/web-vue/es/icon/icon-star';

const router = useRouter();
const route = useRoute();

const mainNavItems = [
  { id: 'chat', icon: IconMessage, label: '对话', path: '/' },
  { id: 'contacts', icon: IconUser, label: '通讯录', path: '/contacts' },
  { id: 'folder', icon: IconApps, label: '文件夹', path: '/folder' },
  { id: 'starred', icon: IconStar, label: '收藏', path: '/starred' },
  { id: 'discover', icon: IconCompass, label: '发现', path: '/discover' },
];

const bottomNavItems = [
  { id: 'mobile', icon: IconMobile, label: '手机', path: '/mobile' },
  { id: 'settings', icon: IconMenu, label: '设置', path: '/settings' },
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
        <img src="/bot.png" alt="avatar" />
      </div>
    </div>

    <!-- Middle: Main Navigation -->
    <nav class="nav-list main-nav">
      <button
        v-for="item in mainNavItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeNavId === item.id }"
        :title="item.label"
        @click="handleSelect(item)"
      >
        <component :is="item.icon" :size="22" style="width: 22px; height: 22px;" />
      </button>
    </nav>

    <!-- Bottom: Secondary Navigation -->
    <nav class="nav-list bottom-nav">
      <button
        v-for="item in bottomNavItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeNavId === item.id }"
        :title="item.label"
        @click="handleSelect(item)"
      >
        <component :is="item.icon" :size="22" style="width: 22px; height: 22px;" />
      </button>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar-nav {
  width: var(--sidebar-width);
  height: 100%;
  background-color: #f2f2f2;
  border-right: 1px solid #e5e5e5;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 15px 0;
  flex-shrink: 0;
}

.user-area {
  margin-bottom: 20px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
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

.bottom-nav {
  margin-top: auto;
  gap: 15px;
}

.nav-item {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: all 0.2s;
  color: #515151;
}

.nav-item:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

.nav-item.active {
  color: #07c160;
}

/* Specific style for active filled icons if needed, 
   but Arco icons usually handle this via color */
</style>
