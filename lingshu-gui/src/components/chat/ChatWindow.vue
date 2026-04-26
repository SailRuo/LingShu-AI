<script setup lang="ts">
import ChatHeader from './ChatHeader.vue';
import MessageList from './MessageList.vue';
import InputArea from './InputArea.vue';
import { useChatStore } from '../../stores/chat';
import { useTts } from '../../composables/useTts';
import { watch } from 'vue';

const chatStore = useChatStore();
const { autoTtsEnabled, speak, appendText } = useTts();

async function handleSend(content: string, attachments: any[]) {
  await chatStore.sendMessage(content, attachments);
}

// 监听消息变化，实现自动语音合成
watch(
  () => chatStore.currentMessages,
  (newMessages, oldMessages) => {
    if (!autoTtsEnabled.value) return;
    
    // 找到新增加的消息
    const oldLength = oldMessages?.length || 0;
    if (newMessages.length > oldLength) {
      const latestMessage = newMessages[newMessages.length - 1];
      
      // 只处理 AI 的消息，且是文本消息
      if (!latestMessage.isSelf && latestMessage.type === 'text') {
        const content = latestMessage.content;
        if (content && content.trim().length > 0) {
          speak(content, latestMessage.id);
        }
      }
    } else if (newMessages.length > 0) {
      // 处理流式更新的消息
      const latestMessage = newMessages[newMessages.length - 1];
      if (!latestMessage.isSelf && latestMessage.type === 'text') {
        const oldMessage = oldMessages?.find(m => m.id === latestMessage.id);
        if (oldMessage && oldMessage.type === 'text') {
          const newContent = latestMessage.content;
          const oldContent = oldMessage.content;
          const isFinished = latestMessage.status === 'sent';
          
          if (newContent !== oldContent || (isFinished && oldMessage.status === 'sending')) {
             // 使用 appendText 进行增量处理，它会根据标点符号自动分段并追加播放队列
             // 如果 isFinished 为 true，则会强制处理最后一段文本
             appendText(newContent, latestMessage.id, isFinished);
          }
        }
      }
    }
  },
  { deep: true }
);
</script>

<template>
  <main class="chat-window">
    <template v-if="chatStore.currentConversation">
      <ChatHeader :conversation="chatStore.currentConversation" />
      <MessageList :messages="chatStore.currentMessages" />
      <InputArea @send="handleSend" />
    </template>
    <div v-else class="empty-chat" id="chat-empty-state">
      <div class="empty-content">
        <svg class="empty-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
          <path d="M896 128H128c-35.3 0-64 28.7-64 64v512c0 35.3 28.7 64 64 64h192l192 128 192-128h192c35.3 0 64-28.7 64-64V192c0-35.3-28.7-64-64-64z" fill="currentColor"/>
          <path d="M320 384h384v64H320zM320 512h256v64H320z" fill="var(--bg-chat-window)"/>
        </svg>
        <p class="empty-text">选择一个会话开始聊天</p>
      </div>
    </div>
  </main>
</template>

<style scoped>
.chat-window {
  flex: 1;
  min-width: 0; /* 允许在 Flex 容器中正常收缩 */
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-chat-window);
  overflow: hidden;
  position: relative;
}

.empty-chat {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-4); /* 使用 Arco Design 变量或 CSS 变量 */
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  opacity: 0.6;
}

.empty-icon {
  width: 80px;
  height: 80px;
  color: var(--color-fill-3);
}

.empty-text {
  font-size: 14px;
  color: var(--color-text-3);
}
</style>
