<script setup lang="ts">
import { ref, onMounted } from 'vue';
import IconRobot from '@arco-design/web-vue/es/icon/icon-robot';
import IconPlus from '@arco-design/web-vue/es/icon/icon-plus';
import IconCheck from '@arco-design/web-vue/es/icon/icon-check';
import IconUpload from '@arco-design/web-vue/es/icon/icon-upload';
import { useAgentsStore, type AgentConfig } from '../../stores/agents';
import { useChatStore } from '../../stores/chat';
import { Message } from '@arco-design/web-vue';

const agentsStore = useAgentsStore();
const chatStore = useChatStore();

// 智能体管理相关状态
const editingAgent = ref<AgentConfig | null>(null);
const isEditing = ref(false);

// 头像选择弹窗状态
const isAvatarModalVisible = ref(false);
const avatarModalTarget = ref<AgentConfig | null>(null);
const tempSelectedAvatar = ref('');

const avatarPresets = [
  '/linger.png',
  'https://api.dicebear.com/7.x/bottts/svg?seed=Lucky',
  'https://api.dicebear.com/7.x/bottts/svg?seed=Bear',
  'https://api.dicebear.com/7.x/bottts/svg?seed=Coco',
  'https://api.dicebear.com/7.x/bottts-neutral/svg?seed=Felix',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Milo',
  'https://api.dicebear.com/7.x/identicon/svg?seed=Vision',
  'https://api.dicebear.com/7.x/identicon/svg?seed=Alpha',
  'https://api.dicebear.com/7.x/initials/svg?seed=AI',
  'https://api.dicebear.com/7.x/pixel-art/svg?seed=Retro'
];

const handleEditAgent = (agent: AgentConfig) => {
  editingAgent.value = { ...agent };
  isEditing.value = true;
};

const handleCreateAgent = async () => {
  const defaults = await agentsStore.getAgentDefaults();
  editingAgent.value = { 
    name: `agent_${Date.now()}`, 
    displayName: '新智能体', 
    systemPrompt: '', 
    isDefault: false, 
    isActive: true,
    ...defaults
  };
  isEditing.value = true;
};

const openAvatarModal = (agent: AgentConfig) => {
  avatarModalTarget.value = agent;
  tempSelectedAvatar.value = agent.avatar || '';
  isAvatarModalVisible.value = true;
};

// 头像上传转Base64
const handleBase64Upload = (fileList: any) => {
  // Arco Design 的 @change 返回的是文件列表
  const fileItem = fileList[fileList.length - 1];
  const file = fileItem.file;
  if (!file) return;
  
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    Message.error('只能上传图片文件');
    return;
  }
  
  const maxSize = 2 * 1024 * 1024; // 2MB
  if (file.size > maxSize) {
    Message.error('图片大小不能超过 2MB!');
    return;
  }

  const reader = new FileReader();
  reader.readAsDataURL(file);
  reader.onload = async () => {
    const base64 = reader.result as string;
    tempSelectedAvatar.value = base64;
    // 如果是现有智能体，直接同步
    if (avatarModalTarget.value?.id) {
       await syncAvatarToBackend(avatarModalTarget.value.id, base64);
    }
  };
};

const selectPresetAvatar = async (url: string) => {
  tempSelectedAvatar.value = url;
  if (avatarModalTarget.value?.id) {
    await syncAvatarToBackend(avatarModalTarget.value.id, url);
  }
};

const syncAvatarToBackend = async (id: number, avatar: string) => {
  try {
    const agent = agentsStore.agents.find(a => a.id === id);
    if (agent) {
      const updated = { ...agent, avatar };
      await agentsStore.updateAgent(id, updated);
      if (editingAgent.value && editingAgent.value.id === id) {
        editingAgent.value.avatar = avatar;
      }
      await chatStore.loadConversations();
      Message.success('头像已同步更新');
    }
  } catch (e) {
    Message.error('头像同步失败');
  }
};

const confirmAvatarSelection = () => {
  if (avatarModalTarget.value) {
    avatarModalTarget.value.avatar = tempSelectedAvatar.value;
  }
  isAvatarModalVisible.value = false;
};

const handleSaveAgent = async () => {
  if (!editingAgent.value) return;
  
  if (!editingAgent.value.avatar) {
    editingAgent.value.avatar = `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(editingAgent.value.name)}`;
  }
  
  if (editingAgent.value.id) {
    await agentsStore.updateAgent(editingAgent.value.id, editingAgent.value);
  } else {
    await agentsStore.createAgent(editingAgent.value);
  }
  await chatStore.loadConversations();
  isEditing.value = false;
  editingAgent.value = null;
};

const handleCancelEdit = () => {
  isEditing.value = false;
  editingAgent.value = null;
};

onMounted(() => {
  agentsStore.fetchAgents();
});
</script>

<template>
  <div class="agent-manager">
    <div v-if="!isEditing" class="agent-list-view">
      <div class="section-header">
        <h3 class="section-title">智能体列表</h3>
        <a-button type="primary" size="small" @click="handleCreateAgent">新增智能体</a-button>
      </div>
          
      <div class="agent-list">
        <div v-for="agent in agentsStore.agents" :key="agent.id" class="agent-card">
          <div class="agent-info">
            <a-avatar 
              :size="40" 
              class="clickable-avatar"
              :style="{ backgroundColor: agent.color || 'var(--primary-color)' }"
              @click="openAvatarModal(agent)"
            >
              <img v-if="agent.avatar" :src="agent.avatar" />
              <IconRobot v-else />
              <div class="avatar-hover-mask"><IconPlus /></div>
            </a-avatar>
            <div class="agent-meta">
              <div class="agent-name">
                {{ agent.displayName }}
                <a-tag v-if="agent.isDefault" color="arcoblue" size="small" borderless>默认</a-tag>
              </div>
              <div class="agent-id">ID: {{ agent.name }}</div>
            </div>
          </div>
          <div class="agent-actions">
            <a-button type="text" size="small" @click="handleEditAgent(agent)">编辑</a-button>
            <a-button v-if="!agent.isDefault" type="text" status="danger" size="small" @click="async () => { await agentsStore.deleteAgent(agent.id!); await chatStore.loadConversations(); }">删除</a-button>
            <a-button v-if="!agent.isDefault" type="outline" size="small" @click="async () => { await agentsStore.setDefaultAgent(agent.id!); await chatStore.loadConversations(); }">设为默认</a-button>
          </div>
        </div>
        <div v-if="agentsStore.agents.length === 0" class="empty-state">
          暂无智能体配置
        </div>
      </div>
    </div>

    <!-- 编辑界面 -->
    <div v-else class="agent-edit-form">
      <div class="section-header">
        <h3 class="section-title">{{ editingAgent?.id ? '编辑智能体' : '新增智能体' }}</h3>
        <div class="header-actions">
          <a-button size="small" @click="handleCancelEdit">取消</a-button>
          <a-button type="primary" size="small" @click="handleSaveAgent">保存</a-button>
        </div>
      </div>

      <a-form :model="editingAgent || {}" layout="vertical">
        <div class="form-row" style="align-items: center; margin-bottom: 20px;">
          <div class="avatar-uploader" @click="openAvatarModal(editingAgent!)">
            <a-avatar :size="64" :style="{ backgroundColor: editingAgent!.color || 'var(--primary-color)' }">
              <img v-if="editingAgent?.avatar" :src="editingAgent.avatar" />
              <IconPlus v-else style="font-size: 24px; color: var(--color-text-3);" />
              <div class="avatar-hover-mask"><IconPlus /></div>
            </a-avatar>
            <div class="upload-tip">点击修改头像</div>
          </div>

          <div style="flex: 1;">
            <div class="form-row">
              <a-form-item label="显示名称" required>
                <a-input v-model="editingAgent!.displayName" placeholder="例如：生活助手" />
              </a-form-item>
              <a-form-item label="唯一标识 (Name)" required>
                <a-input v-model="editingAgent!.name" placeholder="例如：life_helper" :disabled="!!editingAgent?.id" />
              </a-form-item>
            </div>
          </div>
        </div>
            
        <a-form-item label="系统提示词 (System Prompt)" required>
          <a-textarea v-model="editingAgent!.systemPrompt" :auto-size="{ minRows: 3, maxRows: 6 }" placeholder="定义助理的角色、性格和行为..." />
        </a-form-item>

        <a-collapse :default-active-key="[]" expand-icon-position="right">
          <a-collapse-item header="高级 Prompt 配置" key="advanced">
            <a-form-item label="事实提取提示词 (Fact Extraction)">
              <a-textarea v-model="editingAgent!.factExtractionPrompt" :auto-size="{ minRows: 2 }" />
            </a-form-item>
            <a-form-item label="行为原则">
              <a-textarea v-model="editingAgent!.behaviorPrinciples" :auto-size="{ minRows: 2 }" />
            </a-form-item>
            <a-form-item label="情感策略">
              <a-textarea v-model="editingAgent!.emotionalStrategy" :auto-size="{ minRows: 2 }" />
            </a-form-item>
          </a-collapse-item>
              
          <a-collapse-item header="外观设置" key="appearance">
            <div class="form-row">
              <a-form-item label="主题色">
                <a-input v-model="editingAgent!.color" placeholder="#165DFF" />
              </a-form-item>
              <a-form-item label="系统状态">
                <a-switch v-model="editingAgent!.isActive" />
                <span style="margin-left: 8px; font-size: 12px; color: var(--text-secondary);">启用智能体</span>
              </a-form-item>
            </div>
          </a-collapse-item>
        </a-collapse>
      </a-form>
    </div>

    <!-- 头像选择弹窗 -->
    <a-modal
      v-model:visible="isAvatarModalVisible"
      title="选择智能体头像"
      @ok="confirmAvatarSelection"
      width="400px"
    >
      <div class="avatar-selector-grid">
        <div 
          v-for="url in avatarPresets" 
          :key="url" 
          class="preset-item"
          :class="{ selected: tempSelectedAvatar === url }"
          @click="selectPresetAvatar(url)"
        >
          <img :src="url" />
          <div v-if="tempSelectedAvatar === url" class="selected-badge">
            <IconCheck />
          </div>
        </div>
        
        <!-- 上传选项 -->
        <a-upload
          action="/"
          :auto-upload="false"
          :show-file-list="false"
          @change="handleBase64Upload"
          accept="image/*"
        >
          <template #upload-button>
            <div class="preset-item upload-box" :class="{ selected: tempSelectedAvatar.startsWith('data:image') }">
              <img v-if="tempSelectedAvatar.startsWith('data:image')" :src="tempSelectedAvatar" />
              <IconUpload v-else style="font-size: 24px;" />
              <div class="upload-text">{{ tempSelectedAvatar.startsWith('data:image') ? '已选择' : '点击上传' }}</div>
            </div>
          </template>
        </a-upload>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.agent-manager {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 0 4px;
}

.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-card {
  background-color: var(--bg-input);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid var(--border-color);
  transition: all 0.2s;
}

.agent-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.agent-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.clickable-avatar {
  cursor: pointer;
  position: relative;
}

.avatar-hover-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: white;
  border-radius: 50%;
}

.clickable-avatar:hover .avatar-hover-mask,
.avatar-uploader:hover .avatar-hover-mask {
  opacity: 1;
}

.agent-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-id {
  font-size: 12px;
  color: var(--text-tertiary);
}

.agent-actions {
  display: flex;
  gap: 8px;
}

.agent-edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row > * {
  flex: 1;
}

.avatar-uploader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  margin-right: 16px;
}

.avatar-uploader:hover .upload-tip {
  color: var(--primary-color);
}

.upload-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  transition: color 0.2s;
}

.empty-state {
  padding: 40px;
  text-align: center;
  color: var(--text-tertiary);
  background-color: var(--bg-input);
  border-radius: 12px;
  border: 1px dashed var(--border-color);
}

/* Modal Grid */
.avatar-selector-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  max-height: 400px;
  overflow-y: auto;
  padding: 8px;
}

.preset-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 12px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-sidebar);
}

.preset-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preset-item:hover {
  border-color: var(--color-primary-light-3);
  transform: scale(1.05);
}

.preset-item.selected {
  border-color: var(--primary-color);
  background-color: var(--color-primary-light-1);
}

.selected-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  background: var(--primary-color);
  color: white;
  border-radius: 50%;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
}

.upload-box {
  border: 2px dashed var(--border-color);
  color: var(--text-tertiary);
}

.upload-text {
  font-size: 12px;
  margin-top: 4px;
}
</style>
