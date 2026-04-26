<script setup lang="ts">
import { ref, nextTick, watch } from 'vue';
import type { AnyMessage } from '../../types/message';
import { useTts } from '../../composables/useTts';

const props = defineProps<{
  messages: AnyMessage[];
}>();

const listRef = ref<HTMLElement | null>(null);
let resizeObserver: ResizeObserver | null = null;
const { speak } = useTts();

watch(
  () => props.messages,
  async () => {
    await nextTick();
    scrollToBottom();
  },
  { deep: true }
);

import { onMounted, onUnmounted } from 'vue';

onMounted(() => {
  if (listRef.value) {
    resizeObserver = new ResizeObserver(() => {
      scrollToBottom();
    });
    resizeObserver.observe(listRef.value);
  }
});

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
});

function scrollToBottom() {
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight;
  }
}

function shouldShowTime(msg: AnyMessage, index: number): boolean {
  if (index === 0) return true;
  const prevMsg = props.messages[index - 1];
  const diff = msg.timestamp.getTime() - prevMsg.timestamp.getTime();
  return diff > 5 * 60 * 1000; // 5 分钟
}

function formatTime(date: Date): string {
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  return isToday 
    ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
    : `${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}`;
}

function handleBubbleDoubleClick(msg: AnyMessage) {
  if (!msg.isSelf && msg.type === 'text') {
    const content = (msg as any).content;
    if (content && content.trim().length > 0) {
      speak(content, msg.id);
    }
  }
}
</script>

<template>
  <div ref="listRef" class="message-list">
    <div v-if="messages.length === 0" class="empty-messages">
      <p>暂无消息记录</p>
    </div>
    <div v-else class="messages-container">
      <div v-for="(msg, index) in messages" :key="msg.id" class="message-item">
        <!-- 仅当时间间隔超过 5 分钟或第一条消息时显示时间 -->
        <div v-if="shouldShowTime(msg, index)" class="time-divider">
          {{ formatTime(msg.timestamp) }}
        </div>
        
        <article
          class="message-row"
          :class="{ self: msg.isSelf, other: !msg.isSelf }"
        >
          <!-- 非本人消息显示头像 -->
          <img 
            v-if="!msg.isSelf" 
            class="message-avatar" 
            :src="msg.senderAvatar || '/bot.png'" 
            :alt="msg.senderName"
          />
          
          <div class="bubble-wrapper">
            <!-- 非本人消息显示名称 -->
            <div v-if="!msg.isSelf" class="sender-name">{{ msg.senderName }}</div>
            
            <div 
              class="bubble" 
              :class="{ self: msg.isSelf, other: !msg.isSelf }"
              @dblclick="handleBubbleDoubleClick(msg)"
            >
              <span v-if="msg.type === 'text'" class="text-content">{{ (msg as any).content }}</span>
              <span v-else class="unsupported">[{{ msg.type }} 消息]</span>
            </div>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  min-height: 0; /* 极其关键：允许 Flex 项目缩小到 0，防止遮挡 */
  overflow-y: auto;
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-chat-window);
  /* 使用平滑滚动 */
  scroll-behavior: smooth;
}

/* 自定义滚动条整体样式 */
.message-list::-webkit-scrollbar {
  width: 6px; /* 宽度调细 */
}

.message-list::-webkit-scrollbar-track {
  background: transparent;
}

.message-list::-webkit-scrollbar-thumb {
  background: transparent; /* 默认隐藏 */
  border-radius: 10px;
  transition: background 0.3s;
}

/* 鼠标移入消息区域时显示滚动条 */
.message-list:hover::-webkit-scrollbar-thumb {
  background: var(--bg-selected); /* 使用主题背景选中色，自动适配亮/深模式 */
}

/* 鼠标直接悬停在滑块上时加深一点 */
.message-list::-webkit-scrollbar-thumb:hover {
  background: var(--text-placeholder);
}

.messages-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-item {
  display: flex;
  flex-direction: column;
  padding: 0 20px;
}

.time-divider {
  align-self: center;
  font-size: var(--font-size-xs);
  color: var(--text-placeholder);
  margin: 30px 0 10px;
}

.message-row {
  display: flex;
  width: 100%;
  margin: 8px 0;
}

.message-row.self {
  justify-content: flex-end;
}

.message-row.other {
  justify-content: flex-start;
  gap: 8px;
}

.message-avatar {
  width: 38px;
  height: 38px;
  border-radius: 6px;
  flex-shrink: 0;
  object-fit: cover;
  margin-top: 2px;
}

.bubble-wrapper {
  max-width: 85%;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sender-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 12px;
  margin-bottom: 2px;
}

.sender-name {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-bottom: 2px;
  margin-top: 2px;
}

.ai-tag {
  background-color: var(--bg-selected);
  color: var(--text-tertiary);
  font-size: 10px;
  padding: 0px 4px;
  border-radius: 2px;
  font-weight: bold;
  line-height: 1.4;
  text-transform: uppercase;
}

.bubble {
  padding: 12px 16px;
  border-radius: 4px;
  font-size: var(--font-size-md);
  line-height: 1.6;
  word-break: break-word;
  position: relative;
}

.bubble.other {
  background-color: var(--bg-message-other);
  color: var(--text-primary);
  border-radius: 8px;
  margin-left: 0;
  box-shadow: none;
}

.bubble.other::after {
  content: "";
  position: absolute;
  left: -8px;
  top: 12px;
  width: 0;
  height: 0;
  border-right: 10px solid var(--bg-message-other);
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
}

.bubble.self {
  background-color: var(--bg-message-self);
  color: var(--text-message-self);
  margin-right: 12px;
  border-radius: 8px;
}

.bubble.self::after {
  content: "";
  position: absolute;
  right: -8px;
  top: 12px;
  width: 0;
  height: 0;
  border-left: 10px solid var(--bg-message-self);
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
}

.text-content {
  white-space: pre-wrap;
}

.unsupported {
  color: var(--text-tertiary);
  font-style: italic;
}

.empty-messages {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: var(--font-size-md);
}
</style>

