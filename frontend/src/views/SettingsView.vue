<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { 
  NInput, NSelect, NButton, NIcon, 
  NRadioGroup, NRadioButton, NCard, NGrid, NGridItem, useMessage,
  NBreadcrumb, NBreadcrumbItem
} from 'naive-ui'
import { 
  RefreshCw, Settings, Cpu, Globe, 
  Activity, Zap, ChevronRight 
} from 'lucide-vue-next'

const message = useMessage()

const settings = ref({
  source: '',
  model: '',
  baseUrl: '',
  apiKey: '',
})

const modelOptions = ref<{label: string, value: string}[]>([])
const loadingModels = ref(false)

async function fetchModels(silent = false) {
  if (!settings.value.baseUrl || !settings.value.source) return
  loadingModels.value = true
  try {
    const params = new URLSearchParams({
      source: settings.value.source,
      baseUrl: settings.value.baseUrl,
      apiKey: settings.value.apiKey,
    })
    const res = await fetch(`/api/chat/models?${params.toString()}`)
    const models = await res.json()
    modelOptions.value = models.map((m: string) => ({ label: m, value: m }))
    
    if (modelOptions.value.length > 0 && !modelOptions.value.find(o => o.value === settings.value.model)) {
      settings.value.model = modelOptions.value[0].value
    }
    if (!silent) message.success('模型列表已更新')
  } catch (err) {
    message.error('无法连接到服务，请检查地址或密钥')
    modelOptions.value = [{ label: '未找到模型', value: '' }]
  } finally {
    loadingModels.value = false
  }
}

function handleSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.baseUrl = 'http://localhost:11434'
  } else {
    settings.value.baseUrl = 'http://localhost:3000'
  }
}

watch(
  [() => settings.value.source, () => settings.value.baseUrl, () => settings.value.apiKey],
  () => {
    fetchModels(true)
  }
)

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

onMounted(() => {
  fetchSettings()
})

const handleSave = async () => {
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
    message.success('内核配置已同步至系统中枢')
  } catch (err) {
    message.error('配置保存失败')
  }
}
</script>

<template>
  <div class="settings-view">
    <!-- Header Section -->
    <header class="settings-header">
      <div class="header-content">
        <n-breadcrumb separator=">">
          <n-breadcrumb-item>SYSTEM</n-breadcrumb-item>
          <n-breadcrumb-item>配置中心 (KNOWLEDGE HUB)</n-breadcrumb-item>
        </n-breadcrumb>
        <h1 class="page-title">
          <n-icon :component="Settings" />
          系统内核设置
        </h1>
        <p class="page-subtitle">管理灵枢 AI 的神经连接、模型权重及核心通信协议。</p>
      </div>
      <div class="header-actions">
        <n-button type="primary" size="large" @click="handleSave" class="main-save-btn">
          <template #icon><n-icon :component="Zap" /></template>
          部署并应用更改
        </n-button>
      </div>
    </header>

    <div class="settings-container">
      <n-grid :cols="24" :x-gap="24" :y-gap="24">
        <!-- Main Column -->
        <n-grid-item :span="16">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Cpu" />
              <h2>模型动力源 (Core Model Source)</h2>
            </div>
            
            <n-card class="glass-card">
              <div class="source-selector">
                <n-radio-group v-model:value="settings.source" size="large" @update:value="handleSourceChange">
                  <n-radio-button value="ollama">
                    <div class="radio-content">
                      <n-icon :component="Activity" />
                      <span>Ollama (本地)</span>
                    </div>
                  </n-radio-button>
                  <n-radio-button value="openai">
                    <div class="radio-content">
                      <n-icon :component="Globe" />
                      <span>Custom / OpenAI</span>
                    </div>
                  </n-radio-button>
                </n-radio-group>
              </div>

              <div class="setting-item">
                <div class="item-label">
                  <span class="label-text">核心模型路径</span>
                  <n-button quaternary circle size="small" @click="fetchModels(false)" :loading="loadingModels">
                    <template #icon><n-icon :component="RefreshCw" /></template>
                  </n-button>
                </div>
                <n-select
                  v-model:value="settings.model"
                  :options="modelOptions"
                  placeholder="选择神经网络权重..."
                  size="large"
                  filterable
                  tag
                />
                <p class="field-hint">当前系统通过 REST API 接入对应的推理引擎。</p>
              </div>
            </n-card>

            <div class="section-header mt-8">
              <n-icon :component="Globe" />
              <h2>通信节点 (Endpoint)</h2>
            </div>

            <n-card class="glass-card">
              <div class="dual-fields">
                <div class="setting-item flex-1">
                  <div class="item-label">服务地址 (Base URL)</div>
                  <n-input v-model:value="settings.baseUrl" placeholder="https://..." size="large" />
                </div>
                <div v-if="settings.source === 'openai'" class="setting-item flex-1">
                  <div class="item-label">API 密钥 (Access Key)</div>
                  <n-input v-model:value="settings.apiKey" type="password" show-password-on="click" placeholder="sk-..." size="large" />
                </div>
              </div>
            </n-card>
          </section>
        </n-grid-item>

        <!-- Sidebar Column -->
        <n-grid-item :span="8">
          <div class="info-sidebar">
            <n-card title="内核运行状态" class="glass-card status-card">
              <div class="status-list">
                <div class="status-entry">
                  <span class="dot active"></span>
                  <span class="text">神经网络：在线</span>
                </div>
                <div class="status-entry">
                  <span class="dot active"></span>
                  <span class="text">协议栈：SSE/HTTP2</span>
                </div>
                <div class="status-entry">
                  <span class="dot warning"></span>
                  <span class="text">存储带宽：正常</span>
                </div>
              </div>
              <template #footer>
                <div class="version-tag">STABLE v0.8.4-BETA</div>
              </template>
            </n-card>

            <n-card title="关于灵枢" class="glass-card mt-6">
              <p class="info-text">
                灵枢 (LingShu-AI) 是基于神经网络的认知辅助中枢。
                您的所有配置都将持久化在本地加密存储中。
              </p>
              <div class="links">
                <n-button text type="primary">文档 <n-icon :component="ChevronRight" /></n-button>
                <n-button text type="primary">Github <n-icon :component="ChevronRight" /></n-button>
              </div>
            </n-card>
          </div>
        </n-grid-item>
      </n-grid>
    </div>
  </div>
</template>

<style scoped>
.settings-view {
  padding: 40px 60px;
  max-width: 1200px;
  margin: 0 auto;
  animation: fadeIn 0.6s cubic-bezier(0.23, 1, 0.32, 1);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 48px;
  border-bottom: 1px solid var(--color-outline);
  padding-bottom: 32px;
}

.page-title {
  font-size: 32px;
  font-weight: 800;
  margin: 12px 0 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(135deg, var(--color-text), var(--color-primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.page-subtitle {
  color: var(--color-text-dim);
  font-size: 14px;
  max-width: 500px;
}

.main-save-btn {
  height: 50px;
  padding: 0 32px;
  border-radius: 14px;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: var(--color-primary) !important;
  box-shadow: 0 8px 24px var(--color-primary-dim);
  border: none;
  transition: all 0.3s ease;
}

.main-save-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px var(--color-primary-dim);
}

.glass-card {
  background: var(--color-surface) !important;
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-outline) !important;
  border-radius: 20px !important;
  transition: all 0.3s ease;
}

.glass-card:hover {
  border-color: var(--color-primary-dim) !important;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  color: var(--color-text);
}

.section-header h2 {
  font-size: 16px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.mt-8 { margin-top: 32px; }
.mt-6 { margin-top: 24px; }
.flex-1 { flex: 1; }

.source-selector {
  margin-bottom: 24px;
}

.radio-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setting-item {
  margin-bottom: 20px;
}

.item-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dim);
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.field-hint {
  font-size: 11px;
  color: var(--color-text-dim);
  margin-top: 8px;
  opacity: 0.7;
}

.dual-fields {
  display: flex;
  gap: 20px;
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.active { background: var(--color-success); box-shadow: 0 0 8px var(--color-success); }
.dot.warning { background: var(--color-warning); }

.version-tag {
  font-size: 10px;
  font-family: monospace;
  color: var(--color-text-dim);
  opacity: 0.5;
}

.info-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-dim);
}

.links {
  margin-top: 16px;
  display: flex;
  gap: 16px;
}

:deep(.n-input), :deep(.n-select .n-base-selection) {
  --n-border-radius: 12px !important;
  background-color: rgba(0,0,0,0.05) !important;
}
</style>
