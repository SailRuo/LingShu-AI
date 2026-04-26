<script setup lang="ts">
import ChatHeader from './ChatHeader.vue';
import MessageList from './MessageList.vue';
import InputArea from './InputArea.vue';
import { useChatStore } from '../../stores/chat';
import { useTts } from '../../composables/useTts';
import { watch, ref } from 'vue';

const chatStore = useChatStore();
const { autoTtsEnabled, speak, appendText } = useTts();

// 记录上次处理的消息 ID 和内容，用于检测流式更新
const lastProcessedMessageId = ref<string | null>(null);
const lastProcessedContent = ref<string>('');

// 切换会话时重置处理记录，防止误触发
watch(
  () => chatStore.currentConversationId,
  () => {
    console.log('[TTS Debug] Conversation changed, resetting processed records');
    lastProcessedMessageId.value = null;
    lastProcessedContent.value = '';
  }
);

async function handleSend(content: string, attachments: any[]) {
  await chatStore.sendMessage(content, attachments);
}

// 监听消息变化，实现自动语音合成
watch(
  () => chatStore.currentMessages,
  (newMessages) => {
    if (!autoTtsEnabled.value) return;
    if (newMessages.length === 0) return;
    
    // 获取最后一条 AI 消息
    const latestMessage = newMessages[newMessages.length - 1];
    
    // 只处理 AI 的消息，且是文本消息
    if (latestMessage.isSelf || latestMessage.type !== 'text') return;
    
    const content = latestMessage.content || '';
    const messageId = latestMessage.id;
    const isFinished = latestMessage.status === 'sent';
    
    // 检查是否是新的消息 ID
    if (messageId !== lastProcessedMessageId.value) {
      console.log('[TTS Debug] New message ID detected:', messageId, 'status:', latestMessage.status);
      
      // 关键逻辑：如果新消息的状态已经是 'sent'，说明是加载的历史记录，不触发 TTS
      if (latestMessage.status === 'sent') {
        console.log('[TTS Debug] History message detected, skipping TTS');
        lastProcessedMessageId.value = messageId;
        lastProcessedContent.value = content;
        return;
      }
      
      // 只有状态为 'sending' 的新消息才触发 speak
      lastProcessedMessageId.value = messageId;
      lastProcessedContent.value = content;
      if (content.trim().length > 0) {
        console.log('[TTS Debug] New real-time message, calling speak');
        speak(content, messageId);
      }
      return;
    }
    
    // 同一条消息的内容更新（流式输出）
    if (content !== lastProcessedContent.value) {
      console.log('[TTS Debug] Content updated, calling appendText');
      lastProcessedContent.value = content;
      appendText(content, messageId, isFinished);
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
