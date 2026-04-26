<script setup lang="ts">
import { ref, nextTick, watch } from 'vue';
import type { AnyMessage } from '../../types/message';

const props = defineProps<{
  messages: AnyMessage[];
}>();

const listRef = ref<HTMLElement | null>(null);

watch(
  () => props.messages.length,
  async () => {
    await nextTick();
    scrollToBottom();
  }
);

function scrollToBottom() {
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight;
  }
}
</script>

<template>
  <div ref="listRef" class="message-list">
    <div v-if="messages.length === 0" class="empty-messages">
      <p>暂无消息记录</p>
    </div>
    <div v-else class="messages-container">
      <article
        v-for="(msg, index) in messages"
        :key="msg.id"
        class="message-row"
        :class="{ self: msg.isSelf, other: !msg.isSelf }"
      >
        <div class="bubble-wrapper">
          <div class="bubble" :class="{ self: msg.isSelf, other: !msg.isSelf }">
            <span v-if="msg.type === 'text'" class="text-content">{{ (msg as any).content }}</span>
            <span v-else class="unsupported">[{{ msg.type }} 消息]</span>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script lang="ts">
function formatTime(date: Date): string {
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });
}
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
}

.empty-messages {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary);
  font-size: var(--font-size-md);
}

.messages-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-row {
  display: flex;
  gap: 10px;
  max-width: 100%;
}

.message-row.self {
  justify-content: flex-end;
}

.message-row.other {
  justify-content: flex-start;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  object-fit: cover;
  margin-top: 4px;
}

.bubble-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 60%;
}

.message-row.self .bubble-wrapper {
  align-items: flex-end;
}

.message-row.other .bubble-wrapper {
  align-items: flex-start;
}

.sender-name {
  font-size: var(--font-size-base);
  color: var(--text-link);
  margin-bottom: 4px;
  cursor: pointer;
}

.bubble {
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  position: relative;
}

.bubble.self {
  background-color: #95ec69;
  color: #000;
}

.bubble.self::after {
  content: '';
  position: absolute;
  right: -5px;
  top: 12px;
  width: 0;
  height: 0;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
  border-left: 6px solid #95ec69;
}

.bubble.other {
  background-color: #f5f5f5;
  color: #000;
  border: none;
}

.bubble.other::after {
  content: '';
  position: absolute;
  left: -5px;
  top: 12px;
  width: 0;
  height: 0;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
  border-right: 6px solid #f5f5f5;
}

.text-content {
  color: #000000;
}

.unsupported {
  color: var(--text-tertiary);
  font-style: italic;
}

.message-time {
  font-size: var(--font-size-xs);
  color: var(--text-placeholder);
  margin-top: 4px;
  padding: 0 4px;
}
</style>
