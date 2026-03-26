<script setup lang="ts">
import { NDrawer, NDrawerContent, NForm, NFormItem, NInput, NSelect, NButton, NSpace, NDivider, NRadioGroup, NRadioButton } from 'naive-ui'
import { useSettings } from '@/stores/settingsStore'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits(['update:show'])

const {
  settings,
  chatModelOptions,
  loadingChatModels,
  fetchSettings,
  saveSettings,
  fetchChatModels,
  debouncedFetchChatModels,
  handleSourceChange
} = useSettings()

async function handleSave() {
  try {
    await saveSettings()
    emit('update:show', false)
  } catch (err) {
    console.error('Failed to save settings', err)
  }
}
</script>

<template>
  <n-drawer :show="show" @update:show="emit('update:show', $event)" :width="400">
    <n-drawer-content title="系统设置" closable>
      <n-space vertical size="large">
        <n-divider title-placement="left">核心内核配置</n-divider>
        <div class="source-toggle">
          <n-radio-group v-model:value="settings.source" size="medium" @update:value="handleSourceChange">
            <n-radio-button value="ollama">Ollama (本地)</n-radio-button>
            <n-radio-button value="openai">Custom / OpenAI</n-radio-button>
          </n-radio-group>
        </div>

        <n-form label-placement="top">
          <n-form-item label="默认模型 ID">
            <n-select v-model:value="settings.model" :options="chatModelOptions" :loading="loadingChatModels" filterable tag />
          </n-form-item>
          <n-form-item label="服务基地址 (Base URL)">
            <n-input v-model:value="settings.baseUrl" placeholder="http://localhost:11434" @update:value="debouncedFetchChatModels" />
          </n-form-item>
          <n-form-item v-if="settings.source === 'openai'" label="API 密钥 (Access Key)">
            <n-input v-model:value="settings.apiKey" type="password" show-password-on="click" placeholder="sk-..." @update:value="debouncedFetchChatModels" />
          </n-form-item>
        </n-form>

        <n-divider title-placement="left">个性化</n-divider>
        <div class="settings-hint">
          更多关于存储、记忆阈值和感知频率的设置正在开发中。
        </div>

        <template #footer>
          <n-button type="primary" block @click="handleSave" class="save-btn">
            部署并应用配置
          </n-button>
        </template>
      </n-space>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.source-toggle {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.save-btn {
  height: 48px;
  background: var(--color-send-btn);
  box-shadow: 0 4px 16px var(--color-send-btn-glow);
  border: none;
  font-weight: 600;
}

.settings-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  opacity: 0.7;
}
</style>
