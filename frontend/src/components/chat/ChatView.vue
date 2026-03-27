<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { NScrollbar, useDialog, useMessage } from 'naive-ui'
import { useChat } from '@/composables/useChat'
import { useWebSocket, type WebSocketMessage } from '@/composables/useWebSocket'
import { useSettings } from '@/stores/settingsStore'
import { useAsr } from '@/composables/useAsr'
import ChatMessageComponent from '@/components/chat/ChatMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { Sparkles, Loader2, Wifi, WifiOff, Trash2 } from 'lucide-vue-next'

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
  startAssistantMessage,
  appendAssistantChunk,
  appendReasoningChunk,
  upsertToolStep,
  failLatestAssistantMessage,
  syncLatestAssistantMessage,
  clearHistory,
  formatTime
} = useChat()

const {
  isConnected,
  connect,
  disconnect,
  sendChat,
  send,
  on,
  off,
  startHeartbeat
} = useWebSocket()

const { settings, fetchSettings } = useSettings()

const {
  isListening: asrListening,
  isRecording: asrRecording,
  isProcessing: asrProcessing,
  config: asrConfig,
  loadConfig: loadAsrConfig,
  startListening: startAsrListening,
  stopListening: stopAsrListening,
  startPushToTalk,
  stopPushToTalk,
  handleAsrResult,
  handleAsrError,
  setSendAudioCallback
} = useAsr()

const dialog = useDialog()
const message = useMessage()

const scrollRef = ref<InstanceType<typeof NScrollbar> | null>(null)
const isLoadingMore = ref(false)
const prevScrollHeight = ref(0)
const enableThinking = ref(false)
const isAIThinking = ref(false) // AI 正在思考/回复
const starSpeed = ref(1) // 星星移动速度
const meteors = ref<Array<{id: number, x: number, y: number, duration: number, delay: number}>>([])
let stopHeartbeat: (() => void) | null = null
let starAnimationId: number | null = null
let meteorTimer: number | null = null

// 检测用户是否已经在底部（允许 50px 的误差）
function isUserAtBottom(): boolean {
  const scrollContainer = scrollRef.value
  if (!scrollContainer) return true
  
  // 尝试不同的方式获取滚动容器
  let scrollElement: HTMLElement | null = null
  
  // 方式 1: 直接访问 $el
  const el = (scrollContainer as any).$el
  if (el && typeof el.querySelector === 'function') {
    scrollElement = el.querySelector('.n-scrollbar-container') || el
  }
  
  // 方式 2: 如果没有找到，尝试使用 containerRef
  if (!scrollElement && (scrollContainer as any).containerRef) {
    scrollElement = (scrollContainer as any).containerRef
  }
  
  // 如果还是没找到，返回 true（默认认为在底部）
  if (!scrollElement) return true
  
  const { scrollTop, scrollHeight, clientHeight } = scrollElement
  const threshold = 50 // 50px 误差范围
  return scrollHeight - scrollTop - clientHeight < threshold
}

function scrollToBottom(behavior: 'auto' | 'smooth' = 'smooth') {
  nextTick(() => {
    scrollRef.value?.scrollTo({ top: 999999, behavior })
  })
}

// 监听消息变化，自动滚动到最下方（只在用户在底部时才滚动）
watch(() => messages.value.length, () => {
  if (isUserAtBottom()) {
    scrollToBottom()
  }
}, { flush: 'post' }) // 确保在 DOM 更新后执行

// 监听 AI 思考状态
watch(isTyping, (newVal) => {
  if (newVal) {
    // AI 开始回复，启动星辰移动效果
    startStarMovement()
  }
})

// 启动星辰移动效果
function startStarMovement() {
  isAIThinking.value = true
  starSpeed.value = 1.5 // 初始速度降低 (原来 3)
  
  // 逐渐减速
  const decreaseInterval = setInterval(() => {
    if (!isTyping.value) {
      clearInterval(decreaseInterval)
      return
    }
    
    starSpeed.value = Math.max(0.2, starSpeed.value - 0.15) // 减速幅度减小 (原来 0.3)
    
    if (starSpeed.value <= 0.2) {
      clearInterval(decreaseInterval)
    }
  }, 1500) // 减速间隔增长 (原来 800)
}

// 创建流星效果
function createMeteor() {
  const id = Date.now()
  const x = Math.random() * 100
  const y = Math.random() * 30 // 流星从上方向下
  const duration = 1 + Math.random() * 1.5 // 1-2.5 秒
  const delay = Math.random() * 3 // 0-3 秒延迟
  
  meteors.value.push({ id, x, y, duration, delay })
  
  // 移除流星
  setTimeout(() => {
    meteors.value = meteors.value.filter(m => m.id !== id)
  }, (duration + delay) * 1000)
}

// 启动流星定时器
function startMeteorTimer() {
  if (meteorTimer !== null) return
  
  // 每 3-6 秒创建一个流星
  const createMeteorLoop = () => {
    createMeteor()
    const nextDelay = 3000 + Math.random() * 3000
    meteorTimer = window.setTimeout(createMeteorLoop, nextDelay)
  }
  
  createMeteorLoop()
}

function handleSend() {
  const text = inputMessage.value.trim()
  if (!text || isTyping.value) return

  messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
  inputMessage.value = ''
  isTyping.value = true
  scrollToBottom()

  sendChat(text, undefined, settings.value.model, settings.value.apiKey, settings.value.baseUrl, enableThinking.value)
}

function handleToggleThinking() {
  enableThinking.value = !enableThinking.value
}

function handleQuickAction(text: string) {
  inputMessage.value = text
  handleSend()
}

async function handleClearHistory() {
  if (messages.value.length === 0) return
  
  dialog.warning({
    title: '确认清空',
    content: '确定要清空所有会话历史吗？此操作不可撤销。',
    positiveText: '确认清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await clearHistory()
        message.success('已成功清空会话历史')
      } catch (error) {
        message.error('清空历史失败，请稍后重试')
      }
    }
  })
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

async function handleWebSocketMessage(msg: WebSocketMessage) {
  switch (msg.type) {
    case 'chatStart':
      isTyping.value = true
      startAssistantMessage()
      break
      
    case 'chatChunk':
      appendAssistantChunk(msg.content || '')
      isTyping.value = false
      // 只在用户在底部时才滚动
      if (isUserAtBottom()) {
        scrollToBottom()
      }
      break

    case 'reasoningChunk':
      appendReasoningChunk(msg.content || '')
      if (isUserAtBottom()) {
        scrollToBottom()
      }
      break

    case 'toolCallStart':
      upsertToolStep({
        toolCallId: msg.toolCallId,
        toolName: msg.toolName,
        arguments: msg.arguments,
        status: 'running',
        isError: false
      })
      isTyping.value = false
      if (isUserAtBottom()) {
        scrollToBottom()
      }
      break

    case 'toolCallEnd':
      upsertToolStep({
        toolCallId: msg.toolCallId,
        toolName: msg.toolName,
        arguments: msg.arguments,
        result: msg.result,
        output: msg.result,
        status: msg.isError ? 'error' : 'success',
        isError: !!msg.isError
      })
      if (isUserAtBottom()) {
        scrollToBottom()
      }
      break
      
    case 'chatEnd':
      isTyping.value = false
      await syncLatestAssistantMessage()
      // 消息结束时，如果用户在底部，就滚动到底部
      if (isUserAtBottom()) {
        scrollToBottom()
      }
      break
      
    case 'error':
      isTyping.value = false
      failLatestAssistantMessage(msg.message || '发生错误')
      await syncLatestAssistantMessage()
      // 错误时也保持用户当前位置，除非用户在底部
      if (isUserAtBottom()) {
        scrollToBottom()
      }
      break
      
    case 'proactiveGreeting':
      if (msg.content) {
        messages.value.push({ 
          role: 'assistant', 
          content: msg.content, 
          timestamp: Date.now() 
        })
        // 主动问候时，如果用户在底部就滚动
        if (isUserAtBottom()) {
          scrollToBottom()
        }
      }
      break
      
    case 'userState':
      console.log('用户状态更新:', msg)
      break

    case 'asrResult':
      handleAsrResult(msg.text || '')
      if (msg.text && msg.text.trim()) {
        inputMessage.value = msg.text
        handleSend()
      }
      break

    case 'asrError':
      handleAsrError(msg.message || '语音识别失败')
      break
  }
}

onMounted(async () => {
  await initChat()
  if (messages.value.length === 0) {
    initWelcome()
  }
  await fetchSettings()
  await loadAsrConfig()

  connect()

  on('*', handleWebSocketMessage)

  stopHeartbeat = startHeartbeat(30000)
  
  // 启动流星效果
  startMeteorTimer()

  setSendAudioCallback((base64: string, mimeType: string) => {
    send({ type: 'audio', data: base64, mimeType })
  })

  setTimeout(() => {
    scrollToBottom('auto')
  }, 100)
})

onUnmounted(() => {
  stopAsrListening()
  disconnect()
  off('*', handleWebSocketMessage)
  if (stopHeartbeat) {
    stopHeartbeat()
  }
  if (starAnimationId !== null) {
    cancelAnimationFrame(starAnimationId)
  }
  if (meteorTimer !== null) {
    clearTimeout(meteorTimer)
  }
})

async function handleToggleAsr() {
  if (asrListening.value) {
    await stopAsrListening()
  } else {
    const success = await startAsrListening()
    if (!success) {
      message.error('无法启动语音输入，请检查麦克风权限')
    }
  }
}
</script>

<template>
  <div class="chat-view">
    <!-- Action Bar -->
    <div class="chat-header">
      <div class="connection-status" :class="{ connected: isConnected, disconnected: !isConnected }">
        <Wifi v-if="isConnected" :size="14" />
        <WifiOff v-else :size="14" />
        <span>{{ isConnected ? '已连接' : '未连接' }}</span>
      </div>

      <div class="chat-actions" v-if="messages.length > 0">
        <button 
          class="action-btn clear-btn" 
          @click="handleClearHistory" 
          title="清空会话"
        >
          <Trash2 :size="14" />
          <span>清空</span>
        </button>
      </div>
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
          <button class="quick-btn" @click="handleQuickAction('我们的回忆')">
            <Sparkles :size="16" />
            <span>我们的回忆</span>
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
      <!-- 星空背景 -->
      <div class="stars-background">
        <!-- 动态星星 -->
        <div class="star" v-for="i in 50" :key="i" :style="{
          left: Math.random() * 100 + '%',
          top: Math.random() * 100 + '%',
          animationDelay: Math.random() * 3 + 's',
          width: Math.random() * 2 + 1 + 'px',
          height: Math.random() * 2 + 1 + 'px',
          transform: `translateY(${isAIThinking ? (Math.random() * 20 * starSpeed) : 0}px)`,
          transition: isAIThinking ? 'transform 0.1s linear' : 'transform 0.5s ease-out'
        }"></div>
        
        <!-- 流星 -->
        <div class="meteor" v-for="meteor in meteors" :key="meteor.id" :style="{
          left: meteor.x + '%',
          top: meteor.y + '%',
          animationDuration: meteor.duration + 's',
          animationDelay: meteor.delay + 's'
        }"></div>
      </div>
      
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
      :asr-enabled="asrConfig.enabled"
      :asr-listening="asrListening"
      :asr-recording="asrRecording"
      :asr-processing="asrProcessing"
      :enable-thinking="enableThinking"
      @send="handleSend"
      @toggle-asr="handleToggleAsr"
      @toggle-thinking="handleToggleThinking"
      @start-push-to-talk="startPushToTalk"
      @stop-push-to-talk="stopPushToTalk"
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
  z-index: 1;
}

/* Chat Header */
.chat-header {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  z-index: 100;
}

/* Connection Status */
.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 12px;
  transition: all 0.3s ease;
  backdrop-filter: blur(8px);
}

.connection-status.connected {
  background: rgba(52, 211, 153, 0.1);
  color: var(--color-primary);
  border: 1px solid rgba(52, 211, 153, 0.2);
}

.connection-status.disconnected {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

/* Chat Actions */
.chat-actions {
  display: flex;
  align-items: center;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.05);
  color: var(--color-text-dim);
  backdrop-filter: blur(8px);
}

.clear-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.2);
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
  position: relative;
  z-index: 1;
  width: calc(100% - 48px);
  max-width: 1050px;
  margin: 0 auto;
  padding: 24px 0 200px;
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

/* 星空背景 */
.stars-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

/* 消息区域 - 在星空背景之上 */
.messages-area {
  position: relative;
  z-index: 1;
  pointer-events: none;
}

.messages-area :deep(.n-scrollbar-container) {
  pointer-events: auto;
}

.star {
  position: absolute;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  box-shadow: 0 0 4px rgba(255, 255, 255, 0.5);
  animation: twinkle 2s ease-in-out infinite;
  will-change: transform;
}

/* 流星效果 */
.meteor {
  position: absolute;
  width: 2px;
  height: 2px;
  background: linear-gradient(to bottom right, rgba(255, 255, 255, 1), transparent);
  border-radius: 50%;
  box-shadow: 
    0 0 10px rgba(255, 255, 255, 0.8),
    0 0 20px rgba(147, 197, 253, 0.6),
    -30px 0 15px rgba(255, 255, 255, 0.3);
  animation: meteor-shower 2s ease-in-out forwards;
  opacity: 0;
}

@keyframes meteor-shower {
  0% {
    opacity: 0;
    transform: translate(0, 0) scale(0.5);
  }
  10% {
    opacity: 1;
  }
  50% {
    transform: translate(100px, 150px) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate(200px, 300px) scale(0.3);
  }
}

@keyframes twinkle {
  0%, 100% {
    opacity: 0.3;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
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
