<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useChatStore } from '../../stores/chat';
import { useUIStore } from '../../stores/ui';
import IconSearch from '@arco-design/web-vue/es/icon/icon-search';
import IconPlus from '@arco-design/web-vue/es/icon/icon-plus';
import IconMenu from '@arco-design/web-vue/es/icon/icon-menu';
import IconUp from '@arco-design/web-vue/es/icon/icon-up';

const chatStore = useChatStore();
const uiStore = useUIStore();

const isResizing = ref(false);

onMounted(() => {
  console.log('ConversationList mounted, total conversations:', chatStore.conversations.length);
  chatStore.loadConversations();
});

function handleSearch(e: Event) {
  const target = e.target as HTMLInputElement;
  chatStore.setSearchQuery(target.value);
}

function startResize(e: MouseEvent) {
  isResizing.value = true;
  document.addEventListener('mousemove', handleResize);
  document.addEventListener('mouseup', stopResize);
  e.preventDefault();
}

function handleResize(e: MouseEvent) {
  if (!isResizing.value) return;
  const newWidth = e.clientX - 60;
  uiStore.setConversationListWidth(newWidth);
}

function stopResize() {
  isResizing.value = false;
  document.removeEventListener('mousemove', handleResize);
  document.removeEventListener('mouseup', stopResize);
}
</script>

<template>
  <section
    class="conversation-list"
    :style="{ width: uiStore.conversationListWidth + 'px' }"
  >
    <div class="list-header">
      <div class="search-wrapper">
        <IconSearch :size="14" class="search-icon" style="width: 14px; height: 14px;" />
        <input
          type="text"
          class="search-input"
          placeholder="搜索"
          :value="chatStore.searchQuery"
          @input="handleSearch"
        />
      </div>
      <button class="add-btn">
        <IconPlus :size="16" style="width: 16px; height: 16px;" />
      </button>
    </div>

    <div class="list-body">
      <div v-if="chatStore.filteredConversations.length === 0" class="empty-state">
        <p>暂无会话</p>
      </div>
      <div
        v-else
        v-for="conv in chatStore.filteredConversations"
        :key="conv.id"
        class="conversation-item"
        :class="{ selected: conv.id === chatStore.currentConversationId }"
        @click="chatStore.selectConversation(conv.id)"
      >
        <img class="avatar" :src="conv.avatar" :alt="conv.name" />
        <div class="item-content">
          <div class="item-header">
            <span class="name text-ellipsis">{{ conv.name }}</span>
            <span class="time">{{ formatTime(conv.timestamp) }}</span>
          </div>
          <div class="item-footer">
            <p class="preview text-ellipsis">{{ conv.lastMessage }}</p>
            <span v-if="conv.unreadCount > 0" class="unread-badge">
              {{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="list-footer">
      <IconMenu :size="14" style="width: 14px; height: 14px;" />
      <span>折叠置顶聊天</span>
      <IconUp :size="14" class="up-icon" style="width: 14px; height: 14px;" />
    </div>

    <div class="resize-handle" @mousedown="startResize"></div>
  </section>
</template>

<script lang="ts">
function formatTime(date: Date): string {
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);

  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin}分钟前`;

  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}小时前`;

  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}天前`;

  return date.toLocaleDateString('zh-CN');
}
</script>

<style scoped>
.conversation-list {
  position: relative;
  height: 100%;
  background-color: var(--bg-conversation-list);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  min-width: var(--conversation-list-min-width);
  max-width: var(--conversation-list-max-width);
}

.list-header {
  padding: 25px 12px 10px;
  background-color: var(--bg-conversation-list);
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1;
}

.search-icon {
  position: absolute;
  left: 8px;
  color: #999;
}

.search-input {
  flex: 1;
  height: 28px;
  padding: 0 12px 0 28px;
  border-radius: 4px;
  background-color: #e2e2e2;
  font-size: 12px;
  border: none;
}

.add-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #e2e2e2;
  border-radius: 4px;
  color: #515151;
  cursor: pointer;
  flex-shrink: 0;
}

.add-btn:hover {
  background-color: #d2d2d2;
}

.list-body {
  flex: 1;
  overflow-y: auto;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-tertiary);
  font-size: var(--font-size-md);
}

.conversation-item {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  position: relative;
  transition: background-color var(--transition-fast);
}

.conversation-item:hover {
  background-color: var(--bg-hover);
}

.conversation-item.selected {
  background-color: #1aad19;
}

.conversation-item.selected .name,
.conversation-item.selected .time,
.conversation-item.selected .preview {
  color: #fff;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  object-fit: cover;
}

.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.name {
  font-size: var(--font-size-lg);
  font-weight: 500;
  color: var(--text-primary);
  max-width: 180px;
}

.time {
  font-size: var(--font-size-xs);
  color: var(--text-placeholder);
  flex-shrink: 0;
  margin-left: 8px;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview {
  font-size: var(--font-size-base);
  color: var(--text-tertiary);
  max-width: 200px;
}

.unread-badge {
  min-width: 18px;
  height: 18px;
  padding: 2px 6px;
  border-radius: var(--radius-full);
  background-color: var(--color-unread-bg);
  color: white;
  font-size: var(--font-size-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-left: 4px;
}

.list-footer {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #999;
  font-size: 12px;
  border-top: 1px solid #e5e5e5;
  cursor: pointer;
}

.list-footer:hover {
  background-color: #e5e5e5;
}

.resize-handle {
  position: absolute;
  right: -2px;
  top: 0;
  bottom: 0;
  width: 4px;
  cursor: col-resize;
  z-index: 10;
  transition: background-color var(--transition-fast);
}

.resize-handle:hover {
  background-color: var(--color-primary);
}
</style>
