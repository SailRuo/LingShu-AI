<script setup lang="ts">
import { getCurrentWindow } from '@tauri-apps/api/window';
import { ref, onMounted } from 'vue';

const appWindow = getCurrentWindow();
const isMaximized = ref(false);

const checkMaximized = async () => {
  isMaximized.value = await appWindow.isMaximized();
};

onMounted(async () => {
  checkMaximized();
  await appWindow.onResized(() => checkMaximized());
});

const handleMouseDown = (e: MouseEvent) => {
  if (e.buttons === 1) {
    appWindow.startDragging();
  }
};

const handleDoubleClick = () => {
  appWindow.toggleMaximize();
};

const minimize = () => appWindow.minimize();
const toggleMaximize = async () => {
  await appWindow.toggleMaximize();
  checkMaximized();
};
const close = () => appWindow.close();
</script>

<template>
  <div 
    class="custom-titlebar" 
    data-tauri-drag-region
    @mousedown="handleMouseDown" 
    @dblclick="handleDoubleClick"
  >
    <div class="drag-spacer" data-tauri-drag-region></div>

    <div class="window-controls" @mousedown.stop @dblclick.stop>
      <div class="win-btn" title="最小化" @click="minimize">
        <svg width="10" height="10" viewBox="0 0 10 10">
          <path d="M0 5H10" stroke="currentColor" stroke-width="1"/>
        </svg>
      </div>
      
      <div class="win-btn" :title="isMaximized ? '还原' : '最大化'" @click="toggleMaximize">
        <svg v-if="!isMaximized" width="10" height="10" viewBox="0 0 10 10">
          <path d="M1 1H9V9H1V1Z" stroke="currentColor" fill="none" stroke-width="1"/>
        </svg>
        <svg v-else width="10" height="10" viewBox="0 0 10 10">
          <path d="M3 1H9V7" stroke="currentColor" fill="none" stroke-width="1"/>
          <path d="M1 3H7V9H1V3Z" stroke="currentColor" fill="none" stroke-width="1"/>
        </svg>
      </div>
      
      <div class="win-btn close-btn" title="关闭" @click="close">
        <svg width="10" height="10" viewBox="0 0 10 10">
          <path d="M1 1L9 9M9 1L1 9" stroke="currentColor" stroke-width="1"/>
        </svg>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-titlebar {
  height: 32px;
  background-color: var(--bg-sidebar); /* 恢复背景色，与左侧对齐或根据需要选择 */
  display: flex;
  align-items: center;
  justify-content: flex-end;
  user-select: none;
  cursor: default;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
}

.drag-spacer {
  flex: 1;
  height: 100%;
}

.window-controls {
  display: flex;
  height: 100%;
}

.win-btn {
  width: 46px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555;
  transition: background-color 0.1s ease;
}

.win-btn:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

.close-btn:hover {
  background-color: #e81123 !important;
  color: white !important;
}

[data-theme="dark"] .custom-titlebar {
  background-color: #1e1e1e;
}

[data-theme="dark"] .win-btn {
  color: #ccc;
}
</style>
