import { ref } from 'vue'
import { useSettings } from '@/stores/settingsStore'
import { getFullUrl } from '@/utils/request'

export function useTts() {
  const { settings } = useSettings()
  const isPlaying = ref(false)
  const audioContext = ref<AudioContext | null>(null)
  const sourceNode = ref<AudioBufferSourceNode | null>(null)

  async function speak(text: string) {
    if (!settings.value.ttsEnabled || !text) return

    stop()
    isPlaying.value = true

    try {
      const response = await fetch(getFullUrl('/api/tts/speak'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ text }),
      })

      if (!response.ok) {
        throw new Error('TTS request failed')
      }

      const arrayBuffer = await response.arrayBuffer()
      
      if (!audioContext.value) {
        audioContext.value = new (window.AudioContext || (window as any).webkitAudioContext)()
      }

      const audioBuffer = await audioContext.value.decodeAudioData(arrayBuffer)
      
      if (sourceNode.value) {
        sourceNode.value.stop()
      }

      sourceNode.value = audioContext.value.createBufferSource()
      sourceNode.value.buffer = audioBuffer
      sourceNode.value.connect(audioContext.value.destination)
      sourceNode.value.onended = () => {
        isPlaying.value = false
      }
      sourceNode.value.start()
      
    } catch (error) {
      console.error('TTS error:', error)
      isPlaying.value = false
    }
  }

  function stop() {
    if (sourceNode.value) {
      sourceNode.value.stop()
      sourceNode.value = null
    }
    isPlaying.value = false
  }

  return {
    isPlaying,
    speak,
    stop
  }
}
