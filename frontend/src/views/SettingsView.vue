<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { getFullUrl } from '@/utils/request'
import {
  NInput, NSelect, NButton, NIcon, NRadioGroup, NRadioButton,
  NCard, NGrid, NGridItem, useMessage, NTabs, NTabPane,
  NTag, NSwitch, NPopconfirm, NModal, NForm, NFormItem,
  NInputNumber, NDivider, NCollapse, NCollapseItem
} from 'naive-ui'
import {
  RefreshCw, Settings, Cpu, Globe, Activity, Zap, Plus,
  Trash2, Edit, Star, Users, Bell, Send, Brain, Palette, Mic,
  Bot, Gem, Rocket, Sparkles, Volume2, MessageCircle, Info,
  Cloud, Flame, Wind
} from 'lucide-vue-next'
import McpSettings from '@/components/McpSettings.vue'
import ThemeModal from '@/components/common/ThemeModal.vue'
import { useThemeStore } from '@/stores/themeStore'

const message = useMessage()
const activeTab = ref('basic')
const showThemeModal = ref(false)
const themeStore = useThemeStore()
const animationEffect = useLocalStorage('lingshu-animation-effect', 'off')

const settings = ref({
  source: '',
  model: '',
  baseUrl: '',
  apiKey: '',
  embedSource: 'ollama',
  embedModel: '',
  embedBaseUrl: 'http://localhost:11434',
  embedApiKey: '',
  memoryModelSource: '',
  memoryModel: '',
  memoryModelBaseUrl: '',
  memoryModelApiKey: '',
  proactiveEnabled: true,
  inactiveThresholdMinutes: 5,
  greetingCooldownSeconds: 300,
  inactiveCheckIntervalMs: 3600000,
  ttsBaseUrl: 'http://localhost:5050',
  ttsApiKey: '',
  ttsDefaultVoice: 'alloy',
  ttsDefaultSpeed: 1.0,
  ttsDefaultFormat: 'mp3',
  ttsDefaultSeed: -1,
  enableThinking: false,
})

const chatModelOptions = ref<{label: string, value: string}[]>([])
const embedModelOptions = ref<{label: string, value: string}[]>([])
const memoryModelOptions = ref<{label: string, value: string}[]>([])
const loadingChatModels = ref(false)
const loadingEmbedModels = ref(false)
const loadingMemoryModels = ref(false)

interface SkillResource {
  relativePath: string
}

interface SkillItem {
  name: string
  description: string
  basePath: string
  resourceCount: number
  scriptCount?: number
  resources: SkillResource[]
  scripts?: SkillResource[]
}

const skills = ref<SkillItem[]>([])
const skillsPath = ref('.lingshu/skills')
const skillsError = ref('')
const loadingSkills = ref(false)
const showSkillsHelp = ref(false)
const lastSkillsRefreshAt = ref('')

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

const getRandomAvatar = () => {
  const randomIndex = Math.floor(Math.random() * avatarOptions.length)
  return avatarOptions[randomIndex].icon
}

const isCustomAvatar = (avatar: string) => {
  return avatar && (avatar.startsWith('data:image/') || avatar.startsWith('http'))
}

const fileInput = ref<HTMLInputElement | null>(null)

const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (file.size > 512 * 1024) { // 限制 512KB
    message.error('图片大小不能超过 512KB')
    return
  }

  const reader = new FileReader()
  reader.onload = (e) => {
    const base64 = e.target?.result as string
    agentForm.value.avatar = base64
  }
  reader.readAsDataURL(file)
}

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
  avatar: getRandomAvatar(),
  color: '#3b82f6',
  isActive: true
})

const asrSettings = ref({
  enabled: false,
  url: 'http://localhost:50001',
  sensitivity: 0.5
})
const savingAsr = ref(false)

const voiceOptions = ref([
  { label: 'Alloy (中性)', value: 'alloy' },
  { label: 'Echo (男性)', value: 'echo' },
  { label: 'Fable (英式)', value: 'fable' },
  { label: 'Onyx (低沉)', value: 'onyx' },
  { label: 'Nova (女性)', value: 'nova' },
  { label: 'Shimmer (柔和)', value: 'shimmer' }
])

const loadingVoices = ref(false)

const formatOptions = [
  { label: 'MP3', value: 'mp3' },
  { label: 'OPUS', value: 'opus' },
  { label: 'AAC', value: 'aac' },
  { label: 'FLAC', value: 'flac' },
  { label: 'WAV', value: 'wav' }
]

const wechatBotAccounts = ref<any[]>([])
const qrCodeUrl = ref('')
const qrcodeId = ref('')
const currentAccountId = ref('')
const pollingStatus = ref(false)
let pollingTimer: any = null
let authWindow: Window | null = null
let authSuccessHandled = false

async function fetchWechatBotAccounts() {
  try {
    const res = await fetch(getFullUrl('/api/settings/wechat-bot/accounts'))
    wechatBotAccounts.value = await res.json()
  } catch (err) {
    console.error('Failed to fetch WeChat Bot accounts', err)
  }
}

async function getWechatQrCode() {
  try {
    const res = await fetch(getFullUrl('/api/settings/wechat-bot/qrcode'), { method: 'POST' })
    const data = await res.json()
    qrCodeUrl.value = data.qrcode_img_content || ''
    qrcodeId.value = data.qrcode || ''
    currentAccountId.value = ''
    if (qrcodeId.value) {
      authSuccessHandled = false
      startPollingStatus()
      if (qrCodeUrl.value) {
        authWindow = window.open(qrCodeUrl.value, 'wechat-auth', 'width=500,height=700,scrollbars=yes,resizable=yes')
      }
    }
  } catch (err) {
    message.error('获取授权链接失败')
  }
}

function closeAuthWindow() {
  if (authWindow && !authWindow.closed) {
    authWindow.close()
    authWindow = null
  }
}

function startPollingStatus() {
  pollingStatus.value = true
  if (pollingTimer) clearInterval(pollingTimer)
  pollingTimer = setInterval(async () => {
    try {
      const res = await fetch(getFullUrl(`/api/settings/wechat-bot/status?qrcode=${qrcodeId.value}`))
      const data = await res.json()
      if (data && data.errcode === -14) {
        clearInterval(pollingTimer)
        pollingStatus.value = false
        closeAuthWindow()
        message.warning('会话已过期，请重新授权')
        fetchWechatBotAccounts()
        return
      }
      if (data && data.status === 'confirmed' && !authSuccessHandled) {
        authSuccessHandled = true
        clearInterval(pollingTimer)
        pollingStatus.value = false
        closeAuthWindow()
        message.success('微信扫码授权成功！')
        fetchWechatBotAccounts()
      } else if (data && data.status === 'expired') {
        clearInterval(pollingTimer)
        pollingStatus.value = false
        closeAuthWindow()
        message.warning('二维码已过期，请重新获取')
      }
    } catch (err) {
      console.error('Polling status error', err)
    }
  }, 2000)
}

async function removeWechatBotAccount(accountId: string) {
  try {
    await fetch(getFullUrl(`/api/settings/wechat-bot/accounts/${accountId}`), { method: 'DELETE' })
    message.success('账户已删除')
    await fetchWechatBotAccounts()
  } catch (err) {
    message.error('删除账户失败')
  }
}

async function fetchSkills() {
  loadingSkills.value = true
  skillsError.value = ''
  try {
    const res = await fetch(getFullUrl('/api/settings/skills'))
    const data = await res.json()
    skills.value = Array.isArray(data.skills) ? data.skills : []
    skillsPath.value = data.path || '.lingshu/skills'
    lastSkillsRefreshAt.value = new Date().toLocaleString()
    if (data.error) {
      skillsError.value = data.error
    }
  } catch (err) {
    skills.value = []
    skillsError.value = '获取 Skills 列表失败'
  } finally {
    loadingSkills.value = false
  }
}

onMounted(() => {
  fetchSettings()
  fetchAgents()
  fetchAsrSettings()
  fetchWechatBotAccounts()
  fetchSkills()
})

onUnmounted(() => {
  if (pollingTimer) clearInterval(pollingTimer)
  closeAuthWindow()
})

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
    const res = await fetch(getFullUrl(`/api/chat/models?${params.toString()}`))
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

async function fetchMemoryModels(silent = false) {
  const baseUrl = settings.value.memoryModelBaseUrl || settings.value.baseUrl
  const source = settings.value.memoryModelSource || settings.value.source
  if (!baseUrl || !source) return
  loadingMemoryModels.value = true
  try {
    const params = new URLSearchParams({
      source: source,
      baseUrl: baseUrl,
      apiKey: settings.value.memoryModelApiKey || settings.value.apiKey,
    })
    const res = await fetch(getFullUrl(`/api/chat/models?${params.toString()}`))
    const models = await res.json()
    memoryModelOptions.value = models.map((m: string) => ({ label: m, value: m }))

    if (memoryModelOptions.value.length > 0 && !memoryModelOptions.value.find(o => o.value === settings.value.memoryModel)) {
      settings.value.memoryModel = memoryModelOptions.value[0].value
    }
    if (!silent) message.success('记忆模型列表已更新')
  } catch (err) {
    if (!silent) message.error('无法连接到记忆模型服务')
    memoryModelOptions.value = []
  } finally {
    loadingMemoryModels.value = false
  }
}

async function fetchVoices(silent = false) {
  if (!settings.value.ttsBaseUrl) return
  loadingVoices.value = true
  try {
    const params = new URLSearchParams({
      baseUrl: settings.value.ttsBaseUrl,
      apiKey: settings.value.ttsApiKey || '',
    })
    const res = await fetch(getFullUrl(`/api/tts/voices?${params.toString()}`))
    if (!res.ok) throw new Error('Failed to fetch voices')
    const data = await res.json()

    let voicesList: any[] = []

    // 支持 OpenAI 标准格式 (doubaotts 等)
    if (data && Array.isArray(data.data)) {
      voicesList = data.data
    }
    // 支持 edge-tts 自定义格式
    else if (data && Array.isArray(data.voices)) {
      voicesList = data.voices
    }
    // 兼容直接返回数组的情况
    else if (Array.isArray(data)) {
      voicesList = data
    }

    if (voicesList.length > 0) {
      voiceOptions.value = voicesList.map((v: any) => {
        // edge-tts 只有 name，豆包有 id 和 name
        const value = v.id || v.name
        const label = v.name || v.id
        return { label: label, value: value }
      })

      if (voiceOptions.value.length > 0 && !voiceOptions.value.find((o: any) => o.value === settings.value.ttsDefaultVoice)) {
        settings.value.ttsDefaultVoice = voiceOptions.value[0].value
      }
      if (!silent) message.success('音色列表已更新')
    } else {
      throw new Error('Invalid voices format or empty list')
    }
    } catch (err) {    if (!silent) message.error('无法获取音色列表，请检查服务地址或 Token 是否有效')
  } finally {
    loadingVoices.value = false
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

function handleMemoryModelSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.memoryModelBaseUrl = 'http://localhost:11434'
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

watch(
  [() => settings.value.memoryModelSource, () => settings.value.memoryModelBaseUrl, () => settings.value.memoryModelApiKey],
  () => fetchMemoryModels(true)
)

async function fetchSettings() {
  try {
    const res = await fetch(getFullUrl('/api/settings'))
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
      memoryModelSource: data.memoryModelSource || '',
      memoryModel: data.memoryModel || '',
      memoryModelBaseUrl: data.memoryModelBaseUrl || '',
      memoryModelApiKey: data.memoryModelApiKey || '',
      proactiveEnabled: data.proactiveEnabled ?? true,
      inactiveThresholdMinutes: data.inactiveThresholdMinutes ?? 5,
      greetingCooldownSeconds: data.greetingCooldownSeconds ?? 300,
      inactiveCheckIntervalMs: data.inactiveCheckIntervalMs ?? 3600000,
      ttsBaseUrl: data.ttsBaseUrl || 'http://localhost:5050',
      ttsApiKey: data.ttsApiKey || '',
      ttsDefaultVoice: data.ttsDefaultVoice || 'alloy',
      ttsDefaultSpeed: data.ttsDefaultSpeed || 1.0,
      ttsDefaultFormat: data.ttsDefaultFormat || 'mp3',
      ttsDefaultSeed: data.ttsDefaultSeed ?? -1,
      enableThinking: data.enableThinking ?? false,
    }
    fetchChatModels(true)
    fetchEmbedModels(true)
    fetchMemoryModels(true)
    fetchVoices(true)
  } catch (err) {
    console.error('Failed to fetch settings', err)
  }
}

watch(
  [() => settings.value.ttsBaseUrl, () => settings.value.ttsApiKey],
  () => fetchVoices(true)
)

async function fetchAgents() {
  try {
    const res = await fetch(getFullUrl('/api/agents'))
    const data = await res.json()
    console.log('[SettingsView] 获取智能体列表:', data.length, '个智能体')
    agents.value = data
  } catch (err) {
    console.error('Failed to fetch agents', err)
  }
}

async function fetchAsrSettings() {
  try {
    const res = await fetch(getFullUrl('/api/settings/asr'))
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
    await fetch(getFullUrl('/api/settings/asr'), {
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
  fetchAsrSettings()
})

const handleSave = async () => {
  try {
    await fetch(getFullUrl('/api/settings'), {
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
        memoryModelSource: settings.value.memoryModelSource,
        memoryModel: settings.value.memoryModel,
        memoryModelBaseUrl: settings.value.memoryModelBaseUrl,
        memoryModelApiKey: settings.value.memoryModelApiKey,
        proactiveEnabled: settings.value.proactiveEnabled,
        inactiveThresholdMinutes: settings.value.inactiveThresholdMinutes,
        greetingCooldownSeconds: settings.value.greetingCooldownSeconds,
        inactiveCheckIntervalMs: settings.value.inactiveCheckIntervalMs,
        ttsBaseUrl: settings.value.ttsBaseUrl,
        ttsApiKey: settings.value.ttsApiKey,
        ttsDefaultVoice: settings.value.ttsDefaultVoice,
        ttsDefaultSpeed: settings.value.ttsDefaultSpeed,
        ttsDefaultFormat: settings.value.ttsDefaultFormat,
        ttsDefaultSeed: settings.value.ttsDefaultSeed,
        enableThinking: settings.value.enableThinking,
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
    const res = await fetch(getFullUrl('/api/chat/proactive/test-greeting'))
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
    const res = await fetch(getFullUrl('/api/agents/defaults'))
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
      avatar: (defaults.avatar && avatarOptions.some(opt => opt.icon === defaults.avatar)) ? defaults.avatar : getRandomAvatar(),
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
    const url = editingAgent.value ? getFullUrl(`/api/agents/${editingAgent.value.id}`) : getFullUrl('/api/agents')
    const method = editingAgent.value ? 'PUT' : 'POST'

    console.log('[SettingsView] 保存智能体:', editingAgent.value ? '更新' : '创建', editingAgent.value?.name)
    console.log('[SettingsView] systemPrompt 长度:', agentForm.value.systemPrompt?.length)

    await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(agentForm.value)
    })

    message.success(editingAgent.value ? '智能体已更新' : '智能体已创建')
    showAgentModal.value = false
    console.log('[SettingsView] 保存成功，刷新智能体列表...')
    fetchAgents()
  } catch (err) {
    message.error('保存失败')
  }
}

async function deleteAgent(id: number) {
  try {
    await fetch(getFullUrl(`/api/agents/${id}`), { method: 'DELETE' })
    message.success('智能体已删除')
    fetchAgents()
  } catch (err) {
    message.error('删除失败')
  }
}

async function setDefaultAgent(id: number) {
  try {
    await fetch(getFullUrl(`/api/agents/${id}/set-default`), { method: 'POST' })
    message.success('已设为默认智能体')
    fetchAgents()
  } catch (err) {
    message.error('操作失败')
  }
}
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

            <n-card class="glass-card mt-4">
              <div class="setting-item">
                <div class="item-label">
                  <span class="label-text">背景特效</span>
                  <span class="item-hint">为界面增加动态氛围感</span>
                </div>
                <n-radio-group v-model:value="animationEffect" size="medium">
                  <n-radio-button value="off">
                    <div class="flex items-center gap-1">
                      <n-icon :component="Rocket" :size="14" v-if="animationEffect === 'off'" />
                      <span>关闭</span>
                    </div>
                  </n-radio-button>
                  <n-radio-button value="starfield">
                    <div class="flex items-center gap-1">
                      <n-icon :component="Sparkles" :size="14" v-if="animationEffect === 'starfield'" />
                      <span>星辰</span>
                    </div>
                  </n-radio-button>
                  <n-radio-button value="rain">
                    <div class="flex items-center gap-1">
                      <n-icon :component="Zap" :size="14" v-if="animationEffect === 'rain'" />
                      <span>下雨</span>
                    </div>
                  </n-radio-button>
                  <n-radio-button value="aurora">
                    <div class="flex items-center gap-1">
                      <n-icon :component="Wind" :size="14" v-if="animationEffect === 'aurora'" />
                      <span>极光</span>
                    </div>
                  </n-radio-button>
                  <n-radio-button value="firefly">
                    <div class="flex items-center gap-1">
                      <n-icon :component="Flame" :size="14" v-if="animationEffect === 'firefly'" />
                      <span>萤火</span>
                    </div>
                  </n-radio-button>
                  <n-radio-button value="mist">
                    <div class="flex items-center gap-1">
                      <n-icon :component="Cloud" :size="14" v-if="animationEffect === 'mist'" />
                      <span>云海</span>
                    </div>
                  </n-radio-button>
                </n-radio-group>
              </div>
            </n-card>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="model" tab="模型配置">
        <div class="tab-content">
          <n-tabs type="segment" animated>
            <n-tab-pane name="llm" tab="对话模型 (LLM)">

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

                  <n-divider class="mt-4" />

                  <div class="setting-item">
                    <div class="item-label">
                      <span class="label-text">推理/思考模式</span>
                    </div>
                    <div class="switch-row">
                      <n-switch v-model:value="settings.enableThinking" />
                      <span class="switch-hint">开启后，支持推理的模型会展示思考过程（如 DeepSeek R1、Qwen3 等）</span>
                    </div>
                  </div>
                </n-card>

            </n-tab-pane>

            <n-tab-pane name="embedding" tab="向量模型 (Embedding)">

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

            </n-tab-pane>

            <n-tab-pane name="memoryModel" tab="记忆模型 (Memory)">

                <n-card class="glass-card">
                  <div class="source-selector">
                      <n-radio-group v-model:value="settings.memoryModelSource" size="large" @update:value="handleMemoryModelSourceChange">
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
                        <span class="label-text">记忆模型</span>
                        <n-button quaternary circle size="small" @click="fetchMemoryModels(false)" :loading="loadingMemoryModels">
                          <template #icon><n-icon :component="RefreshCw" /></template>
                        </n-button>
                      </div>
                      <n-select
                        v-model:value="settings.memoryModel"
                        :options="memoryModelOptions"
                        placeholder="选择记忆模型..."
                        size="large"
                        filterable
                        tag
                      />
                    </div>

                    <div class="dual-fields mt-4">
                      <div class="setting-item flex-1">
                        <div class="item-label">服务地址</div>
                        <n-input v-model:value="settings.memoryModelBaseUrl" placeholder="默认使用对话模型地址..." size="large" />
                      </div>
                      <div v-if="settings.memoryModelSource === 'openai'" class="setting-item flex-1">
                        <div class="item-label">API 密钥</div>
                        <n-input v-model:value="settings.memoryModelApiKey" type="password" show-password-on="click" placeholder="默认使用对话模型密钥..." size="large" />
                      </div>
                    </div>
                </n-card>

            </n-tab-pane>

            <n-tab-pane name="tts" tab="语音合成 (TTS)">

                <n-card class="glass-card">
                  <div class="setting-item">
                    <div class="item-label">
                      <span class="label-text">TTS 服务地址</span>
                    </div>
                    <n-input
                      v-model:value="settings.ttsBaseUrl"
                      placeholder="http://localhost:5050"
                      size="large"
                    />
                    <div class="item-hint">OpenAI 兼容的 TTS 服务地址，如 openai-edge-tts</div>
                  </div>

                  <div class="setting-item mt-4">
                    <div class="item-label">
                      <span class="label-text">API 密钥 (可选)</span>
                    </div>
                    <n-input
                      v-model:value="settings.ttsApiKey"
                      type="password"
                      show-password-on="click"
                      placeholder="如果服务需要认证..."
                      size="large"
                    />
                  </div>



                  <div class="setting-item mt-4">
                    <div class="item-label">
                      <span class="label-text">默认语音</span>
                      <n-button quaternary circle size="small" @click="fetchVoices(false)" :loading="loadingVoices">
                        <template #icon><n-icon :component="RefreshCw" /></template>
                      </n-button>
                    </div>
                    <n-select
                      v-model:value="settings.ttsDefaultVoice"
                      :options="voiceOptions"
                      placeholder="选择默认语音..."
                      size="large"
                      filterable
                      tag
                    />
                    <div class="item-hint">OpenAI 兼容语音选项，支持手动输入自定义音色名称</div>
                  </div>

                  <div class="dual-fields mt-4">
                    <div class="setting-item flex-1">
                      <div class="item-label">语速</div>
                      <n-input-number
                        v-model:value="settings.ttsDefaultSpeed"
                        :min="0.25"
                        :max="4.0"
                        :step="0.25"
                        size="large"
                      />
                      <div class="item-hint">0.25x - 4.0x</div>
                    </div>
                    <div class="setting-item flex-1">
                      <div class="item-label">输出格式</div>
                      <n-select
                        v-model:value="settings.ttsDefaultFormat"
                        :options="formatOptions"
                        placeholder="选择格式..."
                        size="large"
                      />
                    </div>
                  </div>

                  <n-collapse class="mt-4" :default-expanded-names="[]">
                    <n-collapse-item title="高级设置" name="advanced">
                      <div class="setting-item">
                        <div class="item-label">
                          <span class="label-text">随机种子 (Seed)</span>
                        </div>
                        <n-input-number
                          v-model:value="settings.ttsDefaultSeed"
                          :min="-1"
                          :step="1"
                          size="large"
                          placeholder="固定种子以复现语气，-1 为随机"
                        />
                        <div class="item-hint">固定种子可复现特定语气，设为 -1 则每次合成随机。</div>
                      </div>
                    </n-collapse-item>
                  </n-collapse>
                </n-card>

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
            <div class="section-header flex justify-end">
              <n-button type="primary" size="small" @click="openCreateAgent" class="create-btn">
                <template #icon><n-icon :component="Plus" /></template>
                创建智能体
              </n-button>
            </div>

            <div class="agents-grid">
              <n-card v-for="agent in agents" :key="agent.id" class="glass-card agent-card">
                <div class="agent-header">
                  <span class="agent-avatar" :style="{ background: agent.color || '#3b82f6' }">
                    <template v-if="isCustomAvatar(agent.avatar)">
                      <img :src="agent.avatar" class="avatar-image-content" />
                    </template>
                    <template v-else-if="agent.avatar === 'Bot'"><Bot :size="24" /></template>
                    <template v-else-if="agent.avatar === 'Brain'"><Brain :size="24" /></template>
                    <template v-else-if="agent.avatar === 'Sparkles'"><Sparkles :size="24" /></template>
                    <template v-else-if="agent.avatar === 'Cpu'"><Cpu :size="24" /></template>
                    <template v-else-if="agent.avatar === 'Gem'"><Gem :size="24" /></template>
                    <template v-else-if="agent.avatar === 'Rocket'"><Rocket :size="24" /></template>
                    <template v-else-if="agent.avatar === 'Zap'"><Zap :size="24" /></template>
                    <template v-else>{{ agent.avatar || '🤖' }}</template>
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

      <n-tab-pane name="skills" tab="Skills">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header flex justify-end">
              <div class="section-actions">
                <n-button quaternary circle size="small" @click="showSkillsHelp = true">
                  <template #icon><n-icon :component="Info" /></template>
                </n-button>
                <n-button type="primary" secondary size="small" :loading="loadingSkills" @click="fetchSkills">
                  <template #icon><n-icon :component="RefreshCw" /></template>
                  刷新
                </n-button>
              </div>
            </div>

            <n-card class="glass-card skills-card">
              <div class="setting-item">
                <div class="item-label">
                  <span class="label-text">Skills 目录</span>
                </div>
                <div class="item-hint">系统会从这个目录加载 Skills：<code>{{ skillsPath }}</code></div>
                <div class="item-hint" v-if="lastSkillsRefreshAt">上次刷新：{{ lastSkillsRefreshAt }}</div>
              </div>

              <div v-if="skillsError" class="skills-error">{{ skillsError }}</div>

              <div class="setting-item mt-4">
                <div class="item-label">
                  <span class="label-text">当前已加载</span>
                  <n-tag size="small" round type="success">{{ skills.length }} 个</n-tag>
                </div>
                <div v-if="loadingSkills" class="skills-loading">正在重新扫描 `.lingshu/skills` ...</div>
                <div v-else-if="skills.length === 0" class="empty-state compact">
                  <p>暂无已加载的 Skills</p>
                  <p class="hint">在 `.lingshu/skills` 下新增 `SKILL.md` 后，点击右上角刷新即可同步。</p>
                </div>
                <div v-else class="skills-grid">
                  <n-card v-for="skill in skills" :key="`${skill.basePath}-${skill.name}`" class="skill-card" size="small">
                    <div class="skill-card-header">
                      <div class="skill-title-block">
                        <div class="skill-name-row">
                          <div class="skill-name" :title="skill.name">{{ skill.name }}</div>
                          <n-tag size="small" round type="info" class="skill-count-tag">{{ skill.resourceCount }} resources</n-tag>
                        </div>
                        <div class="skill-base-path" :title="skill.basePath">
                          <span class="skill-base-label">Path</span>
                          <span class="skill-base-value">{{ skill.basePath }}</span>
                        </div>
                      </div>
                      <n-tag size="small" round type="warning" class="skill-script-tag">{{ skill.scriptCount || 0 }} scripts</n-tag>
                    </div>
                    <div class="skill-desc" :title="skill.description || '暂无描述'">
                      {{ skill.description || '暂无描述' }}
                    </div>
                    <div v-if="skill.resources?.length" class="skill-resources">
                      <div class="skill-subtitle">Resources</div>
                      <n-tag v-for="resource in skill.resources" :key="resource.relativePath" size="small" round>
                        {{ resource.relativePath }}
                      </n-tag>
                    </div>
                    <div v-if="skill.scripts?.length" class="skill-resources">
                      <div class="skill-subtitle">Scripts</div>
                      <n-tag v-for="script in skill.scripts" :key="script.relativePath" size="small" round type="success">
                        {{ script.relativePath }}
                      </n-tag>
                    </div>
                  </n-card>
                </div>
              </div>

              <div class="setting-item mt-4">
                <div class="item-label">
                  <span class="label-text">说明</span>
                </div>
                <div class="item-hint">
                  Skills 会在运行时按需加载；更详细的使用说明可以点右上角帮助图标查看。
                </div>
              </div>
            </n-card>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="asr" tab="语音识别">
        <div class="tab-content">
          <section class="settings-section">


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

      <n-tab-pane name="wechat" tab="微信 Bot (iLink)">
        <div class="tab-content">
          <section class="settings-section">


            <div class="wechat-bot-header">
              <p class="section-desc">支持多账户授权，每个微信用户独立记忆和对话上下文</p>
              <n-button type="primary" @click="getWechatQrCode" :disabled="pollingStatus">
                <template #icon><n-icon :component="Plus" /></template>
                扫码授权
              </n-button>
            </div>

            <div v-if="wechatBotAccounts.length === 0" class="empty-state">
              <n-icon :component="MessageCircle" :size="48" class="empty-icon" />
              <p>暂无授权账户</p>
              <p class="hint">点击上方"扫码授权"按钮开始授权</p>
            </div>

            <div v-else class="accounts-grid">
              <n-card v-for="account in wechatBotAccounts" :key="account.accountId" class="account-card" :class="{ 'account-active': account.status === 'confirmed' }">
                <div class="account-header">
                  <div class="account-status">
                    <span class="status-dot" :class="{
                      'status-success': account.status === 'confirmed',
                      'status-warning': account.status === 'wait' || account.status === 'scaned',
                      'status-error': account.status !== 'confirmed' && account.status !== 'wait' && account.status !== 'scaned'
                    }"></span>
                    <span class="status-text">{{ 
                      account.status === 'confirmed' ? '已授权' : 
                      (account.status === 'wait' || account.status === 'scaned' ? '等待扫码' : 
                      (account.status === 'session_timeout' ? '会话过期' : '未授权'))
                    }}</span>
                  </div>
                  <n-popconfirm @positive-click="removeWechatBotAccount(account.accountId)">
                    <template #trigger>
                      <n-button quaternary circle size="small" type="error">
                        <template #icon><n-icon :component="Trash2" :size="16" /></template>
                      </n-button>
                    </template>
                    确定要取消此账户的授权吗？
                  </n-popconfirm>
                </div>
                
                <div class="account-body">
                  <div class="account-info">
                    <span class="info-label">微信 ID</span>
                    <span class="info-value">{{ account.ilinkUserId || account.accountId?.substring(0, 12) + '...' }}</span>
                  </div>
                  <div class="account-info" v-if="account.botToken">
                    <span class="info-label">Token</span>
                    <span class="info-value token">{{ account.botToken }}</span>
                  </div>
                  <div class="account-info" v-if="account.lastLoginTime">
                    <span class="info-label">最后更新</span>
                    <span class="info-value">{{ new Date(account.lastLoginTime).toLocaleString() }}</span>
                  </div>
                </div>

                <div class="account-footer" v-if="account.status !== 'confirmed'">
                  <n-button 
                    size="small" 
                    type="primary"
                    block
                    @click="getWechatQrCode()" 
                    :loading="pollingStatus"
                  >
                    重新授权
                  </n-button>
                </div>
              </n-card>
            </div>

            <n-card class="glass-card polling-card" v-if="pollingStatus">
              <div class="polling-content">
                <n-icon :component="RefreshCw" :size="24" class="spin-icon" />
                <div class="polling-text">
                  <h4>正在等待扫码确认...</h4>
                  <p>授权页面已在新标签页打开</p>
                </div>
                <a :href="qrCodeUrl" target="_blank" class="manual-link">
                  手动打开授权页面
                </a>
              </div>
            </n-card>
          </section>
        </div>
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
                <div 
                  class="avatar-option upload-btn" 
                  :class="{ active: isCustomAvatar(agentForm.avatar) }"
                  @click="fileInput?.click()"
                >
                  <template v-if="isCustomAvatar(agentForm.avatar)">
                    <img :src="agentForm.avatar" class="avatar-image-content" />
                  </template>
                  <template v-else>
                    <n-icon :component="Plus" :size="24" />
                  </template>
                  <input 
                    type="file" 
                    ref="fileInput" 
                    style="display: none" 
                    accept="image/*" 
                    @change="handleFileUpload" 
                  />
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

    <n-modal
      v-model:show="showSkillsHelp"
      preset="card"
      title="Skills 使用说明"
      class="skills-help-modal"
      :style="{ width: '720px', maxWidth: '90vw' }"
    >
      <div class="skills-help-content">
        <p>把 Skill 放到 <code>.lingshu/skills</code> 下，系统会在每次聊天前重新扫描并自动加载。</p>
        <pre class="code-block">.lingshu/skills/
  travel-planner/
    SKILL.md
    resources/
  meeting-summarizer/
    SKILL.md</pre>
        <p>Skills 通过官方 <code>Skills.from(...).toolProvider()</code> 暴露 <code>activate_skill</code> 和 <code>read_skill_resource</code>。新增或修改文件后，点击刷新按钮即可让后台重新拉取列表。</p>
      </div>
    </n-modal>

    <ThemeModal v-model:open="showThemeModal" />
  </div>
</template>

<style scoped>
.settings-view {
  height: 100%;
  overflow-y: auto;
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
  background: rgba(10, 16, 28, 0.1);
  backdrop-filter: blur(4px) saturate(106%);
  -webkit-backdrop-filter: blur(4px) saturate(106%);
  border: 1px solid var(--color-glass-border);
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
  background: rgba(10, 16, 28, 0.12) !important;
  backdrop-filter: blur(6px) saturate(108%);
  -webkit-backdrop-filter: blur(6px) saturate(108%);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: 16px !important;
  padding: 14px !important;
}

.glass-card :deep(.n-card),
.glass-card :deep(.n-card__content),
.glass-card :deep(.n-card-header),
.glass-card :deep(.n-card__footer) {
  background: transparent !important;
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
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  letter-spacing: 0.01em;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.switch-hint {
  font-size: 12px;
  color: var(--color-text-dim);
  opacity: 0.7;
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
  color: white;
  overflow: hidden;
}

.avatar-image-content {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-option.upload-btn {
  border: 2px dashed var(--color-border);
  background: transparent;
  overflow: hidden;
  padding: 0;
}

.avatar-option.upload-btn.active {
  border-style: solid;
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
  background-color: rgba(10, 16, 28, 0.08) !important;
}

:deep(.n-input-wrapper),
:deep(.n-input__input),
:deep(.n-input__textarea),
:deep(.n-base-selection-label) {
  background: transparent !important;
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
  background: rgba(10, 16, 28, 0.1);
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
  background: rgba(10, 16, 28, 0.1);
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
  background: rgba(10, 16, 28, 0.1);
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

/* 微信 Bot 账户管理样式 */
.wechat-bot-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 20px;
  background: rgba(10, 16, 28, 0.1);
  border-radius: 12px;
  border: 1px solid var(--color-glass-border);
}

.section-desc {
  font-size: 14px;
  color: var(--color-text-dim);
  margin: 0;
}

.empty-state {
  text-align: center;
  padding: 48px 24px;
  background: rgba(10, 16, 28, 0.08);
  border-radius: 12px;
  border: 1px dashed var(--color-outline);
}

.empty-icon {
  color: var(--color-text-dim);
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-state p {
  margin: 0;
  color: var(--color-text-dim);
}

.empty-state .hint {
  font-size: 12px;
  margin-top: 8px;
}

.accounts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.account-card {
  background: rgba(10, 16, 28, 0.1) !important;
  border: 1px solid var(--color-glass-border) !important;
  border-radius: 12px !important;
  transition: all 0.3s ease;
}

.account-card:hover {
  border-color: var(--color-primary-dim) !important;
}

.account-card.account-active {
  border-color: rgba(34, 197, 94, 0.3) !important;
  background: rgba(34, 197, 94, 0.05) !important;
}

.account-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-outline);
}

.account-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.status-dot.status-success {
  background: #22c55e;
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
}

.status-dot.status-warning {
  background: #f59e0b;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.5);
}

.status-dot.status-error {
  background: #ef4444;
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.5);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.account-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.account-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 12px;
  color: var(--color-text-dim);
}

.info-value {
  font-size: 12px;
  color: var(--color-text);
  font-family: monospace;
}

.info-value.token {
  color: var(--color-primary);
}

.account-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-outline);
}

.polling-card {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(139, 92, 246, 0.1)) !important;
  border-color: rgba(59, 130, 246, 0.2) !important;
}

.polling-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px;
}

.spin-icon {
  color: var(--color-primary);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.polling-text {
  flex: 1;
}

.polling-text h4 {
  margin: 0 0 4px 0;
  font-size: 14px;
  color: var(--color-text);
}

.polling-text p {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-dim);
}

.manual-link {
  font-size: 12px;
  color: var(--color-primary);
  text-decoration: none;
  padding: 6px 12px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 6px;
  transition: all 0.2s;
}

.manual-link:hover {
  background: rgba(59, 130, 246, 0.2);
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.skills-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.skills-error {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
  font-size: 12px;
}

.skills-loading {
  font-size: 13px;
  color: var(--color-text-dim);
  padding: 8px 0;
}

.empty-state.compact {
  padding: 16px 0 4px;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}

.skill-card {
  position: relative;
  border-radius: 18px !important;
  border: 1px solid color-mix(in srgb, var(--color-outline) 72%, transparent) !important;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02)) !important;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.skill-card::before {
  content: '';
  position: absolute;
  inset: 0;
  height: 1px;
  border-radius: inherit;
  background: linear-gradient(90deg, color-mix(in srgb, var(--color-primary) 45%, transparent), transparent);
  pointer-events: none;
  opacity: 0.7;
}

.skill-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-outline)) !important;
  box-shadow: 0 16px 34px rgba(0, 0, 0, 0.14);
}

.skill-card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.skill-title-block {
  min-width: 0;
  flex: 1 1 auto;
}

.skill-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.skill-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.25;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-base-path {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--color-text-dim);
  min-width: 0;
  opacity: 0.86;
}

.skill-base-label {
  flex: 0 0 auto;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.skill-base-value {
  min-width: 0;
  font-family: monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-stats {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
  flex: 0 0 auto;
}

.skill-count-tag,
.skill-script-tag {
  margin-top: 2px;
}

.skill-desc {
  padding-top: 2px;
  font-size: 13px;
  color: var(--color-text-dim);
  line-height: 1.65;
  min-height: 44px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.skill-resources {
  margin-top: 12px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.skill-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-right: 2px;
  flex: 0 0 100%;
}

.skills-help-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--color-text);
}

.skills-help-content p {
  margin: 0;
  line-height: 1.7;
}

.skills-help-content .code-block {
  margin: 0;
}
</style>
