<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { NScrollbar } from 'naive-ui'
import { useChat } from '@/composables/useChat'
import { useWebSocket, type WebSocketMessage } from '@/composables/useWebSocket'
import { useSettings } from '@/stores/settingsStore'
import ChatMessageComponent from '@/components/chat/ChatMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import type { ChatMessage } from '@/types'
import { Sparkles, Loader2, Wifi, WifiOff } from 'lucide-vue-next'

const {
  messages,
  inputMessage,
  isTyping,
  welcomeGreeting,
  isLoadingHistory,
  hasMoreHistory,
  initWelcome,
  initChat,
  loadHistory,
  formatTime
} = useChat()

const {
  isConnected,
  connect,
  disconnect,
  sendChat,
  on,
  off,
  startHeartbeat
} = useWebSocket()

const { settings, fetchSettings } = useSettings()

const scrollRef = ref<InstanceType<typeof NScrollbar> | null>(null)
const isLoadingMore = ref(false)
const prevScrollHeight = ref(0)
const currentAssistantMessage = ref('')
let stopHeartbeat: (() => void) | null = null

function scrollToBottom() {
  nextTick(() => {
    scrollRef.value?.scrollTo({ top: 999999, behavior: 'smooth' })
  })
}

function handleSend() {
  const text = inputMessage.value.trim()
  if (!text || isTyping.value) return

  messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
  inputMessage.value = ''
  isTyping.value = true
  scrollToBottom()

  const assistantMessage: ChatMessage = {
    role: 'assistant',
    content: '',
    timestamp: Date.now()
  }
  messages.value.push(assistantMessage)
  currentAssistantMessage.value = ''

  sendChat(text, undefined, settings.value.model, settings.value.apiKey, settings.value.baseUrl)
}

function handleQuickAction(text: string) {
  inputMessage.value = text
  handleSend()
}

async function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  if (target.scrollTop < 50 && hasMoreHistory.value && !isLoadingMore.value) {
    isLoadingMore.value = true
    prevScrollHeight.value = target.scrollHeight

    const loaded = await loadHistory(20)

    if (loaded) {
      nextTick(() => {
        const newScrollHeight = target.scrollHeight
        target.scrollTop = newScrollHeight - prevScrollHeight.value
      })
    }

    isLoadingMore.value = false
  }
}

function handleWebSocketMessage(message: WebSocketMessage) {
  switch (message.type) {
    case 'chatStart':
      isTyping.value = true
      currentAssistantMessage.value = ''
      break
      
    case 'chatChunk':
      currentAssistantMessage.value += message.content || ''
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.content = currentAssistantMessage.value
      }
      scrollToBottom()
      break
      
    case 'chatEnd':
      isTyping.value = false
      currentAssistantMessage.value = ''
      break
      
    case 'error':
      isTyping.value = false
      const errorMsg = messages.value[messages.value.length - 1]
      if (errorMsg && errorMsg.role === 'assistant') {
        errorMsg.content = '⚠️ ' + (message.message || '发生错误')
      }
      break
      
    case 'proactiveGreeting':
      if (message.content) {
        messages.value.push({ 
          role: 'assistant', 
          content: message.content, 
          timestamp: Date.now() 
        })
        scrollToBottom()
      }
      break
      
    case 'userState':
      console.log('用户状态更新:', message)
      break
  }
}

onMounted(async () => {
  await initChat()
  initWelcome()
  await fetchSettings()

  connect()

  on('*', handleWebSocketMessage)

  stopHeartbeat = startHeartbeat(30000)

  nextTick(() => {
    scrollToBottom()
  })
})

onUnmounted(() => {
  disconnect()
  off('*', handleWebSocketMessage)
  if (stopHeartbeat) {
    stopHeartbeat()
  }
})
</script>

<template>
  <div class="chat-view">
    <!-- Connection Status -->
    <div class="connection-status" :class="{ connected: isConnected, disconnected: !isConnected }">
      <Wifi v-if="isConnected" :size="14" />
      <WifiOff v-else :size="14" />
      <span>{{ isConnected ? '已连接' : '未连接' }}</span>
    </div>

    <!-- Welcome Section -->
    <div class="welcome-section" v-if="messages.length === 0">
      <div class="welcome-content">
        <div class="soul-orb" :class="{ thinking: isTyping }">
          <div class="orb-glow"></div>
          <div class="orb-ring"></div>
          <div class="orb-core"></div>
        </div>
        
        <h1 class="welcome-title">{{ welcomeGreeting }}</h1>
        <p class="welcome-subtitle">与灵枢建立深度连接，探索内心世界</p>
        
        <div class="quick-actions">
          <button class="quick-btn" @click="handleQuickAction('今天心情如何？')">
            <Sparkles :size="16" />
            <span>今天心情如何？</span>
          </button>
          <button class="quick-btn" @click="handleQuickAction('帮我分析一下')">
            <Sparkles :size="16" />
            <span>帮我分析一下</span>
          </button>
          <button class="quick-btn" @click="handleQuickAction('回忆上次对话')">
            <Sparkles :size="16" />
            <span>回忆上次对话</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Messages Area -->
    <n-scrollbar ref="scrollRef" class="messages-area" v-if="messages.length > 0" @scroll="handleScroll">
      <div class="messages-content">
        <!-- Load More Indicator -->
        <div class="load-more-indicator" v-if="hasMoreHistory">
          <button 
            class="load-more-btn" 
            @click="loadHistory(20)" 
            :disabled="isLoadingHistory"
          >
            <Loader2 v-if="isLoadingHistory" :size="16" class="spin" />
            <span v-else>向上滚动加载更多</span>
          </button>
        </div>
        <div class="no-more-indicator" v-else-if="messages.length > 0 && !hasMoreHistory">
          <span>— 没有更多历史消息 —</span>
        </div>
        
        <ChatMessageComponent
          v-for="(msg, i) in messages"
          :key="i"
          :message="msg"
          :time-label="formatTime(msg.timestamp)"
        />
        
        <!-- Typing Indicator -->
        <div class="typing-indicator" v-if="isTyping">
          <div class="typing-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </n-scrollbar>

    <!-- Input Area -->
    <ChatInput
      v-model="inputMessage"
      :loading="isTyping"
      :disabled="!isConnected"
      @send="handleSend"
    />
  </div>
</template>

<style scoped>
.chat-view {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* Connection Status */
.connection-status {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 12px;
  z-index: 100;
  transition: all 0.3s ease;
}

.connection-status.connected {
  background: rgba(52, 211, 153, 0.15);
  color: var(--color-primary);
  border: 1px solid rgba(52, 211, 153, 0.3);
}

.connection-status.disconnected {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

/* Welcome Section */
.welcome-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px 200px;
}

.welcome-content {
  text-align: center;
  max-width: 680px;
}

.soul-orb {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto 40px;
}

.orb-core {
  position: absolute;
  inset: 35px;
  background: radial-gradient(circle at 30% 30%, var(--color-primary), transparent 70%);
  background-color: var(--color-primary);
  border-radius: 50%;
  box-shadow: 
    0 0 30px var(--color-primary),
    0 0 60px rgba(52, 211, 153, 0.3),
    inset 0 0 20px rgba(255, 255, 255, 0.2);
  animation: breathe 4s ease-in-out infinite;
}

.orb-ring {
  position: absolute;
  inset: 15px;
  border: 2px solid var(--color-primary);
  border-radius: 50%;
  opacity: 0.4;
  animation: pulse-ring 4s ease-in-out infinite;
  box-shadow: 0 0 15px var(--color-primary);
}

.orb-glow {
  position: absolute;
  inset: -15px;
  background: radial-gradient(circle, var(--color-primary) 0%, transparent 70%);
  opacity: 0.15;
  border-radius: 50%;
  filter: blur(25px);
  animation: breathe 4s ease-in-out infinite;
}

.soul-orb.thinking .orb-core {
  animation: thinking-pulse 0.8s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.08); opacity: 0.85; }
}

@keyframes pulse-ring {
  0%, 100% { transform: scale(1); opacity: 0.4; }
  50% { transform: scale(1.12); opacity: 0.15; }
}

@keyframes thinking-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.25); }
}

.welcome-title {
  font-size: 18px;
  font-weight: 500;
  color: var(--color-text);
  margin-bottom: 16px;
  line-height: 1.7;
  letter-spacing: 0.02em;
  opacity: 0.95;
}

.welcome-subtitle {
  font-size: 14px;
  color: var(--color-text-dim);
  margin-bottom: 48px;
  letter-spacing: 0.05em;
  opacity: 0.8;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
}

.quick-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 24px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(8px);
}

.quick-btn:hover {
  background: var(--color-primary-dim);
  border-color: var(--color-primary);
  color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(52, 211, 153, 0.15);
}

.quick-btn:active {
  transform: translateY(0);
}

/* Messages Area */
.messages-area {
  flex: 1;
  min-height: 0;
}

.messages-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 24px 200px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Load More Indicator */
.load-more-indicator {
  text-align: center;
  padding: 16px 0;
}

.load-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: transparent;
  border: 1px dashed var(--color-outline);
  border-radius: 20px;
  color: var(--color-text-dim);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.load-more-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.load-more-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.no-more-indicator {
  text-align: center;
  padding: 16px 0;
  color: var(--color-text-dim);
  font-size: 12px;
  opacity: 0.6;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Typing Indicator */
.typing-indicator {
  display: flex;
  padding: 16px 0;
}

.typing-dots {
  display: flex;
  gap: 6px;
  padding: 12px 16px;
  background: var(--color-surface);
  border-radius: 16px;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border-radius: 50%;
  animation: dot-bounce 1.4s infinite;
}

.typing-dots span:nth-child(2) { animation-delay: 0.15s; }
.typing-dots span:nth-child(3) { animation-delay: 0.3s; }

@keyframes dot-bounce {
  0%, 100% { transform: translateY(0); opacity: 0.4; }
  50% { transform: translateY(-6px); opacity: 1; }
}
</style>
