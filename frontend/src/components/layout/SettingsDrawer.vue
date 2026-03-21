<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { NDrawer, NDrawerContent, NForm, NFormItem, NInput, NSelect, NButton, NSpace, NDivider, NRadioGroup, NRadioButton } from 'naive-ui'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits(['update:show'])

const settings = ref({
  source: 'ollama',
  model: 'qwen3.5:4b',
  baseUrl: 'http://localhost:11434',
  apiKey: '',
})

const modelOptions = ref<{label: string, value: string}[]>([])
const loadingModels = ref(false)

async function fetchModels() {
  loadingModels.value = true
  try {
    const params = new URLSearchParams()
    if (settings.value.source) params.append('source', settings.value.source)
    if (settings.value.baseUrl) params.append('baseUrl', settings.value.baseUrl)
    if (settings.value.apiKey) params.append('apiKey', settings.value.apiKey)
    
    const res = await fetch(`/api/chat/models?${params.toString()}`)
    const models = await res.json()
    modelOptions.value = models.map((m: string) => ({ label: m, value: m }))
  } catch {
    modelOptions.value = [{ label: 'Qwen 3.5 4B (默认)', value: 'qwen3.5:4b' }]
  } finally {
    loadingModels.value = false
  }
}

async function fetchSettings() {
  try {
    const res = await fetch('/api/settings')
    const data = await res.json()
    settings.value = {
      source: data.source || 'ollama',
      model: data.chatModel,
      baseUrl: data.baseUrl,
      apiKey: data.apiKey
    }
  } catch (err) {
    console.error('Failed to fetch settings', err)
  }
}

onMounted(async () => {
  await fetchSettings()
  fetchModels()
})

watch(() => props.show, async (newVal) => {
  if (newVal) {
    await fetchSettings()
    fetchModels()
  }
})

function handleSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.baseUrl = 'http://localhost:11434'
  } else {
    settings.value.baseUrl = 'http://localhost:3000'
  }
  fetchModels()
}

watch(() => settings.value.baseUrl, (_newVal, oldVal) => {
  if (oldVal !== undefined) fetchModels()
})

async function handleSave() {
  try {
    await fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        source: settings.value.source,
        chatModel: settings.value.model,
        baseUrl: settings.value.baseUrl,
        apiKey: settings.value.apiKey
      })
    })
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
            <n-select v-model:value="settings.model" :options="modelOptions" :loading="loadingModels" filterable tag />
          </n-form-item>
          <n-form-item label="服务基地址 (Base URL)">
            <n-input v-model:value="settings.baseUrl" placeholder="http://localhost:11434" />
          </n-form-item>
          <n-form-item v-if="settings.source === 'openai'" label="API 密钥 (Access Key)">
            <n-input v-model:value="settings.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
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
