<script setup lang="ts">
import ChatHeader from '../chat/ChatHeader.vue';
import MessageList from '../chat/MessageList.vue';
import InputArea from '../chat/InputArea.vue';
import { useChatStore } from '../../stores/chat';

const chatStore = useChatStore();

async function handleSend(content: string) {
  await chatStore.sendMessage(content);
}
</script>

<template>
  <main class="chat-window">
    <template v-if="chatStore.currentConversation">
      <ChatHeader :conversation="chatStore.currentConversation" />
      <MessageList :messages="chatStore.currentMessages" />
      <InputArea @send="handleSend" />
    </template>
    <div v-else class="empty-chat">
      <svg class="empty-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
        <path d="M896 128H128c-35.3 0-64 28.7-64 64v512c0 35.3 28.7 64 64 64h192l192 128 192-128h192c35.3 0 64-28.7 64-64V192c0-35.3-28.7-64-64-64z" fill="#E8E8E8"/>
        <path d="M320 384h384v64H320zM320 512h256v64H320z" fill="#D9D9D9"/>
      </svg>
      <p class="empty-text">选择一个会话开始聊天</p>
    </div>
  </main>
</template>

<style scoped>
.chat-window {
  flex: 1;
  min-width: 400px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-chat-window);
  overflow: hidden;
}

.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--text-tertiary);
}

.empty-icon {
  width: 120px;
  height: 120px;
  opacity: 0.4;
}

.empty-text {
  font-size: var(--font-size-md);
}
</style>
