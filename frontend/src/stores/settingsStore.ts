import { ref } from 'vue'
import { getFullUrl } from '@/utils/request'

export interface SystemSettings {
  source: string
  model: string
  baseUrl: string
  apiKey: string
  ttsBaseUrl: string
  ttsApiKey: string
  ttsDefaultVoice: string
  ttsDefaultSpeed: number
  ttsDefaultFormat: string
  ttsEnabled: boolean
  enableThinking: boolean
}

export interface AsrSettings {
  enabled: boolean
  url: string
  sensitivity: number
}

const settings = ref<SystemSettings>({
  source: 'openai',
  model: '',
  baseUrl: '',
  apiKey: '',
  ttsBaseUrl: 'http://localhost:5050',
  ttsApiKey: '',
  ttsDefaultVoice: 'alloy',
  ttsDefaultSpeed: 1.0,
  ttsDefaultFormat: 'mp3',
  ttsEnabled: false,
  enableThinking: false
})

const asrSettings = ref<AsrSettings>({
  enabled: false,
  url: 'http://localhost:50001',
  sensitivity: 0.5
})

const isLoaded = ref(false)

async function fetchSettings() {
  try {
    const res = await fetch(getFullUrl('/api/settings'))
    if (res.ok) {
      const data = await res.json()
      settings.value = {
        source: data.source || 'openai',
        model: data.chatModel || '',
        baseUrl: data.baseUrl || '',
        apiKey: data.apiKey || '',
        ttsBaseUrl: data.ttsBaseUrl || 'http://localhost:5050',
        ttsApiKey: data.ttsApiKey || '',
        ttsDefaultVoice: data.ttsDefaultVoice || 'alloy',
        ttsDefaultSpeed: data.ttsDefaultSpeed || 1.0,
        ttsDefaultFormat: data.ttsDefaultFormat || 'mp3',
        ttsEnabled: data.ttsEnabled ?? false,
        enableThinking: data.enableThinking ?? false
      }
      isLoaded.value = true
    }
  } catch (err) {
    console.error('Failed to fetch settings', err)
  }
}

async function fetchAsrSettings() {
  try {
    const res = await fetch(getFullUrl('/api/settings/asr'))
    if (res.ok) {
      const data = await res.json()
      asrSettings.value = {
        enabled: data.enabled ?? false,
        url: data.url ?? 'http://localhost:50001',
        sensitivity: data.sensitivity ?? 0.5
      }
    }
  } catch (err) {
    console.error('Failed to fetch ASR settings', err)
  }
}

async function saveSettings() {
  try {
    await fetch(getFullUrl('/api/settings'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        source: settings.value.source,
        chatModel: settings.value.model,
        baseUrl: settings.value.baseUrl,
        apiKey: settings.value.apiKey,
        ttsBaseUrl: settings.value.ttsBaseUrl,
        ttsApiKey: settings.value.ttsApiKey,
        ttsDefaultVoice: settings.value.ttsDefaultVoice,
        ttsDefaultSpeed: settings.value.ttsDefaultSpeed,
        ttsDefaultFormat: settings.value.ttsDefaultFormat,
        ttsEnabled: settings.value.ttsEnabled,
        enableThinking: settings.value.enableThinking
      })
    })
  } catch (err) {
    console.error('Failed to save settings', err)
  }
}

async function saveAsrSettings() {
  try {
    await fetch('/api/settings/asr', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(asrSettings.value)
    })
  } catch (err) {
    console.error('Failed to save ASR settings', err)
  }
}

function toggleThinking() {
  settings.value.enableThinking = !settings.value.enableThinking
}

export function useSettings() {
  return {
    settings,
    asrSettings,
    isLoaded,
    fetchSettings,
    fetchAsrSettings,
    saveSettings,
    saveAsrSettings,
    toggleThinking
  }
}
