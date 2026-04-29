import { defineStore } from 'pinia';
import { ref } from 'vue';
import { Message } from '@arco-design/web-vue';

function getFullUrl(path: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  return `${baseUrl}${path}`;
}

export interface LlmConfig {
  source: string
  model: string
  baseUrl: string
  apiKey: string
  enableThinking: boolean
}

export interface EmbeddingConfig {
  source: string
  model: string
  baseUrl: string
  apiKey: string
}

export interface MemoryModelConfig {
  source: string
  model: string
  baseUrl: string
  apiKey: string
}

export interface TtsConfig {
  enabled: boolean
  baseUrl: string
  apiKey: string
  defaultVoice: string
  defaultSpeed: number
  defaultFormat: string
  defaultSeed: number
}

export interface AsrConfig {
  enabled: boolean
  url: string
}

export interface ModelOption {
  label: string
  value: string
}

export const useSettingsStore = defineStore('settings', () => {
  const llm = ref<LlmConfig>({
    source: 'ollama',
    model: '',
    baseUrl: 'http://localhost:11434',
    apiKey: '',
    enableThinking: false
  })

  const embedding = ref<EmbeddingConfig>({
    source: 'ollama',
    model: '',
    baseUrl: 'http://localhost:11434',
    apiKey: ''
  })

  const memoryModel = ref<MemoryModelConfig>({
    source: '',
    model: '',
    baseUrl: '',
    apiKey: ''
  })

  const tts = ref<TtsConfig>({
    enabled: false,
    baseUrl: 'http://localhost:5050',
    apiKey: '',
    defaultVoice: 'alloy',
    defaultSpeed: 1.0,
    defaultFormat: 'mp3',
    defaultSeed: -1
  })

  const asr = ref<AsrConfig>({
    enabled: false,
    url: 'http://localhost:50001'
  })

  const isLoaded = ref(false)
  const saving = ref(false)

  const chatModelOptions = ref<ModelOption[]>([])
  const embedModelOptions = ref<ModelOption[]>([])
  const memoryModelOptions = ref<ModelOption[]>([])
  const loadingChatModels = ref(false)
  const loadingEmbedModels = ref(false)
  const loadingMemoryModels = ref(false)

  async function fetchSettings() {
    try {
      const res = await fetch(getFullUrl('/api/settings'))
      if (!res.ok) throw new Error('Failed to fetch settings')
      const data = await res.json()

      llm.value = {
        source: data.source || 'ollama',
        model: data.chatModel || '',
        baseUrl: data.baseUrl || 'http://localhost:11434',
        apiKey: data.apiKey || '',
        enableThinking: data.enableThinking ?? false
      }

      embedding.value = {
        source: data.embedSource || 'ollama',
        model: data.embedModel || '',
        baseUrl: data.embedBaseUrl || 'http://localhost:11434',
        apiKey: data.embedApiKey || ''
      }

      memoryModel.value = {
        source: data.memoryModelSource || '',
        model: data.memoryModel || '',
        baseUrl: data.memoryModelBaseUrl || '',
        apiKey: data.memoryModelApiKey || ''
      }

      tts.value = {
        enabled: data.ttsEnabled ?? false,
        baseUrl: data.ttsBaseUrl || 'http://localhost:5050',
        apiKey: data.ttsApiKey || '',
        defaultVoice: data.ttsDefaultVoice || 'alloy',
        defaultSpeed: data.ttsDefaultSpeed ?? 1.0,
        defaultFormat: data.ttsDefaultFormat || 'mp3',
        defaultSeed: data.ttsDefaultSeed ?? -1
      }

      // Fetch ASR settings separately
      try {
        const asrRes = await fetch(getFullUrl('/api/settings/asr'))
        if (asrRes.ok) {
          const asrData = await asrRes.json()
          asr.value = {
            enabled: asrData.enabled ?? false,
            url: asrData.url || 'http://localhost:50001'
          }
        }
      } catch (err) {
        console.error('Failed to fetch ASR settings', err)
      }

      isLoaded.value = true

      fetchChatModels(true)
      fetchEmbedModels(true)
      fetchMemoryModels(true)
    } catch (err) {
      console.error('Failed to fetch settings', err)
    }
  }

  async function saveSettings() {
    saving.value = true
    try {
      const res = await fetch(getFullUrl('/api/settings'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          source: llm.value.source,
          chatModel: llm.value.model,
          baseUrl: llm.value.baseUrl,
          apiKey: llm.value.apiKey,
          enableThinking: llm.value.enableThinking,
          embedSource: embedding.value.source,
          embedModel: embedding.value.model,
          embedBaseUrl: embedding.value.baseUrl,
          embedApiKey: embedding.value.apiKey,
          memoryModelSource: memoryModel.value.source,
          memoryModel: memoryModel.value.model,
          memoryModelBaseUrl: memoryModel.value.baseUrl,
          memoryModelApiKey: memoryModel.value.apiKey,
          ttsEnabled: tts.value.enabled,
          ttsBaseUrl: tts.value.baseUrl,
          ttsApiKey: tts.value.apiKey,
          ttsDefaultVoice: tts.value.defaultVoice,
          ttsDefaultSpeed: tts.value.defaultSpeed,
          ttsDefaultFormat: tts.value.defaultFormat,
          ttsDefaultSeed: tts.value.defaultSeed
        })
      })
      if (!res.ok) throw new Error('Failed to save settings')

      // Save ASR settings separately
      const asrRes = await fetch(getFullUrl('/api/settings/asr'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          enabled: asr.value.enabled,
          url: asr.value.url
        })
      })
      if (!asrRes.ok) throw new Error('Failed to save ASR settings')

      Message.success('模型配置已保存')
    } catch (err) {
      console.error('Failed to save settings', err)
      Message.error('保存配置失败')
    } finally {
      saving.value = false
    }
  }

  async function fetchChatModels(silent = false) {
    const baseUrl = llm.value.baseUrl
    const source = llm.value.source
    if (!baseUrl || !source) return
    loadingChatModels.value = true
    try {
      const params = new URLSearchParams({
        source,
        baseUrl,
        apiKey: llm.value.apiKey
      })
      const res = await fetch(getFullUrl(`/api/chat/models?${params.toString()}`))
      const models: string[] = await res.json()
      chatModelOptions.value = models.map((m: string) => ({ label: m, value: m }))
      if (chatModelOptions.value.length > 0 && !chatModelOptions.value.find(o => o.value === llm.value.model)) {
        llm.value.model = chatModelOptions.value[0].value
      }
      if (!silent) Message.success('对话模型列表已更新')
    } catch (err) {
      if (!silent) Message.error('无法连接到对话模型服务')
      chatModelOptions.value = []
    } finally {
      loadingChatModels.value = false
    }
  }

  async function fetchEmbedModels(silent = false) {
    const baseUrl = embedding.value.baseUrl
    const source = embedding.value.source
    if (!baseUrl || !source) return
    loadingEmbedModels.value = true
    try {
      const params = new URLSearchParams({
        source,
        baseUrl,
        apiKey: embedding.value.apiKey
      })
      const res = await fetch(getFullUrl(`/api/chat/models?${params.toString()}`))
      const models: string[] = await res.json()
      embedModelOptions.value = models.map((m: string) => ({ label: m, value: m }))
      if (embedModelOptions.value.length > 0 && !embedModelOptions.value.find(o => o.value === embedding.value.model)) {
        embedding.value.model = embedModelOptions.value[0].value
      }
      if (!silent) Message.success('向量模型列表已更新')
    } catch (err) {
      if (!silent) Message.error('无法连接到向量模型服务')
      embedModelOptions.value = []
    } finally {
      loadingEmbedModels.value = false
    }
  }

  async function fetchMemoryModels(silent = false) {
    const baseUrl = memoryModel.value.baseUrl || llm.value.baseUrl
    const source = memoryModel.value.source || llm.value.source
    if (!baseUrl || !source) return
    loadingMemoryModels.value = true
    try {
      const params = new URLSearchParams({
        source,
        baseUrl,
        apiKey: memoryModel.value.apiKey || llm.value.apiKey
      })
      const res = await fetch(getFullUrl(`/api/chat/models?${params.toString()}`))
      const models: string[] = await res.json()
      memoryModelOptions.value = models.map((m: string) => ({ label: m, value: m }))
      if (memoryModelOptions.value.length > 0 && !memoryModelOptions.value.find(o => o.value === memoryModel.value.model)) {
        memoryModel.value.model = memoryModelOptions.value[0].value
      }
      if (!silent) Message.success('记忆模型列表已更新')
    } catch (err) {
      if (!silent) Message.error('无法连接到记忆模型服务')
      memoryModelOptions.value = []
    } finally {
      loadingMemoryModels.value = false
    }
  }

  return {
    llm,
    embedding,
    memoryModel,
    tts,
    asr,
    isLoaded,
    saving,
    chatModelOptions,
    embedModelOptions,
    memoryModelOptions,
    loadingChatModels,
    loadingEmbedModels,
    loadingMemoryModels,
    fetchSettings,
    saveSettings,
    fetchChatModels,
    fetchEmbedModels,
    fetchMemoryModels
  }
})
