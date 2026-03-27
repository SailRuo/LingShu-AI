import { ref } from 'vue'

export interface SystemSettings {
  source: string
  model: string
  baseUrl: string
  apiKey: string
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
  apiKey: ''
})

const asrSettings = ref<AsrSettings>({
  enabled: false,
  url: 'http://localhost:50001',
  sensitivity: 0.5
})

const isLoaded = ref(false)

async function fetchSettings() {
  try {
    const res = await fetch('/api/settings')
    if (res.ok) {
      const data = await res.json()
      settings.value = {
        source: data.source || 'openai',
        model: data.chatModel || '',
        baseUrl: data.baseUrl || '',
        apiKey: data.apiKey || ''
      }
      isLoaded.value = true
    }
  } catch (err) {
    console.error('Failed to fetch settings', err)
  }
}

async function fetchAsrSettings() {
  try {
    const res = await fetch('/api/settings/asr')
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

export function useSettings() {
  return {
    settings,
    asrSettings,
    isLoaded,
    fetchSettings,
    fetchAsrSettings,
    saveSettings,
    saveAsrSettings
  }
}
