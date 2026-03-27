import { ref, computed, onUnmounted } from 'vue'

export interface AsrConfig {
  enabled: boolean
  url: string
  sensitivity?: number
}

export type AsrMode = 'auto' | 'push-to-talk'

export function useAsr() {
  const isListening = ref(false)
  const isRecording = ref(false)
  const isProcessing = ref(false)
  const error = ref<string | null>(null)
  const lastResult = ref<string>('')
  const mode = ref<AsrMode>('auto')
  const config = ref<AsrConfig>({
    enabled: false,
    url: 'http://localhost:50001',
    sensitivity: 0.5
  })

  const VAD_THRESHOLD = 0.02
  const SILENCE_DURATION_MS = 800
  const MIN_SPEECH_DURATION_MS = 300
  const AUDIO_SAMPLE_RATE = 16000

  let mediaStream: MediaStream | null = null
  let mediaRecorder: MediaRecorder | null = null
  let audioContext: AudioContext | null = null
  let analyser: AnalyserNode | null = null
  let animationFrameId: number | null = null
  let audioChunks: Blob[] = []
  let speechStartTime = 0
  let lastSpeechTime = 0
  let inSpeech = false
  let silenceTimeout: number | null = null
  let sendAudioCallback: ((base64: string, mimeType: string) => void) | null = null

  const status = computed(() => {
    if (error.value) return 'error'
    if (isProcessing.value) return 'processing'
    if (isRecording.value) return 'recording'
    if (isListening.value) return 'listening'
    return 'idle'
  })

  function setSendAudioCallback(callback: (base64: string, mimeType: string) => void) {
    sendAudioCallback = callback
  }

  async function loadConfig(): Promise<AsrConfig> {
    try {
      const res = await fetch('/api/settings/asr')
      if (res.ok) {
        const data = await res.json()
        config.value = {
          enabled: data.enabled ?? false,
          url: data.url ?? 'http://localhost:50001',
          sensitivity: data.sensitivity ?? 0.5
        }
      }
    } catch (e) {
      console.error('加载 ASR 配置失败:', e)
    }
    return config.value
  }

  async function saveConfig(newConfig: Partial<AsrConfig>): Promise<void> {
    try {
      const res = await fetch('/api/settings/asr', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...config.value, ...newConfig })
      })
      if (res.ok) {
        config.value = { ...config.value, ...newConfig }
      }
    } catch (e) {
      console.error('保存 ASR 配置失败:', e)
      throw e
    }
  }

  async function startListening(): Promise<boolean> {
    if (!config.value.enabled) {
      error.value = 'ASR 服务未启用'
      return false
    }

    if (isListening.value) {
      console.log('[ASR] 已经在监听中')
      return true
    }

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: AUDIO_SAMPLE_RATE,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true
        }
      })

      audioContext = new AudioContext({ sampleRate: AUDIO_SAMPLE_RATE })
      const source = audioContext.createMediaStreamSource(mediaStream)
      analyser = audioContext.createAnalyser()
      analyser.fftSize = 512
      analyser.smoothingTimeConstant = 0.8
      source.connect(analyser)

      isListening.value = true
      error.value = null
      mode.value = 'auto'

      startVadDetection()

      console.log('[ASR] 开始监听')
      return true
    } catch (e) {
      error.value = '无法访问麦克风: ' + (e as Error).message
      console.error('[ASR] 麦克风访问失败:', e)
      return false
    }
  }

  async function stopListening() {
    if (animationFrameId) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }

    if (silenceTimeout) {
      clearTimeout(silenceTimeout)
      silenceTimeout = null
    }

    if (mediaRecorder && isRecording.value) {
      await stopRecording()
    }

    if (audioContext) {
      audioContext.close()
      audioContext = null
    }

    if (mediaStream) {
      mediaStream.getTracks().forEach(track => track.stop())
      mediaStream = null
    }

    analyser = null
    mediaRecorder = null
    isListening.value = false
    isRecording.value = false
    inSpeech = false
    console.log('[ASR] 停止监听')
  }

  function startVadDetection() {
    if (!analyser) {
      console.error('[ASR] analyser 未初始化')
      return
    }

    const dataArray = new Float32Array(analyser.frequencyBinCount)
    const threshold = VAD_THRESHOLD * (1 - (config.value.sensitivity ?? 0.5))

    console.log('[ASR] 开始 VAD 检测, threshold:', threshold.toFixed(4))

    function detect() {
      if (!isListening.value || mode.value !== 'auto') {
        console.log('[ASR] VAD 停止: isListening=', isListening.value, ', mode=', mode.value)
        return
      }

      if (!analyser) {
        console.error('[ASR] analyser 丢失')
        return
      }

      analyser.getFloatTimeDomainData(dataArray)
      const rms = calculateRms(dataArray)

      const now = Date.now()

      if (rms > threshold) {
        if (!inSpeech) {
          inSpeech = true
          speechStartTime = now
          console.log('[ASR] 检测到语音开始, RMS:', rms.toFixed(4))
          startRecording()
        }
        lastSpeechTime = now
      } else if (inSpeech) {
        const speechDuration = lastSpeechTime - speechStartTime
        const silenceDuration = now - lastSpeechTime

        if (silenceDuration > SILENCE_DURATION_MS && speechDuration > MIN_SPEECH_DURATION_MS) {
          console.log('[ASR] 检测到语音结束，语音时长:', speechDuration, 'ms')
          inSpeech = false
          stopRecordingAndSend()
        }
      }

      animationFrameId = requestAnimationFrame(detect)
    }

    animationFrameId = requestAnimationFrame(detect)
  }

  function calculateRms(dataArray: Float32Array): number {
    let sum = 0
    for (let i = 0; i < dataArray.length; i++) {
      sum += dataArray[i] * dataArray[i]
    }
    return Math.sqrt(sum / dataArray.length)
  }

  function startRecording() {
    if (!mediaStream || isRecording.value) return

    audioChunks = []
    
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus'
      : 'audio/webm'

    mediaRecorder = new MediaRecorder(mediaStream, { mimeType })

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) {
        audioChunks.push(e.data)
      }
    }

    mediaRecorder.start(100)
    isRecording.value = true
    console.log('[ASR] 开始录音')
  }

  function stopRecording(): Promise<Blob> {
    return new Promise((resolve) => {
      if (!mediaRecorder || !isRecording.value) {
        resolve(new Blob())
        return
      }

      mediaRecorder!.onstop = () => {
        const mimeType = mediaRecorder!.mimeType || 'audio/webm'
        const blob = new Blob(audioChunks, { type: mimeType })
        audioChunks = []
        isRecording.value = false
        console.log('[ASR] 停止录音, blob size:', blob.size)
        resolve(blob)
      }
      mediaRecorder!.stop()
    })
  }

  async function stopRecordingAndSend() {
    const blob = await stopRecording()
    if (!blob || blob.size === 0) {
      console.log('[ASR] 没有录制到音频')
      return
    }

    await sendAudio(blob)
  }

  async function sendAudio(blob: Blob) {
    if (!sendAudioCallback) {
      console.error('[ASR] 未设置音频发送回调')
      return
    }

    isProcessing.value = true
    error.value = null

    try {
      const reader = new FileReader()
      reader.onload = () => {
        const base64 = (reader.result as string).split(',')[1]
        sendAudioCallback!(base64, blob.type)
      }
      reader.readAsDataURL(blob)
    } catch (e) {
      error.value = '发送音频失败: ' + (e as Error).message
      isProcessing.value = false
    }
  }

  function handleAsrResult(text: string) {
    lastResult.value = text
    isProcessing.value = false
    console.log('[ASR] 识别结果:', text)
  }

  function handleAsrError(message: string) {
    error.value = message
    isProcessing.value = false
    console.error('[ASR] 识别错误:', message)
  }

  function startPushToTalk() {
    if (!isListening.value) return
    mode.value = 'push-to-talk'
    if (animationFrameId) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
    startRecording()
  }

  async function stopPushToTalk() {
    if (mode.value !== 'push-to-talk' || !isRecording.value) return
    const blob = await stopRecording()
    if (blob && blob.size > 0) {
      await sendAudio(blob)
    }
    mode.value = 'auto'
    if (isListening.value) {
      startVadDetection()
    }
  }

  onUnmounted(() => {
    stopListening()
  })

  return {
    isListening,
    isRecording,
    isProcessing,
    error,
    lastResult,
    status,
    mode,
    config,
    loadConfig,
    saveConfig,
    startListening,
    stopListening,
    startPushToTalk,
    stopPushToTalk,
    handleAsrResult,
    handleAsrError,
    setSendAudioCallback
  }
}
