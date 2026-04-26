<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useChatStore } from '../../stores/chat';
import { useUIStore } from '../../stores/ui';
import { useAgentsStore } from '../../stores/agents';
import IconSearch from '@arco-design/web-vue/es/icon/icon-search';
import IconPlus from '@arco-design/web-vue/es/icon/icon-plus';
import IconMenu from '@arco-design/web-vue/es/icon/icon-menu';
import IconUp from '@arco-design/web-vue/es/icon/icon-up';
import IconDelete from '@arco-design/web-vue/es/icon/icon-delete';

const chatStore = useChatStore();
const uiStore = useUIStore();
const agentsStore = useAgentsStore();

const isResizing = ref(false);
const isNewChatModalVisible = ref(false);

onMounted(() => {
  console.log('ConversationList mounted, total conversations:', chatStore.conversations.length);
  chatStore.loadConversations();
});

const handleOpenNewChat = () => {
  agentsStore.fetchAgents();
  isNewChatModalVisible.value = true;
};

const handleSelectAgent = async (agentId: number) => {
  await chatStore.createNewConversation(agentId);
  isNewChatModalVisible.value = false;
};

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
      <button class="add-btn" @click="handleOpenNewChat">
        <IconPlus :size="16" style="width: 16px; height: 16px;" />
      </button>
    </div>

    <div class="list-body">
      <div v-if="chatStore.filteredConversations.length === 0" class="empty-state">
        <p>暂无会话</p>
      </div>
      <a-dropdown
        v-for="conv in chatStore.filteredConversations"
        :key="conv.id"
        trigger="contextMenu"
        alignPoint
        :style="{ display: 'block' }"
      >
        <div
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
        <template #content>
          <a-doption @click="chatStore.deleteConversation(conv.id)" class="delete-option">
            <template #icon><IconDelete /></template>
            删除会话
          </a-doption>
        </template>
      </a-dropdown>
    </div>

    <div class="list-footer">
      <IconMenu :size="14" style="width: 14px; height: 14px;" />
      <span>折叠置顶聊天</span>
      <IconUp :size="14" class="up-icon" style="width: 14px; height: 14px;" />
    </div>

    <div class="resize-handle" @mousedown="startResize"></div>

    <!-- 新建会话弹窗 -->
    <a-modal
      v-model:visible="isNewChatModalVisible"
      title="选择智能体开始聊天"
      :footer="false"
      width="400px"
    >
      <div class="agent-select-list">
        <div 
          v-for="agent in agentsStore.agents" 
          :key="agent.id" 
          class="agent-select-item"
          @click="handleSelectAgent(agent.id!)"
        >
          <a-avatar :size="40" :style="{ backgroundColor: agent.color || 'var(--primary-color)' }">
            <img v-if="agent.avatar" :src="agent.avatar" />
            <span v-else>{{ agent.displayName[0] }}</span>
          </a-avatar>
          <div class="agent-select-info">
            <div class="agent-select-name">{{ agent.displayName }}</div>
            <div class="agent-select-desc text-ellipsis">{{ agent.systemPrompt }}</div>
          </div>
        </div>
        <div v-if="agentsStore.agents.length === 0" class="empty-agents">
          暂无可用智能体，请前往设置创建
        </div>
      </div>
    </a-modal>
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
  border-right: none; /* 取消右侧边框线 */
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  min-width: var(--conversation-list-min-width);
  max-width: var(--conversation-list-max-width);
}

.list-header {
  padding: 12px 10px;
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
  color: var(--text-tertiary);
}

.search-input {
  flex: 1;
  height: 26px;
  padding: 0 12px 0 28px;
  border-radius: 6px;
  background-color: var(--bg-hover);
  font-size: var(--font-size-xs);
  color: var(--text-primary);
  border: 1px solid transparent;
  transition: all var(--transition-fast);
}

.search-input:focus {
  background-color: var(--bg-input);
  border-color: var(--color-primary);
}

.add-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-hover);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  flex-shrink: 0;
  border: none;
}

.add-btn:hover {
  background-color: var(--bg-selected);
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
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: 2px;
  cursor: pointer;
  position: relative;
  transition: background-color var(--transition-fast);
}

.conversation-item:hover {
  background-color: var(--bg-hover);
}

.conversation-item.selected {
  background-color: var(--color-item-active);
}

.conversation-item.selected .name,
.conversation-item.selected .time,
.conversation-item.selected .preview {
  color: var(--color-item-active-text);
}

.conversation-item.selected .time {
  opacity: 0.8;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 4px;
  flex-shrink: 0;
  object-fit: cover;
}

.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.name {
  font-size: var(--font-size-sm);
  font-weight: 400;
  color: var(--text-primary);
  max-width: 160px;
}

.time {
  font-size: 10px;
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
  font-size: 11px;
  color: var(--text-tertiary);
  max-width: 180px;
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
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  border-top: 1px solid var(--border-color);
  cursor: pointer;
}

.list-footer:hover {
  background-color: var(--bg-hover);
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

/* Agent Select Dialog Styles */
.agent-select-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
}

.agent-select-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.agent-select-item:hover {
  background-color: var(--bg-hover);
}

.agent-select-info {
  flex: 1;
  min-width: 0;
}

.agent-select-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.agent-select-desc {
  font-size: 11px;
  color: var(--text-tertiary);
}

.empty-agents {
  padding: 20px;
  text-align: center;
  color: var(--text-tertiary);
}

.delete-option {
  color: var(--color-danger, #f53f3f) !important;
}
</style>
