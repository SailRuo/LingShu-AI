import { ref } from 'vue'

export interface UserState {
  userId: string
  affinity: number
  relationshipStage: string
  lastEmotion: string
  lastEmotionIntensity: number
  needsGreeting: boolean
  inactiveHours: number
}

export function useProactive() {
  const userState = ref<UserState | null>(null)
  const proactiveMessage = ref('')
  const hasProactiveMessage = ref(false)
  let checkInterval: ReturnType<typeof setInterval> | null = null

  async function triggerGreeting(userId: string = 'User') {
    try {
      await fetch(`/api/chat/proactive/trigger?userId=${userId}`, { method: 'POST' })
    } catch (err) {
      console.error('Trigger greeting error:', err)
    }
  }

  async function fetchUserState(userId: string = 'User') {
    try {
      const res = await fetch(`/api/chat/proactive/attention`)
      if (res.ok) {
        const users = await res.json()
        const currentUser = users.find((u: UserState) => u.userId === userId)
        if (currentUser) {
          userState.value = currentUser
          if (currentUser.needsGreeting) {
            await fetchProactiveGreeting(userId)
          }
        }
      }
    } catch (err) {
      console.error('Fetch user state error:', err)
    }
  }

  async function fetchProactiveGreeting(userId: string = 'User') {
    try {
      const res = await fetch(`/api/chat/proactive/greeting?userId=${userId}`)
      if (!res.ok) throw new Error('Greeting stream failed')

      const reader = res.body?.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      
      if (!reader) return

      proactiveMessage.value = ''
      hasProactiveMessage.value = true

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        buffer += decoder.decode(value, { stream: true })
        let lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (const line of lines) {
          if (line.trim().startsWith('data:')) {
            let content = line.replace(/^data:\s?/, '')
            proactiveMessage.value += content
          }
        }
      }
    } catch (err) {
      console.error('Proactive greeting error:', err)
    }
  }

  async function fetchProactiveComfort(userId: string = 'User') {
    try {
      const res = await fetch(`/api/chat/proactive/comfort?userId=${userId}`)
      if (!res.ok) throw new Error('Comfort stream failed')

      const reader = res.body?.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      
      if (!reader) return

      proactiveMessage.value = ''
      hasProactiveMessage.value = true

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        buffer += decoder.decode(value, { stream: true })
        let lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (const line of lines) {
          if (line.trim().startsWith('data:')) {
            let content = line.replace(/^data:\s?/, '')
            proactiveMessage.value += content
          }
        }
      }
    } catch (err) {
      console.error('Proactive comfort error:', err)
    }
  }

  function clearProactiveMessage() {
    proactiveMessage.value = ''
    hasProactiveMessage.value = false
  }

  function startPolling(userId: string = 'User', intervalMs: number = 60000) {
    triggerGreeting(userId)
    
    setTimeout(() => {
      fetchUserState(userId)
    }, 5000)
    
    checkInterval = setInterval(() => {
      fetchUserState(userId)
    }, intervalMs)
  }

  function stopPolling() {
    if (checkInterval) {
      clearInterval(checkInterval)
      checkInterval = null
    }
  }

  function getAffinityColor(affinity: number): string {
    if (affinity >= 81) return '#ff4d4f'
    if (affinity >= 61) return '#ff7a45'
    if (affinity >= 31) return '#ffa940'
    return '#52c41a'
  }

  function getAffinityLabel(stage: string): string {
    const labels: Record<string, string> = {
      '挚友': '❤️ 挚友',
      '亲密': '💕 亲密',
      '熟悉': '😊 熟悉',
      '初识': '👋 初识'
    }
    return labels[stage] || '👋 初识'
  }

  return {
    userState,
    proactiveMessage,
    hasProactiveMessage,
    fetchUserState,
    fetchProactiveGreeting,
    fetchProactiveComfort,
    clearProactiveMessage,
    startPolling,
    stopPolling,
    getAffinityColor,
    getAffinityLabel
  }
}
