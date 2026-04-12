<script setup lang="ts">
import { ref } from 'vue'
import ChatView from '@/components/chat/ChatView.vue'
import StreamPanel from '@/components/chat/StreamPanel.vue'

const isStreamPanelOpen = ref(false)

function togglePanel() {
  isStreamPanelOpen.value = !isStreamPanelOpen.value
}
</script>

<template>
  <div class="resonance-layout">
    <div class="chat-container">
      <ChatView @toggle-panel="togglePanel" />
    </div>
    
    <Transition name="slide">
      <div class="side-panel" v-if="isStreamPanelOpen">
        <StreamPanel @close="isStreamPanelOpen = false" />
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.resonance-layout {
  height: 100%;
  display: flex;
  position: relative;
  overflow: hidden;
}

.chat-container {
  flex: 1;
  min-width: 0;
  height: 100%;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.side-panel {
  width: 400px;
  height: 100%;
  flex-shrink: 0;
  border-left: 1px solid var(--color-outline);
  background: rgba(var(--color-surface-rgb), 0.8);
  backdrop-filter: blur(20px);
  z-index: 10;
}

@media (max-width: 768px) {
  .side-panel {
    position: absolute;
    right: 0;
    top: 0;
    width: 100%;
    max-width: 400px;
    box-shadow: -4px 0 24px rgba(0, 0, 0, 0.2);
  }
}

/* 侧边栏入场动画 */
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
