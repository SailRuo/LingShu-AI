import { ref, watch } from 'vue'

export interface SystemSettings {
  source: string
  model: string
  baseUrl: string
  apiKey: string
}

const settings = ref<SystemSettings>({
  source: 'openai',
  model: '',
  baseUrl: '',
  apiKey: ''
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

watch(
  () => settings.value,
  () => {
    if (isLoaded.value) {
      saveSettings()
    }
  },
  { deep: true }
)

export function useSettings() {
  return {
    settings,
    isLoaded,
    fetchSettings,
    saveSettings
  }
}
