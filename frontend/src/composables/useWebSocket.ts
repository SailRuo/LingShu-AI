import { ref } from 'vue'
import { getApiBaseUrl } from '@/utils/request'

export interface WebSocketMessage {
  type: string
  [key: string]: any
}

export type MessageHandler = (message: WebSocketMessage) => void

export function useWebSocket() {
  const ws = ref<WebSocket | null>(null)
  const isConnected = ref(false)
  const sessionId = ref<string | null>(null)
  const userId = ref<string>('User')
  const reconnectAttempts = ref(0)
  const maxReconnectAttempts = 5
  
  const messageHandlers = new Map<string, Set<MessageHandler>>()

  function connect(url?: string) {
    if (ws.value?.readyState === WebSocket.OPEN) {
      return
    }

    const wsUrl = url || getDefaultWsUrl()
    ws.value = new WebSocket(wsUrl)

    ws.value.onopen = () => {
      isConnected.value = true
      reconnectAttempts.value = 0
      console.log('[WebSocket] 连接成功')
      
      register(userId.value)
    }

    ws.value.onclose = (event) => {
      isConnected.value = false
      sessionId.value = null
      console.log('[WebSocket] 连接关闭:', event.code, event.reason)
      
      if (reconnectAttempts.value < maxReconnectAttempts) {
        const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.value), 30000)
        setTimeout(() => {
          reconnectAttempts.value++
          connect(url)
        }, delay)
      }
    }

    ws.value.onerror = (error) => {
      console.error('[WebSocket] 错误:', error)
    }

    ws.value.onmessage = (event) => {
      try {
        const message: WebSocketMessage = JSON.parse(event.data)
        handleMessage(message)
      } catch (e) {
        console.error('[WebSocket] 解析消息失败:', e)
      }
    }
  }

  function getDefaultWsUrl(): string {
    const baseUrl = getApiBaseUrl()
    if (baseUrl) {
      const wsBase = baseUrl.replace(/^http/, 'ws')
      return `${wsBase}/ws/chat`
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host.replace(':5173', ':8080')
    return `${protocol}//${host}/ws/chat`
  }

  function disconnect() {
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
    isConnected.value = false
    sessionId.value = null
  }

  function send(message: WebSocketMessage) {
    if (ws.value?.readyState === WebSocket.OPEN) {
      ws.value.send(JSON.stringify(message))
    } else {
      console.warn('[WebSocket] 连接未打开，无法发送消息')
    }
  }

  function register(uid: string) {
    userId.value = uid
    send({ type: 'register', userId: uid })
  }

  function sendChat(message: string, agentId?: number, model?: string, apiKey?: string, baseUrl?: string, enableThinking?: boolean, images?: string[]) {
    const payload: WebSocketMessage = { type: 'chat', message }
    if (agentId) payload.agentId = agentId
    if (model) payload.model = model
    if (apiKey) payload.apiKey = apiKey
    if (baseUrl) payload.baseUrl = baseUrl
    if (enableThinking) payload.enableThinking = enableThinking
    if (images && images.length > 0) payload.images = images
    send(payload)
  }

  function requestHistory(size: number = 20, beforeId?: number) {
    const payload: WebSocketMessage = { type: 'history', size }
    if (beforeId) payload.beforeId = beforeId
    send(payload)
  }

  function ping() {
    send({ type: 'ping' })
  }

  function handleMessage(message: WebSocketMessage) {

    switch (message.type) {
      case 'connected':
        sessionId.value = message.sessionId
        break
      case 'registered':
        console.log('[WebSocket] 用户已注册:', message.userId)
        break
      case 'pong':
        break
    }

    const handlers = messageHandlers.get(message.type)
    if (handlers) {
      handlers.forEach(handler => handler(message))
    }

    const allHandlers = messageHandlers.get('*')
    if (allHandlers) {
      allHandlers.forEach(handler => handler(message))
    }
  }

  function on(type: string, handler: MessageHandler) {
    if (!messageHandlers.has(type)) {
      messageHandlers.set(type, new Set())
    }
    messageHandlers.get(type)!.add(handler)
  }

  function off(type: string, handler: MessageHandler) {
    const handlers = messageHandlers.get(type)
    if (handlers) {
      handlers.delete(handler)
    }
  }

  function startHeartbeat(intervalMs: number = 30000) {
    const interval = setInterval(() => {
      if (isConnected.value) {
        ping()
      }
    }, intervalMs)
    
    return () => clearInterval(interval)
  }

  return {
    ws,
    isConnected,
    sessionId,
    userId,
    connect,
    disconnect,
    send,
    register,
    sendChat,
    requestHistory,
    ping,
    on,
    off,
    startHeartbeat
  }
}
