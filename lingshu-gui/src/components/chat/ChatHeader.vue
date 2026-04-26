<script setup lang="ts">
import type { Conversation } from '../../types/conversation';
import IconSettings from '@arco-design/web-vue/es/icon/icon-settings';
import IconRobot from '@arco-design/web-vue/es/icon/icon-robot';
import IconCaretDown from '@arco-design/web-vue/es/icon/icon-caret-down';
import IconSound from '@arco-design/web-vue/es/icon/icon-sound';
import IconMute from '@arco-design/web-vue/es/icon/icon-mute';
import { useChatStore } from '../../stores/chat';
import { useAgentsStore } from '../../stores/agents';
import { useTts } from '../../composables/useTts';
import { computed, onMounted } from 'vue';

const props = defineProps<{
  conversation: Conversation | null;
}>();

const chatStore = useChatStore();
const agentsStore = useAgentsStore();
const { autoTtsEnabled, toggleAutoTts, isPlaying, stop } = useTts();

onMounted(() => {
  agentsStore.fetchAgents();
});

const currentAgent = computed(() => {
  return agentsStore.agents.find(a => a.id === chatStore.currentAgentId) || 
         agentsStore.agents.find(a => a.isDefault);
});

const handleAgentChange = (agentId: any) => {
  chatStore.setAgentId(agentId);
};

const handleTtsToggle = () => {
  if (isPlaying.value) {
    stop();
  } else {
    toggleAutoTts();
  }
};
</script>

<template>
  <header v-if="conversation" class="chat-header" data-tauri-drag-region>
    <div class="header-left">
      <div v-if="conversation.id === '1'" class="agent-selector">
        <a-dropdown @select="handleAgentChange" trigger="click">
          <div class="current-agent">
            <a-avatar :size="24" :style="{ backgroundColor: currentAgent?.color || 'var(--primary-color)' }">
              <img v-if="currentAgent?.avatar" :src="currentAgent.avatar" />
              <IconRobot v-else />
            </a-avatar>
            <span class="name">{{ currentAgent?.displayName || conversation.name }}</span>
            <IconCaretDown class="caret" />
          </div>
          <template #content>
            <a-doption v-for="agent in agentsStore.agents" :key="agent.id" :value="agent.id">
              <template #icon>
                <a-avatar :size="16" :style="{ backgroundColor: agent.color || 'var(--primary-color)' }">
                  <img v-if="agent.avatar" :src="agent.avatar" />
                  <IconRobot v-else />
                </a-avatar>
              </template>
              {{ agent.displayName }}
            </a-doption>
          </template>
        </a-dropdown>
      </div>
      <template v-else>
        <span class="name">{{ conversation.name }}</span>
      </template>
      <div v-if="conversation.id === '1'" class="ai-badge">AI</div>
    </div>
    <div class="header-right">
      <button 
        class="action-btn" 
        :class="{ 'is-playing': isPlaying, 'is-active': autoTtsEnabled }"
        :title="isPlaying ? '停止播放' : (autoTtsEnabled ? '关闭自动语音' : '开启自动语音')" 
        @click="handleTtsToggle"
      >
        <IconSound v-if="autoTtsEnabled || isPlaying" :size="18" :class="{ 'playing-anim': isPlaying }" />
        <IconMute v-else :size="18" />
      </button>
      <button class="action-btn" title="设置" id="header-settings-btn">
        <IconSettings :size="18" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.chat-header {
  height: 44px;
  background-color: transparent;
  border-bottom: 1px solid var(--header-border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-selector {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s;
}

.agent-selector:hover {
  background-color: var(--bg-hover);
}

.current-agent {
  display: flex;
  align-items: center;
  gap: 8px;
}

.caret {
  font-size: 10px;
  color: var(--text-tertiary);
  margin-left: -2px;
}

.name {
  font-size: var(--font-size-md);
  color: var(--text-primary);
  font-weight: 500;
}

.ai-badge {
  background-color: var(--text-placeholder);
  color: #fff;
  font-size: 10px;
  padding: 0px 4px;
  border-radius: 2px;
  transform: scale(0.9);
  line-height: 1.4;
}

.header-right {
  display: flex;
  align-items: center;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px;
  border-radius: 4px;
  transition: all 0.2s;
}

.action-btn:hover {
  background-color: var(--bg-hover);
  color: var(--text-primary);
}

.action-btn.is-active {
  color: var(--primary-color);
}

.action-btn.is-playing {
  color: var(--success-color, #00b42a);
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.1); opacity: 0.8; }
  100% { transform: scale(1); opacity: 1; }
}

.playing-anim {
  animation: pulse 1.5s infinite ease-in-out;
}
</style>
