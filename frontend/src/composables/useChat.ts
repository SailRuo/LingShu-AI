import { ref } from 'vue'
import type { ChatMessage } from '@/types'

/**
 * 聊天业务逻辑 Composable
 * 封装消息列表、流式发送、打字状态
 */
export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const inputMessage = ref('')
  const isTyping = ref(false)
  const welcomeGreeting = ref('欢迎回来')

  /** 初始化欢迎消息 (从后端获取流式动态问候语) */
  async function initWelcome() {
    try {
      const res = await fetch('/api/chat/welcome')
      if (!res.ok) throw new Error('Welcome stream failed')

      const reader = res.body?.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      
      if (!reader) return

      welcomeGreeting.value = '' // 先清空，准备流式写入

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

  /** 发送消息 (流式响应版本) */
  async function sendMessage(scrollCallback?: () => void) {
    const text = inputMessage.value.trim()
    if (!text || isTyping.value) return

    // 1. 添加用户消息
    messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
    inputMessage.value = ''
    isTyping.value = true
    scrollCallback?.()

    // 2. 创建一个空的助手消息占位
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

      // 3. 处理流读取 (SSE 格式解析: data: <content>\n\n)
      const reader = res.body?.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      
      if (!reader) throw new Error('No reader found')

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        buffer += decoder.decode(value, { stream: true })
        
        // 分割并处理每一行 (兼容 \r\n 和 \n)
        let lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (let line of lines) {
          const trimmed = line.trim()
          if (!trimmed) continue
          
          if (trimmed.startsWith('data:')) {
            // 找到 'data:' 的结束位置，并保留其后的所有字符 (包括空格和换行)
            const dataMatch = line.match(/^(\s*data:\s?)/)
            if (dataMatch) {
              const prefixLen = dataMatch[0].length
              const content = line.slice(prefixLen).replace(/\r$/, '')
              
              // 找到当前正在更新的助手消息
              const targetIdx = messages.value.length - 1
              if (targetIdx >= 0 && messages.value[targetIdx].role === 'assistant') {
                // 使用响应式更新，确保视图立即刷新
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

  /** 格式化时间戳 */
  function formatTime(ts: number): string {
    const diff = Math.floor((Date.now() - ts) / 1000)
    if (diff < 60) return '刚刚'
    if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
    return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return { messages, inputMessage, isTyping, welcomeGreeting, initWelcome, sendMessage, formatTime }
}
