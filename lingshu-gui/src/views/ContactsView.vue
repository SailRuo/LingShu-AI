<script setup lang="ts">
import { ref } from 'vue';
import IconSearch from '@arco-design/web-vue/es/icon/icon-search';
import IconUserGroup from '@arco-design/web-vue/es/icon/icon-user-group';
import IconDown from '@arco-design/web-vue/es/icon/icon-down';
import IconRight from '@arco-design/web-vue/es/icon/icon-right';

interface ContactGroup {
  id: string;
  label: string;
  count: number;
  expanded: boolean;
}

const searchQuery = ref('');
const contactGroups = ref<ContactGroup[]>([
  { id: '1', label: '我的好友', count: 12, expanded: false },
  { id: '2', label: '家人', count: 5, expanded: false },
  { id: '3', label: '同事', count: 8, expanded: false },
  { id: '4', label: '客户', count: 15, expanded: false },
]);

function handleSearch(e: Event) {
  const target = e.target as HTMLInputElement;
  searchQuery.value = target.value;
}

function toggleGroup(group: ContactGroup) {
  group.expanded = !group.expanded;
}
</script>

<template>
  <div class="contacts-view">
    <div class="contacts-header">
      <div class="search-wrapper">
        <IconSearch :size="14" class="search-icon" style="width: 14px; height: 14px;" />
        <input
          type="text"
          class="search-input"
          placeholder="搜索"
          :value="searchQuery"
          @input="handleSearch"
        />
      </div>
    </div>

    <div class="contacts-manage-area">
      <button class="manage-btn">
        <IconUserGroup :size="16" style="width: 16px; height: 16px;" />
        <span>通讯录管理</span>
      </button>
    </div>

    <div class="contacts-list">
      <div
        v-for="group in contactGroups"
        :key="group.id"
        class="contact-group"
      >
        <div class="group-header" @click="toggleGroup(group)">
          <span class="group-label">{{ group.label }}</span>
          <div class="group-meta">
            <span class="group-count">{{ group.count }}</span>
            <IconRight v-if="!group.expanded" :size="14" class="arrow-icon" />
            <IconDown v-else :size="14" class="arrow-icon" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.contacts-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-conversation-list);
}

.contacts-header {
  padding: 25px 12px 10px;
  background-color: var(--bg-conversation-list);
}

.search-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 8px;
  color: #999;
  font-size: 14px;
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

.contacts-manage-area {
  padding: 0 12px 10px;
}

.manage-btn {
  width: 100%;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background-color: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  font-size: 13px;
  color: #333;
  cursor: pointer;
}

.manage-btn:hover {
  background-color: #f5f5f5;
}

.contacts-list {
  flex: 1;
  overflow-y: auto;
}

.contact-group {
  border-bottom: 1px solid #f0f0f0;
}

.group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.group-header:hover {
  background-color: var(--bg-hover);
}

.group-label {
  font-size: var(--font-size-base);
  color: var(--text-primary);
}

.group-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-count {
  font-size: var(--font-size-xs);
  color: var(--text-placeholder);
}

.arrow-icon {
  color: #999;
}
</style>
