import { ref, computed } from 'vue'

export interface SystemSettings {
  source: string
  model: string
  baseUrl: string
  apiKey: string
  embedSource: string
  embedModel: string
  embedBaseUrl: string
  embedApiKey: string
  proactiveEnabled: boolean
  inactiveThresholdMinutes: number
  greetingCooldownSeconds: number
  inactiveCheckIntervalMs: number
}

const settings = ref<SystemSettings>({
  source: 'openai',
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
  inactiveCheckIntervalMs: 3600000
})

const isLoaded = ref(false)
const isLoading = ref(false)
const chatModelOptions = ref<{label: string, value: string}[]>([])
const embedModelOptions = ref<{label: string, value: string}[]>([])
const loadingChatModels = ref(false)
const loadingEmbedModels = ref(false)

let fetchChatModelsTimer: ReturnType<typeof setTimeout> | null = null
let fetchEmbedModelsTimer: ReturnType<typeof setTimeout> | null = null

async function fetchSettings(force = false) {
  if (isLoaded.value && !force) return
  if (isLoading.value) return
  
  isLoading.value = true
  try {
    const res = await fetch('/api/settings')
    if (res.ok) {
      const data = await res.json()
      settings.value = {
        source: data.source || 'openai',
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
        inactiveCheckIntervalMs: data.inactiveCheckIntervalMs ?? 3600000
      }
      isLoaded.value = true
    }
  } catch (err) {
    console.error('Failed to fetch settings', err)
  } finally {
    isLoading.value = false
  }
}

async function fetchChatModels(silent = false) {
  if (!settings.value.baseUrl || !settings.value.source) return
  
  if (fetchChatModelsTimer) {
    clearTimeout(fetchChatModelsTimer)
  }
  
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
  } catch (err) {
    console.error('Failed to fetch chat models', err)
    chatModelOptions.value = []
  } finally {
    loadingChatModels.value = false
  }
}

async function fetchEmbedModels(silent = false) {
  if (!settings.value.embedBaseUrl || !settings.value.embedSource) return
  
  if (fetchEmbedModelsTimer) {
    clearTimeout(fetchEmbedModelsTimer)
  }
  
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
  } catch (err) {
    console.error('Failed to fetch embed models', err)
    embedModelOptions.value = []
  } finally {
    loadingEmbedModels.value = false
  }
}

function debouncedFetchChatModels(delay = 300) {
  if (fetchChatModelsTimer) {
    clearTimeout(fetchChatModelsTimer)
  }
  fetchChatModelsTimer = setTimeout(() => {
    fetchChatModels(true)
  }, delay)
}

function debouncedFetchEmbedModels(delay = 300) {
  if (fetchEmbedModelsTimer) {
    clearTimeout(fetchEmbedModelsTimer)
  }
  fetchEmbedModelsTimer = setTimeout(() => {
    fetchEmbedModels(true)
  }, delay)
}

async function saveSettings() {
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
        inactiveCheckIntervalMs: settings.value.inactiveCheckIntervalMs
      })
    })
  } catch (err) {
    console.error('Failed to save settings', err)
  }
}

function handleSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.baseUrl = 'http://localhost:11434'
  } else if (newSource === 'openai') {
    settings.value.baseUrl = 'http://localhost:3000'
  }
  debouncedFetchChatModels()
}

function handleEmbedSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.embedBaseUrl = 'http://localhost:11434'
  }
  debouncedFetchEmbedModels()
}

export function useSettings() {
  return {
    settings,
    isLoaded,
    isLoading,
    chatModelOptions,
    embedModelOptions,
    loadingChatModels,
    loadingEmbedModels,
    fetchSettings,
    saveSettings,
    fetchChatModels,
    fetchEmbedModels,
    debouncedFetchChatModels,
    debouncedFetchEmbedModels,
    handleSourceChange,
    handleEmbedSourceChange
  }
}
