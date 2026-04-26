<script setup lang="ts">
import SidebarNav from './SidebarNav.vue';
import TitleBar from './TitleBar.vue';
</script>

<template>
  <div class="main-layout">
    <SidebarNav />
    
    <div class="main-container">
      <!-- 重新放回头部的固定流 -->
      <TitleBar />
      <div class="content-area">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column; /* 恢复垂直排列 */
  height: 100%;
  overflow: hidden;
  background-color: var(--bg-chat-window);
}

.content-area {
  flex: 1;
  width: 100%;
  overflow: hidden;
  position: relative;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
