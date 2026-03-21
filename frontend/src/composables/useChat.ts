import { ref } from 'vue'
import type { ChatMessage } from '@/types'

interface HistoryMessage {
  id: number
  role: string
  content: string
  timestamp: string
}

export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const inputMessage = ref('')
  const isTyping = ref(false)
  const welcomeGreeting = ref('欢迎回来')
  const isLoadingHistory = ref(false)
  const hasMoreHistory = ref(true)
  const oldestMessageId = ref<number | null>(null)

  async function loadHistory(size: number = 20): Promise<boolean> {
    if (isLoadingHistory.value || !hasMoreHistory.value) {
      return false
    }
    
    isLoadingHistory.value = true
    
    try {
      const params = new URLSearchParams({ size: size.toString() })
        if (oldestMessageId.value) {
            params.append('beforeId', oldestMessageId.value.toString())
        }
        
        const res = await fetch(`/api/chat/history?${params}`)
        if (!res.ok) throw new Error('History fetch failed')
        
        const historyMessages: HistoryMessage[] = await res.json()
        
        if (historyMessages.length === 0) {
            hasMoreHistory.value = false
            return false
        }
        
        const formattedMessages: ChatMessage[] = historyMessages.map(m => ({
            role: m.role as 'user' | 'assistant',
            content: m.content,
            timestamp: new Date(m.timestamp).getTime()
        }))
        
        if (formattedMessages.length < size) {
            hasMoreHistory.value = false
        }
        
        const oldestMsg = historyMessages[historyMessages.length - 1]
        if (oldestMsg) {
            oldestMessageId.value = oldestMsg.id
        }
        
        messages.value = [...formattedMessages.reverse(), ...messages.value]
        
        return true
    } catch (err) {
        console.error('Load history error:', err)
        return false
    } finally {
        isLoadingHistory.value = false
    }
  }

  async function initWelcome() {
    try {
        const res = await fetch('/api/chat/welcome')
        if (!res.ok) throw new Error('Welcome stream failed')

        const reader = res.body?.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        
        if (!reader) return

        welcomeGreeting.value = ''

        while (true) {
            const { done, value } = await reader.read()
            if (done) break
            
            buffer += decoder.decode(value, { stream: true })
            let lines = buffer.split('\n')
            buffer = lines.pop() || ''
            
            for (const line of lines) {
                if (line.trim().startsWith('data:')) {
                    let content = line.replace(/^data:\s?/, '')
                    welcomeGreeting.value += content
                }
            }
        }
    } catch (err) {
        console.error('Welcome fetch error:', err)
        welcomeGreeting.value = "欢迎回来。今天有什么我可以帮你的吗？"
    }
  }

  async function initChat() {
    await loadHistory(20)
  }

  async function sendMessage(scrollCallback?: () => void) {
    const text = inputMessage.value.trim()
    if (!text || isTyping.value) return

    messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
    inputMessage.value = ''
    isTyping.value = true
    scrollCallback?.()

    const assistantMessage: ChatMessage = { 
      role: 'assistant', 
      content: '', 
      timestamp: Date.now() 
    }
    messages.value.push(assistantMessage)

    try {
        const res = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text }),
        })

        if (!res.ok) throw new Error('Stream request failed')

        const reader = res.body?.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        
        if (!reader) throw new Error('No reader found')

        while (true) {
            const { done, value } = await reader.read()
            if (done) break
            
            buffer += decoder.decode(value, { stream: true })
            
            let lines = buffer.split('\n')
            buffer = lines.pop() || ''
            
            for (let line of lines) {
                const trimmed = line.trim()
                if (!trimmed) continue
                
                if (trimmed.startsWith('data:')) {
                    const dataMatch = line.match(/^(\s*data:\s?)/)
                    if (dataMatch) {
                        const prefixLen = dataMatch[0].length
                        const content = line.slice(prefixLen).replace(/\r$/, '')
                        
                        const targetIdx = messages.value.length - 1
                        if (targetIdx >= 0 && messages.value[targetIdx].role === 'assistant') {
                            messages.value[targetIdx] = {
                                ...messages.value[targetIdx],
                                content: messages.value[targetIdx].content + content
                            }
                        }
                        
                        scrollCallback?.()
                    }
                }
            }
        }
    } catch (err) {
        console.error('Streaming error:', err)
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
            lastMsg.content = '⚠️ 系统中枢连接异常，此时无法建立流式传输。'
        }
    } finally {
        isTyping.value = false
        scrollCallback?.()
    }
  }

  function formatTime(ts: number): string {
    const diff = Math.floor((Date.now() - ts) / 1000)
    if (diff < 60) return '刚刚'
    if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
    return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return { 
    messages, 
    inputMessage, 
    isTyping, 
    welcomeGreeting, 
    isLoadingHistory,
    hasMoreHistory,
    initWelcome, 
    initChat,
    loadHistory,
    sendMessage, 
    formatTime 
  }
}
