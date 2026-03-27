<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { 
  NInput, NSelect, NButton, NIcon, NRadioGroup, NRadioButton, 
  NCard, NGrid, NGridItem, useMessage, NTabs, NTabPane,
  NTag, NSwitch, NPopconfirm, NModal, NForm, NFormItem,
  NInputNumber, NDivider
} from 'naive-ui'
import { 
  RefreshCw, Settings, Cpu, Globe, Activity, Zap, Plus, 
  Trash2, Edit, Star, Users, Bell, Send, Brain, Wrench, Palette, Mic,
  Bot, MessageCircle, Gem, Rocket, Sparkles
} from 'lucide-vue-next'
import McpSettings from '@/components/McpSettings.vue'
import ThemeModal from '@/components/common/ThemeModal.vue'
import { useThemeStore } from '@/stores/themeStore'

const message = useMessage()
const activeTab = ref('basic')
const showThemeModal = ref(false)
const themeStore = useThemeStore()

const settings = ref({
  source: '',
  model: '',
  baseUrl: '',
  apiKey: '',
  embedSource: 'ollama',
  embedModel: '',
  embedBaseUrl: 'http://localhost:11434',
  embedApiKey: '',
  proactiveEnabled: true,
  inactiveThresholdMinutes: 5,
  greetingCooldownSeconds: 300,
  inactiveCheckIntervalMs: 3600000,
})

const chatModelOptions = ref<{label: string, value: string}[]>([])
const embedModelOptions = ref<{label: string, value: string}[]>([])
const loadingChatModels = ref(false)
const loadingEmbedModels = ref(false)

interface Agent {
  id: number
  name: string
  displayName: string
  systemPrompt: string
  factExtractionPrompt: string
  behaviorPrinciples: string
  decisionMechanism: string
  toolCallRules: string
  emotionalStrategy: string
  greetingTriggers: string
  hiddenRules: string
  avatar: string
  color: string
  isDefault: boolean
  isActive: boolean
  createdAt: string
  updatedAt: string
}

const agents = ref<Agent[]>([])
const showAgentModal = ref(false)
const editingAgent = ref<Agent | null>(null)
const agentForm = ref({
  name: '',
  displayName: '',
  systemPrompt: '',
  factExtractionPrompt: '',
  behaviorPrinciples: '',
  decisionMechanism: '',
  toolCallRules: '',
  emotionalStrategy: '',
  greetingTriggers: '',
  hiddenRules: '',
  avatar: 'Bot',
  color: '#3b82f6',
  isActive: true
})

interface LocalTool {
  name: string
  displayName: string
  enabled: boolean
  prompt: string
}

const localTools = ref<LocalTool[]>([])
const loadingLocalTools = ref(false)

const asrSettings = ref({
  enabled: false,
  url: 'http://localhost:50001',
  sensitivity: 0.5
})
const savingAsr = ref(false)

async function fetchChatModels(silent = false) {
  if (!settings.value.baseUrl || !settings.value.source) return
  loadingChatModels.value = true
  try {
    const params = new URLSearchParams({
      source: settings.value.source,
      baseUrl: settings.value.baseUrl,
      apiKey: settings.value.apiKey,
    })
    const res = await fetch(`/api/chat/models?${params.toString()}`)
    const models = await res.json()
    chatModelOptions.value = models.map((m: string) => ({ label: m, value: m }))
    
    if (chatModelOptions.value.length > 0 && !chatModelOptions.value.find(o => o.value === settings.value.model)) {
      settings.value.model = chatModelOptions.value[0].value
    }
    if (!silent) message.success('对话模型列表已更新')
  } catch (err) {
    if (!silent) message.error('无法连接到对话服务')
    chatModelOptions.value = []
  } finally {
    loadingChatModels.value = false
  }
}

async function fetchEmbedModels(silent = false) {
  if (!settings.value.embedBaseUrl || !settings.value.embedSource) return
  loadingEmbedModels.value = true
  try {
    const params = new URLSearchParams({
      source: settings.value.embedSource,
      baseUrl: settings.value.embedBaseUrl,
      apiKey: settings.value.embedApiKey,
    })
    const res = await fetch(`/api/chat/models?${params.toString()}`)
    const models = await res.json()
    embedModelOptions.value = models.map((m: string) => ({ label: m, value: m }))
    
    if (embedModelOptions.value.length > 0 && !embedModelOptions.value.find(o => o.value === settings.value.embedModel)) {
      settings.value.embedModel = embedModelOptions.value[0].value
    }
    if (!silent) message.success('向量模型列表已更新')
  } catch (err) {
    if (!silent) message.error('无法连接到向量服务')
    embedModelOptions.value = []
  } finally {
    loadingEmbedModels.value = false
  }
}

function handleSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.baseUrl = 'http://localhost:11434'
  } else if (newSource === 'openai') {
    settings.value.baseUrl = 'http://localhost:3000'
  }
}

function handleEmbedSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.embedBaseUrl = 'http://localhost:11434'
  }
}

watch(
  [() => settings.value.source, () => settings.value.baseUrl, () => settings.value.apiKey],
  () => fetchChatModels(true)
)

watch(
  [() => settings.value.embedSource, () => settings.value.embedBaseUrl, () => settings.value.embedApiKey],
  () => fetchEmbedModels(true)
)

async function fetchSettings() {
  try {
    const res = await fetch('/api/settings')
    const data = await res.json()
    settings.value = {
      source: data.source || 'ollama',
      model: data.chatModel || '', 
      baseUrl: data.baseUrl || '',
      apiKey: data.apiKey || '',
      embedSource: data.embedSource || 'ollama',
      embedModel: data.embedModel || '',
      embedBaseUrl: data.embedBaseUrl || 'http://localhost:11434',
      embedApiKey: data.embedApiKey || '',
      proactiveEnabled: data.proactiveEnabled ?? true,
      inactiveThresholdMinutes: data.inactiveThresholdMinutes ?? 5,
      greetingCooldownSeconds: data.greetingCooldownSeconds ?? 300,
      inactiveCheckIntervalMs: data.inactiveCheckIntervalMs ?? 3600000,
    }
    fetchChatModels(true)
    fetchEmbedModels(true)
  } catch (err) {
    console.error('Failed to fetch settings', err)
  }
}

async function fetchAgents() {
  try {
    const res = await fetch('/api/agents')
    agents.value = await res.json()
  } catch (err) {
    console.error('Failed to fetch agents', err)
  }
}

async function fetchLocalTools() {
  loadingLocalTools.value = true
  try {
    const res = await fetch('/api/settings/local-tools')
    const data = await res.json()
    if (data && data.tools) {
      localTools.value = data.tools
    }
  } catch (err) {
    console.error('Failed to fetch local tools', err)
    message.error('获取本地工具配置失败')
  } finally {
    loadingLocalTools.value = false
  }
}

async function saveLocalTools() {
  try {
    await fetch('/api/settings/local-tools', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tools: localTools.value })
    })
    message.success('本地工具配置已保存')
  } catch (err) {
    message.error('保存本地工具配置失败')
  }
}

async function fetchAsrSettings() {
  try {
    const res = await fetch('/api/settings/asr')
    const data = await res.json()
    asrSettings.value = {
      enabled: data.enabled ?? false,
      url: data.url ?? 'http://localhost:50001',
      sensitivity: data.sensitivity ?? 0.5
    }
  } catch (err) {
    console.error('Failed to fetch ASR settings', err)
  }
}

async function saveAsrSettings() {
  savingAsr.value = true
  try {
    await fetch('/api/settings/asr', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(asrSettings.value)
    })
    message.success('ASR 配置已保存')
  } catch (err) {
    message.error('保存 ASR 配置失败')
  } finally {
    savingAsr.value = false
  }
}

onMounted(() => {
  fetchSettings()
  fetchAgents()
  fetchLocalTools()
  fetchAsrSettings()
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
        apiKey: settings.value.apiKey,
        embedSource: settings.value.embedSource,
        embedModel: settings.value.embedModel,
        embedBaseUrl: settings.value.embedBaseUrl,
        embedApiKey: settings.value.embedApiKey,
        proactiveEnabled: settings.value.proactiveEnabled,
        inactiveThresholdMinutes: settings.value.inactiveThresholdMinutes,
        greetingCooldownSeconds: settings.value.greetingCooldownSeconds,
        inactiveCheckIntervalMs: settings.value.inactiveCheckIntervalMs,
      })
    })
    message.success('内核配置已同步至系统中枢')
  } catch (err) {
    message.error('配置保存失败')
  }
}

const testingGreeting = ref(false)
const testGreetingResult = ref('')

async function testProactiveGreeting() {
  testingGreeting.value = true
  testGreetingResult.value = ''
  try {
    const res = await fetch('/api/chat/proactive/test-greeting')
    const reader = res.body?.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    
    while (reader) {
      const { done, value } = await reader.read()
      if (done) break
      
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const content = trimmed.replace(/^data:\s?/, '')
          testGreetingResult.value += content
        }
      }
    }
    
    if (buffer.trim().startsWith('data:')) {
      testGreetingResult.value += buffer.trim().replace(/^data:\s?/, '')
    }
    message.success('问候测试完成')
  } catch (err) {
    message.error('问候测试失败')
  } finally {
    testingGreeting.value = false
  }
}

async function openCreateAgent() {
  editingAgent.value = null
  try {
    const res = await fetch('/api/agents/defaults')
    const defaults = await res.json()
    agentForm.value = {
      name: '',
      displayName: '',
      systemPrompt: defaults.systemPrompt,
      factExtractionPrompt: defaults.factExtractionPrompt,
      behaviorPrinciples: defaults.behaviorPrinciples,
      decisionMechanism: defaults.decisionMechanism,
      toolCallRules: defaults.toolCallRules,
      emotionalStrategy: defaults.emotionalStrategy,
      greetingTriggers: defaults.greetingTriggers,
      hiddenRules: defaults.hiddenRules,
      avatar: defaults.avatar || '🤖',
      color: defaults.color || '#3b82f6',
      isActive: true
    }
    showAgentModal.value = true
  } catch (err) {
    message.error('无法获取默认提示词配置')
  }
}

function openEditAgent(agent: Agent) {
  editingAgent.value = agent
  agentForm.value = {
    name: agent.name,
    displayName: agent.displayName,
    systemPrompt: agent.systemPrompt,
    factExtractionPrompt: agent.factExtractionPrompt,
    behaviorPrinciples: agent.behaviorPrinciples || '',
    decisionMechanism: agent.decisionMechanism || '',
    toolCallRules: agent.toolCallRules || '',
    emotionalStrategy: agent.emotionalStrategy || '',
    greetingTriggers: agent.greetingTriggers || '',
    hiddenRules: agent.hiddenRules || '',
    avatar: agent.avatar || 'Bot',
    color: agent.color || '#3b82f6',
    isActive: agent.isActive
  }
  showAgentModal.value = true
}

async function saveAgent() {
  try {
    const url = editingAgent.value ? `/api/agents/${editingAgent.value.id}` : '/api/agents'
    const method = editingAgent.value ? 'PUT' : 'POST'
    
    await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(agentForm.value)
    })
    
    message.success(editingAgent.value ? '智能体已更新' : '智能体已创建')
    showAgentModal.value = false
    fetchAgents()
  } catch (err) {
    message.error('保存失败')
  }
}

async function deleteAgent(id: number) {
  try {
    await fetch(`/api/agents/${id}`, { method: 'DELETE' })
    message.success('智能体已删除')
    fetchAgents()
  } catch (err) {
    message.error('删除失败')
  }
}

async function setDefaultAgent(id: number) {
  try {
    await fetch(`/api/agents/${id}/set-default`, { method: 'POST' })
    message.success('已设为默认智能体')
    fetchAgents()
  } catch (err) {
    message.error('操作失败')
  }
}

const avatarOptions = [
  { icon: 'Bot', label: '机器人', component: Bot },
  { icon: 'Brain', label: '大脑', component: Brain },
  { icon: 'Sparkles', label: '火花', component: Sparkles },
  { icon: 'Cpu', label: '芯片', component: Cpu },
  { icon: 'Gem', label: '宝石', component: Gem },
  { icon: 'Rocket', label: '火箭', component: Rocket },
  { icon: 'Zap', label: '闪电', component: Zap }
]
const colorOptions = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#ec4899', '#06b6d4', '#84cc16']
</script>

<template>
  <div class="settings-view">
    <header class="settings-header">
      <div class="header-content">
        <h1 class="page-title">
          <n-icon :component="Settings" />
          系统设置
        </h1>
        <p class="page-subtitle">管理灵枢 AI 的模型配置与智能体</p>
      </div>
    </header>

    <n-tabs v-model:value="activeTab" type="line" animated class="settings-tabs">
      <n-tab-pane name="basic" tab="基础设置">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Palette" />
              <h2>主题外观</h2>
            </div>
            
            <n-card class="glass-card">
              <div class="theme-selector">
                <div class="theme-preview-card" @click="showThemeModal = true">
                  <div class="preview-content">
                    <div class="preview-info">
                      <div class="current-theme-label">当前主题</div>
                      <div class="current-theme-name">{{ themeStore.current.label }}</div>
                    </div>
                    <n-button type="primary" size="medium">
                      <template #icon><n-icon :component="Palette" /></template>
                      更换主题
                    </n-button>
                  </div>
                </div>
              </div>
            </n-card>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="model" tab="模型配置">
        <div class="tab-content">
          <n-tabs type="segment" animated>
            <n-tab-pane name="llm" tab="对话模型 (LLM)">
              <section class="settings-section pt-4">
                <div class="section-header">
                  <n-icon :component="Cpu" />
                  <h2>神经网络源</h2>
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
                      <n-button quaternary circle size="small" @click="fetchChatModels(false)" :loading="loadingChatModels">
                        <template #icon><n-icon :component="RefreshCw" /></template>
                      </n-button>
                    </div>
                    <n-select
                      v-model:value="settings.model"
                      :options="chatModelOptions"
                      placeholder="选择神经网络权重..."
                      size="large"
                      filterable
                      tag
                    />
                  </div>
                  
                  <div class="dual-fields mt-4">
                    <div class="setting-item flex-1">
                      <div class="item-label">服务地址</div>
                      <n-input v-model:value="settings.baseUrl" placeholder="https://..." size="large" />
                    </div>
                    <div v-if="settings.source === 'openai'" class="setting-item flex-1">
                      <div class="item-label">API 密钥</div>
                      <n-input v-model:value="settings.apiKey" type="password" show-password-on="click" placeholder="sk-..." size="large" />
                    </div>
                  </div>
                </n-card>
              </section>
            </n-tab-pane>

            <n-tab-pane name="embedding" tab="向量模型 (Embedding)">
              <section class="settings-section pt-4">
                <div class="section-header">
                  <n-icon :component="Brain" />
                  <h2>语义编码源</h2>
                </div>
                
                <n-card class="glass-card">
                  <div class="source-selector">
                    <n-radio-group v-model:value="settings.embedSource" size="large" @update:value="handleEmbedSourceChange">
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
                      <span class="label-text">向量化模型</span>
                      <n-button quaternary circle size="small" @click="fetchEmbedModels(false)" :loading="loadingEmbedModels">
                        <template #icon><n-icon :component="RefreshCw" /></template>
                      </n-button>
                    </div>
                    <n-select
                      v-model:value="settings.embedModel"
                      :options="embedModelOptions"
                      placeholder="选择向量化模型..."
                      size="large"
                      filterable
                      tag
                    />
                  </div>

                  <div class="dual-fields mt-4">
                    <div class="setting-item flex-1">
                      <div class="item-label">服务地址</div>
                      <n-input v-model:value="settings.embedBaseUrl" placeholder="https://..." size="large" />
                    </div>
                    <div v-if="settings.embedSource === 'openai'" class="setting-item flex-1">
                      <div class="item-label">API 密钥</div>
                      <n-input v-model:value="settings.embedApiKey" type="password" show-password-on="click" placeholder="sk-..." size="large" />
                    </div>
                  </div>
                </n-card>
              </section>
            </n-tab-pane>
          </n-tabs>

          <div class="save-section">
            <n-button type="primary" size="large" @click="handleSave">
              <template #icon><n-icon :component="Zap" /></template>
              保存所有模型配置
            </n-button>
          </div>
        </div>
      </n-tab-pane>

      <n-tab-pane name="agents" tab="智能体管理">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Users" />
              <h2>智能体列表</h2>
              <n-button type="primary" size="small" @click="openCreateAgent" class="create-btn">
                <template #icon><n-icon :component="Plus" /></template>
                创建智能体
              </n-button>
            </div>
            
            <div class="agents-grid">
              <n-card v-for="agent in agents" :key="agent.id" class="glass-card agent-card">
                <div class="agent-header">
                  <span class="agent-avatar" :style="{ background: agent.color || '#3b82f6' }">
                    {{ agent.avatar || '🤖' }}
                  </span>
                  <div class="agent-info">
                    <div class="agent-name">
                      {{ agent.displayName }}
                      <n-tag v-if="agent.isDefault" type="success" size="small">默认</n-tag>
                    </div>
                    <div class="agent-id">@{{ agent.name }}</div>
                  </div>
                </div>
                <div class="agent-prompt-preview">
                  {{ agent.systemPrompt?.substring(0, 100) }}...
                </div>
                <div class="agent-actions">
                  <n-button size="small" @click="openEditAgent(agent)">
                    <template #icon><n-icon :component="Edit" /></template>
                    编辑
                  </n-button>
                  <n-button v-if="!agent.isDefault" size="small" @click="setDefaultAgent(agent.id)">
                    <template #icon><n-icon :component="Star" /></template>
                    设为默认
                  </n-button>
                  <n-popconfirm v-if="!agent.isDefault" @positive-click="deleteAgent(agent.id)">
                    <template #trigger>
                      <n-button size="small" type="error">
                        <template #icon><n-icon :component="Trash2" /></template>
                        删除
                      </n-button>
                    </template>
                    确定删除此智能体吗？
                  </n-popconfirm>
                </div>
              </n-card>
            </div>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="proactive" tab="主动问候">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Bell" />
              <h2>主动问候配置</h2>
            </div>
            
            <n-card class="glass-card">
              <div class="setting-item">
                <div class="item-label">
                  <span>启用主动问候</span>
                </div>
                <n-switch v-model:value="settings.proactiveEnabled" />
              </div>
              
              <div class="setting-item">
                <div class="item-label">不活跃阈值 (分钟)</div>
                <n-input-number 
                  v-model:value="settings.inactiveThresholdMinutes" 
                  :min="1" 
                  :max="1440"
                  placeholder="用户不活跃多少分钟后触发问候"
                  style="width: 100%"
                />
                <div class="item-hint">用户不活跃超过此时间后，系统将考虑发送问候</div>
              </div>
              
              <div class="setting-item">
                <div class="item-label">问候冷却时间 (秒)</div>
                <n-input-number 
                  v-model:value="settings.greetingCooldownSeconds" 
                  :min="60" 
                  :max="86400"
                  placeholder="两次问候之间的最小间隔"
                  style="width: 100%"
                />
                <div class="item-hint">同一用户两次问候之间的最小间隔时间</div>
              </div>
            </n-card>

            <div class="section-header mt-8">
              <n-icon :component="Send" />
              <h2>测试问候</h2>
            </div>

            <n-card class="glass-card">
              <div class="test-section">
                <n-button 
                  type="primary" 
                  :loading="testingGreeting" 
                  @click="testProactiveGreeting"
                >
                  <template #icon><n-icon :component="Send" /></template>
                  测试生成问候
                </n-button>
                
                <div v-if="testGreetingResult" class="test-result">
                  <div class="result-label">生成的问候:</div>
                  <div class="result-content">{{ testGreetingResult }}</div>
                </div>
              </div>
            </n-card>

            <div class="save-section">
              <n-button type="primary" size="large" @click="handleSave">
                <template #icon><n-icon :component="Zap" /></template>
                保存问候配置
              </n-button>
            </div>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="local-tools" tab="本地工具">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Wrench" />
              <h2>本地工具管理</h2>
            </div>
            
            <div class="tools-grid">
              <n-card v-for="tool in localTools" :key="tool.name" class="glass-card tool-card">
                <div class="tool-header">
                  <div class="tool-info">
                    <div class="tool-name">{{ tool.displayName }}</div>
                    <div class="tool-id">@{{ tool.name }}</div>
                  </div>
                  <n-switch v-model:value="tool.enabled" />
                </div>
                <div class="tool-prompt">
                  <div class="item-label">工具提示词 (Prompt)</div>
                  <n-input 
                    v-model:value="tool.prompt" 
                    type="textarea" 
                    :rows="4" 
                    placeholder="定义工具的调用规则和描述..." 
                    :disabled="!tool.enabled"
                  />
                </div>
              </n-card>
            </div>

            <div class="save-section">
              <n-button type="primary" size="large" @click="saveLocalTools" :loading="loadingLocalTools">
                <template #icon><n-icon :component="Zap" /></template>
                保存工具配置
              </n-button>
            </div>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="asr" tab="语音识别">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Mic" />
              <h2>ASR 语音识别配置</h2>
            </div>
            
            <n-card class="glass-card">
              <div class="setting-item">
                <div class="item-label">
                  <span class="label-text">启用语音识别</span>
                </div>
                <n-switch v-model:value="asrSettings.enabled" />
              </div>

              <div class="setting-item mt-4">
                <div class="item-label">
                  <span class="label-text">ASR 服务地址</span>
                </div>
                <n-input 
                  v-model:value="asrSettings.url" 
                  placeholder="http://localhost:50001" 
                  size="large"
                  :disabled="!asrSettings.enabled"
                />
                <div class="item-hint">SenseVoice ASR 服务地址，默认端口 50001</div>
              </div>

              <div class="setting-item mt-4">
                <div class="item-label">
                  <span class="label-text">VAD 灵敏度</span>
                </div>
                <n-input-number 
                  v-model:value="asrSettings.sensitivity" 
                  :min="0" 
                  :max="1" 
                  :step="0.1"
                  :disabled="!asrSettings.enabled"
                />
                <div class="item-hint">语音活动检测灵敏度，值越高越灵敏 (0-1)</div>
              </div>
            </n-card>

            <div class="save-section">
              <n-button type="primary" size="large" @click="saveAsrSettings" :loading="savingAsr">
                <template #icon><n-icon :component="Zap" /></template>
                保存 ASR 配置
              </n-button>
            </div>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="mcp" tab="MCP 插件">
        <McpSettings />
      </n-tab-pane>
    </n-tabs>

    <n-modal 
      v-model:show="showAgentModal" 
      preset="card" 
      :title="editingAgent ? '编辑智能体' : '创建智能体'" 
      class="agent-modal"
      :style="{ width: '70%', minWidth: '600px', maxWidth: '900px' }"
    >
      <n-form label-placement="top">
        <n-grid :cols="2" :x-gap="16">
          <n-grid-item>
            <n-form-item label="名称 (唯一标识)">
              <n-input v-model:value="agentForm.name" placeholder="lingshu" :disabled="!!editingAgent" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="显示名称">
              <n-input v-model:value="agentForm.displayName" placeholder="灵枢" />
            </n-form-item>
          </n-grid-item>
        </n-grid>

        <n-grid :cols="2" :x-gap="16">
          <n-grid-item>
            <n-form-item label="头像">
              <div class="avatar-selector">
                <div 
                  v-for="av in avatarOptions" 
                  :key="av.icon" 
                  class="avatar-option"
                  :class="{ active: agentForm.avatar === av.icon }"
                  @click="agentForm.avatar = av.icon"
                >
                  <component :is="av.component" :size="24" />
                </div>
              </div>
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="主题色">
              <div class="color-selector">
                <span 
                  v-for="c in colorOptions" 
                  :key="c" 
                  class="color-option"
                  :style="{ background: c }"
                  :class="{ active: agentForm.color === c }"
                  @click="agentForm.color = c"
                ></span>
              </div>
            </n-form-item>
          </n-grid-item>
        </n-grid>

        <n-divider style="margin: 8px 0 16px;">提示词配置</n-divider>

        <n-tabs type="line" animated class="prompt-tabs">
          <n-tab-pane name="system" tab="系统提示词">
            <div class="prompt-hint">定义智能体的核心身份、使命和性格特征</div>
            <n-input v-model:value="agentForm.systemPrompt" type="textarea" :rows="8" placeholder="定义智能体的角色和行为..." />
          </n-tab-pane>
          
          <n-tab-pane name="fact" tab="事实提取">
            <div class="prompt-hint">定义如何从对话中提取和更新用户记忆</div>
            <n-input v-model:value="agentForm.factExtractionPrompt" type="textarea" :rows="8" placeholder="定义如何从对话中提取记忆..." />
          </n-tab-pane>
          
          <n-tab-pane name="behavior" tab="行为原则">
            <div class="prompt-hint">定义智能体的行为规范和交互风格</div>
            <n-input v-model:value="agentForm.behaviorPrinciples" type="textarea" :rows="8" placeholder="定义行为原则..." />
          </n-tab-pane>
          
          <n-tab-pane name="decision" tab="决策机制">
            <div class="prompt-hint">定义智能体的自主决策逻辑和意识循环</div>
            <n-input v-model:value="agentForm.decisionMechanism" type="textarea" :rows="8" placeholder="定义决策机制..." />
          </n-tab-pane>
          
          <n-tab-pane name="tool" tab="工具调用">
            <div class="prompt-hint">定义智能体如何感知和操作外部工具</div>
            <n-input v-model:value="agentForm.toolCallRules" type="textarea" :rows="8" placeholder="定义工具调用规则..." />
          </n-tab-pane>
          
          <n-tab-pane name="emotional" tab="情感策略">
            <div class="prompt-hint">定义智能体的情感陪伴和共情逻辑</div>
            <n-input v-model:value="agentForm.emotionalStrategy" type="textarea" :rows="8" placeholder="定义情感陪伴策略..." />
          </n-tab-pane>
          
          <n-tab-pane name="greeting" tab="问候触发">
            <div class="prompt-hint">定义主动问候的触发条件和时机</div>
            <n-input v-model:value="agentForm.greetingTriggers" type="textarea" :rows="8" placeholder="定义问候触发条件..." />
          </n-tab-pane>
          
          <n-tab-pane name="hidden" tab="隐性规则">
            <div class="prompt-hint">定义智能体必须遵守的隐性边界规则</div>
            <n-input v-model:value="agentForm.hiddenRules" type="textarea" :rows="8" placeholder="定义隐性规则..." />
          </n-tab-pane>
        </n-tabs>

        <n-form-item label="启用状态" style="margin-top: 16px;">
          <n-switch v-model:value="agentForm.isActive" />
        </n-form-item>
      </n-form>

      <template #footer>
        <div class="modal-footer">
          <n-button @click="showAgentModal = false">取消</n-button>
          <n-button type="primary" @click="saveAgent">保存</n-button>
        </div>
      </template>
    </n-modal>

    <ThemeModal v-model:open="showThemeModal" />
  </div>
</template>

<style scoped>
.settings-view {
  padding: 24px 32px;
  max-width: 1200px;
  margin: 0 auto;
  animation: fadeIn 0.6s cubic-bezier(0.23, 1, 0.32, 1);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.settings-header {
  margin-bottom: 16px;
  border-bottom: 1px solid var(--color-outline);
  padding-bottom: 12px;
}

.page-title {
  font-size: 22px;
  font-weight: 800;
  margin: 0 0 6px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: linear-gradient(135deg, var(--color-text), var(--color-primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.page-subtitle {
  color: var(--color-text-dim);
  font-size: 13px;
}

.settings-tabs {
  background: var(--color-surface);
  border-radius: 16px;
  padding: 12px;
}

.tab-content {
  padding: 12px 0;
}

.settings-section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  color: var(--color-text);
}

.section-header h2 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  flex: 1;
}

.create-btn {
  margin-left: auto;
}

.glass-card {
  background: var(--color-glass-bg) !important;
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: 16px !important;
}

.source-selector {
  margin-bottom: 20px;
}

.radio-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setting-item {
  margin-bottom: 16px;
}

.item-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dim);
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dual-fields {
  display: flex;
  gap: 16px;
}

.flex-1 { flex: 1; }
.mt-8 { margin-top: 24px; }

.save-section {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.agents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.agent-card {
  padding: 16px;
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.agent-avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.agent-info {
  flex: 1;
}

.agent-name {
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-id {
  font-size: 12px;
  color: var(--color-text-dim);
}

.agent-prompt-preview {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 12px;
  line-height: 1.5;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 16px;
}

.tool-card {
  padding: 16px;
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.tool-info {
  flex: 1;
}

.tool-name {
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 4px;
}

.tool-id {
  font-size: 12px;
  color: var(--color-text-dim);
}

.tool-prompt {
  margin-top: 12px;
}

.agent-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.agent-modal {
  :deep(.n-card) {
    background: var(--color-background) !important;
    border-radius: 16px !important;
    backdrop-filter: none !important;
    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  }
  
  :deep(.n-card-header) {
    padding: 20px 24px;
    border-bottom: 1px solid var(--color-outline);
  }
  
  :deep(.n-card__content) {
    padding: 24px;
  }
  
  :deep(.n-card__footer) {
    padding: 16px 24px;
    border-top: 1px solid var(--color-outline);
  }
}

:deep(.n-modal-mask) {
  backdrop-filter: blur(4px);
}

.avatar-selector, .color-selector {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.avatar-option {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;
  color: var(--color-text-dim);
  background: rgba(0, 0, 0, 0.05);
}

.avatar-option:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-dim);
  color: var(--color-primary);
}

.avatar-option.active {
  border-color: var(--color-primary);
  background: var(--color-primary-dim);
  color: var(--color-primary);
  box-shadow: 0 0 15px var(--color-primary-dim);
}

.color-option {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.color-option:hover, .color-option.active {
  border-color: var(--color-text);
  transform: scale(1.1);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.n-input), :deep(.n-select .n-base-selection), :deep(.n-textarea) {
  --n-border-radius: 12px !important;
  background-color: rgba(0,0,0,0.05) !important;
}

.item-hint {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-top: 4px;
}

.test-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.test-result {
  padding: 16px;
  background: var(--color-glass-bg);
  border-radius: 12px;
  border: 1px solid var(--color-glass-border);
}

.result-label {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 8px;
}

.result-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text);
}

.prompt-tabs {
  background: var(--color-glass-bg);
  border-radius: 12px;
  padding: 12px;
  border: 1px solid var(--color-glass-border);
}

.prompt-tabs :deep(.n-tabs-nav) {
  padding: 0 8px;
}

.prompt-tabs :deep(.n-tab-pane) {
  padding: 12px 0 0;
}

.prompt-hint {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 8px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 8px;
  border-left: 3px solid var(--color-primary);
}

/* 主题选择器样式 */
.theme-selector {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.theme-preview-card {
  flex: 1;
  min-width: 280px;
  max-width: 400px;
  padding: 20px;
  background: var(--color-glass-bg);
  border: 1px solid var(--color-outline);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.theme-preview-card:hover {
  border-color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px var(--color-primary-dim);
}

.preview-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.preview-info {
  flex: 1;
}

.current-theme-label {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 4px;
}

.current-theme-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
</style>
