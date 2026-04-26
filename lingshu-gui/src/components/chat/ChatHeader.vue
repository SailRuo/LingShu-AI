<script setup lang="ts">
import type { Conversation } from '../../types/conversation';
import IconSettings from '@arco-design/web-vue/es/icon/icon-settings';

defineProps<{
  conversation: Conversation | null;
}>();
</script>

<template>
  <header v-if="conversation" class="chat-header">
    <div class="header-left">
      <span class="name">{{ conversation.name }}</span>
      <span v-if="conversation.name === '灵枢'" class="ai-badge">AI</span>
    </div>
    <div class="header-right">
      <button class="action-btn" title="设置">
        <IconSettings :size="18" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.chat-header {
  height: 60px;
  background-color: #f5f5f5;
  border-bottom: 1px solid #e5e5e5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name {
  font-size: 18px;
  font-weight: 500;
  color: #000;
}

.ai-badge {
  background-color: #e2e2e2;
  color: #999;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 2px;
  font-weight: bold;
}

.header-right {
  display: flex;
  align-items: center;
}

.action-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #515151;
  transition: all var(--transition-fast);
  background: none;
  border: none;
  cursor: pointer;
}

.action-btn:hover {
  color: #000;
}
</style>
