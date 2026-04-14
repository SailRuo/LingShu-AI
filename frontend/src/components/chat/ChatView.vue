<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { NScrollbar, useDialog, useMessage } from 'naive-ui'
import { useChat } from '@/composables/useChat'
import { useWebSocket, type WebSocketMessage } from '@/composables/useWebSocket'
import { useSettings } from '@/stores/settingsStore'
import { useAsr } from '@/composables/useAsr'
import { useTts } from '@/composables/useTts'
import ChatMessageComponent from '@/components/chat/ChatMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import type { ChatMessage } from '@/types'
import { copyConversationMarkdown, copyConversationPng } from '@/utils/chatExport'
import { Sparkles, Loader2, Wifi, WifiOff, Trash2, Volume2, VolumeX, Workflow, Download } from 'lucide-vue-next'

const emit = defineEmits<{
  (e: 'toggle-panel'): void
}>()

const {
  messages,
  inputMessage,
  inputImages,
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
  isPlaying: ttsPlaying,
  speak: ttsSpeak,
  stop: ttsStop
} = useTts()

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
const autoScrollEnabled = ref(true) // 是否开启自动滚动到底部
const exportMode = ref(false)
const selectedTurnIds = ref<Set<string>>(new Set())
const isCopyingMarkdown = ref(false)
const isCopyingImage = ref(false)
let stopHeartbeat: (() => void) | null = null

interface TurnGroup {
  id: string
  index: number
  messages: Array<{ message: ChatMessage; originalIndex: number }>
}

const turnGroups = computed<TurnGroup[]>(() => {
  const source = messages.value
  const groups: TurnGroup[] = []
  let i = 0
  let turnIndex = 0

  while (i < source.length) {
    const current = source[i]
    if (current.role !== 'user') {
      i += 1
      continue
    }

    const items: Array<{ message: ChatMessage; originalIndex: number }> = [{ message: current, originalIndex: i }]
    let cursor = i + 1
    while (cursor < source.length && source[cursor].role !== 'user') {
      items.push({ message: source[cursor], originalIndex: cursor })
      cursor += 1
    }

    turnIndex += 1
    groups.push({
      id: `turn-${current.id ?? current.timestamp ?? i}-${turnIndex}`,
      index: turnIndex,
      messages: items
    })
    i = cursor
  }

  return groups
})

const selectedTurnCount = computed(() => selectedTurnIds.value.size)

const allTurnsSelected = computed(() => turnGroups.value.length > 0 && selectedTurnIds.value.size === turnGroups.value.length)

const selectedMessagesForExport = computed<ChatMessage[]>(() => {
  if (!selectedTurnIds.value.size) {
    return []
  }
  return turnGroups.value
    .filter((group) => selectedTurnIds.value.has(group.id))
    .flatMap((group) => group.messages.map((item) => item.message))
})

watch(turnGroups, (nextGroups) => {
  if (!nextGroups.length) {
    exportMode.value = false
    selectedTurnIds.value = new Set()
    return
  }

  const validIds = new Set(nextGroups.map((group) => group.id))
  const filtered = new Set<string>()
  selectedTurnIds.value.forEach((id) => {
    if (validIds.has(id)) {
      filtered.add(id)
    }
  })
  selectedTurnIds.value = filtered
})

// 检测用户是否已经在底部（用于判断是否应该开启自动滚动）
function isAtBottom(el: HTMLElement): boolean {
  const { scrollTop, scrollHeight, clientHeight } = el
  const threshold = 15 // 15px 误差范围
  return scrollHeight - scrollTop - clientHeight < threshold
}

function scrollToBottom(behavior: 'auto' | 'smooth' = 'auto') {
  nextTick(() => {
    scrollRef.value?.scrollTo({ top: 999999, behavior })
  })
}

function getScrollContainer(): HTMLElement | null {
  const raw = (scrollRef.value as unknown as { $el?: unknown })?.$el
  if (raw && typeof (raw as { querySelector?: unknown }).querySelector === 'function') {
    const container = (raw as HTMLElement).querySelector('.n-scrollbar-container') as HTMLElement | null
    if (container) return container
  }
  return document.querySelector('.messages-area .n-scrollbar-container') as HTMLElement | null
}

function preserveScrollPositionForModeSwitch(mutator: () => void) {
  const container = getScrollContainer()
  const wasAtBottom = container ? isAtBottom(container) : true
  const previousTop = container?.scrollTop ?? 0
  const previousHeight = container?.scrollHeight ?? 0

  mutator()

  nextTick(() => {
    const nextContainer = getScrollContainer()
    if (!nextContainer) return
    if (wasAtBottom) {
      nextContainer.scrollTop = nextContainer.scrollHeight
      return
    }
    const delta = nextContainer.scrollHeight - previousHeight
    nextContainer.scrollTop = Math.max(0, previousTop + delta)
  })
}

// 监听消息变化，仅在开启了自动滚动时才滚动
watch(() => messages.value.length, () => {
  if (autoScrollEnabled.value) {
    scrollToBottom()
  }
}, { flush: 'post' })

function handleSend() {
  const text = inputMessage.value.trim()
  const images = inputImages.value
  if ((!text && images.length === 0) || isTyping.value) return

  if (isConnected.value) {
    messages.value.push({
      role: 'user',
      content: text,
      timestamp: Date.now(),
      images: images.length > 0 ? [...images] : undefined
    })
    inputMessage.value = ''
    inputImages.value = []
    isTyping.value = true
    autoScrollEnabled.value = true // 用户发送消息，强制开启自动滚动
    scrollToBottom('smooth')

    sendChat(
      text,
      undefined,
      settings.value.model,
      settings.value.apiKey,
      settings.value.baseUrl,
      images.length > 0 ? [...images] : undefined
    )
  } else {
    message.warning('系统连接已断开，请等待重连')
  }
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
  if (!target) return

  // 更新自动滚动状态：如果用户在底部附近，开启自动滚动；否则关闭
  autoScrollEnabled.value = isAtBottom(target)

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
      if (autoScrollEnabled.value) {
        scrollToBottom()
      }
      break

    case 'reasoningChunk':
      appendReasoningChunk(msg.content || '')
      if (autoScrollEnabled.value) {
        scrollToBottom()
      }
      break

    case 'toolCallStart':
      upsertToolStep({
        toolCallId: msg.toolCallId,
        toolName: msg.toolName,
        skillName: msg.skillName,
        arguments: msg.arguments,
        status: 'running',
        isError: false
      })
      if (autoScrollEnabled.value) {
        scrollToBottom()
      }
      break

    case 'toolCallEnd':
      upsertToolStep({
        toolCallId: msg.toolCallId,
        toolName: msg.toolName,
        skillName: msg.skillName,
        arguments: msg.arguments,
        result: msg.result,
        output: msg.result,
        artifacts: msg.artifacts,
        status: msg.isError ? 'error' : 'success',
        isError: !!msg.isError
      })
      if (autoScrollEnabled.value) {
        scrollToBottom()
      }
      break

    case 'chatEnd':
      isTyping.value = false
      const lastMsg = await syncLatestAssistantMessage()
      if (autoScrollEnabled.value) {
        scrollToBottom('smooth')
      }
      if (settings.value.ttsEnabled && lastMsg && lastMsg.content) {
        ttsSpeak(lastMsg.content, String(lastMsg.id ?? ""))
      }
      break
    case 'error':
      isTyping.value = false
      const errorMsg = msg.message || '发生错误'
      failLatestAssistantMessage(errorMsg)
      await syncLatestAssistantMessage()

      // 提取友好提示
      let friendlyMsg = errorMsg
      if (errorMsg.toLowerCase().includes('context size') ||
          errorMsg.toLowerCase().includes('context_length_exceeded')) {
        friendlyMsg = '对话上下文过长，请尝试开启新对话或清理历史记录。'
      }
      message.error(friendlyMsg)

      if (autoScrollEnabled.value) {
        scrollToBottom()
      }
      break

    case 'proactiveGreeting':
      if (msg.content) {
        const msgId = `proactive-${Date.now()}`
        messages.value.push({
          id: msgId,
          role: 'assistant',
          content: msg.content,
          timestamp: Date.now()
        })
        if (autoScrollEnabled.value) {
          scrollToBottom()
        }
        if (settings.value.ttsEnabled) {
          ttsSpeak(msg.content, msgId)
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
        stopAsrListening()
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

const { saveSettings } = useSettings()

async function handleToggleTts() {
  settings.value.ttsEnabled = !settings.value.ttsEnabled
  if (!settings.value.ttsEnabled) {
    ttsStop()
  }
  await saveSettings()
  message.info(settings.value.ttsEnabled ? '语音合成已开启' : '语音合成已关闭')
}

function toggleExportMode() {
  if (turnGroups.value.length === 0) {
    message.warning('暂无可选择的对话轮次')
    return
  }

  if (exportMode.value) {
    preserveScrollPositionForModeSwitch(() => {
      exportMode.value = false
      selectedTurnIds.value = new Set()
    })
    return
  }

  preserveScrollPositionForModeSwitch(() => {
    exportMode.value = true
    selectedTurnIds.value = new Set(turnGroups.value.map((group) => group.id))
  })
}

function toggleTurnSelection(turnId: string) {
  const next = new Set(selectedTurnIds.value)
  if (next.has(turnId)) {
    next.delete(turnId)
  } else {
    next.add(turnId)
  }
  selectedTurnIds.value = next
}

function toggleSelectAllTurns() {
  if (allTurnsSelected.value) {
    selectedTurnIds.value = new Set()
    return
  }
  selectedTurnIds.value = new Set(turnGroups.value.map((group) => group.id))
}

function cancelExportMode() {
  exportMode.value = false
  selectedTurnIds.value = new Set()
}

async function handleCopyMarkdown() {
  if (!selectedMessagesForExport.value.length) {
    message.warning('请先选择至少一轮对话')
    return
  }

  isCopyingMarkdown.value = true
  try {
    await copyConversationMarkdown(selectedMessagesForExport.value)
    message.success('Markdown 已复制到剪贴板')
  } catch (error) {
    const msg = error instanceof Error ? error.message : '复制失败，请稍后重试'
    message.error(msg)
  } finally {
    isCopyingMarkdown.value = false
  }
}

async function handleCopyLongImage() {
  if (!selectedMessagesForExport.value.length) {
    message.warning('请先选择至少一轮对话')
    return
  }

  isCopyingImage.value = true
  try {
    await copyConversationPng(selectedMessagesForExport.value)
    message.success('长图已复制到剪贴板')
  } catch (error) {
    const msg = error instanceof Error ? error.message : '复制失败，请稍后重试'
    message.error(msg)
  } finally {
    isCopyingImage.value = false
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

      <div class="chat-actions">
        <button
          class="action-btn toggle-panel-btn"
          @click="$emit('toggle-panel')"
          title="记忆流光面板"
        >
          <Workflow :size="16" />
          <span>溯源</span>
        </button>

        <template v-if="messages.length > 0">
          <button
            class="action-btn tts-btn"
            :class="{ active: settings.ttsEnabled, playing: ttsPlaying }"
            @click="handleToggleTts"
            :title="settings.ttsEnabled ? '关闭语音' : '开启语音'"
          >
            <Volume2 v-if="settings.ttsEnabled" :size="16" />
            <VolumeX v-else :size="16" />
          </button>

          <button
            class="action-btn clear-btn"
            @click="handleClearHistory"
            title="清空会话"
          >
            <Trash2 :size="14" />
            <span>清空</span>
          </button>

          <button
            class="action-btn export-btn"
            :class="{ active: exportMode }"
            @click="toggleExportMode"
            :title="exportMode ? '退出导出模式' : '选择轮次并复制导出'"
          >
            <Download :size="14" />
            <span>{{ exportMode ? '退出导出' : '导出' }}</span>
          </button>
        </template>
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
    <n-scrollbar ref="scrollRef" class="messages-area" :class="{ 'export-mode': exportMode }" v-if="messages.length > 0" @scroll="handleScroll">
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

        <div
          v-for="turn in turnGroups"
          :key="turn.id"
          class="turn-block"
          :class="{ selected: selectedTurnIds.has(turn.id), 'export-active': exportMode }"
        >
          <label v-if="exportMode" class="turn-selector">
            <input
              type="checkbox"
              :checked="selectedTurnIds.has(turn.id)"
              @change="toggleTurnSelection(turn.id)"
            />
            <span>第 {{ turn.index }} 轮</span>
          </label>

          <ChatMessageComponent
            v-for="item in turn.messages"
            :key="`${turn.id}-${item.originalIndex}`"
            :message="item.message"
            :time-label="formatTime(item.message.timestamp)"
            :class="{ 'loading-message': item.message.isLoading && !item.message.content }"
          />
        </div>



      </div>
    </n-scrollbar>

    <!-- Input Area -->
    <ChatInput
      v-model="inputMessage"
      v-model:images="inputImages"
      :loading="isTyping"
      :asr-enabled="asrConfig.enabled"
      :asr-listening="asrListening"
      :asr-recording="asrRecording"
      :asr-processing="asrProcessing"
      @send="handleSend"
      @toggle-asr="handleToggleAsr"
      @start-push-to-talk="startPushToTalk"
      @stop-push-to-talk="stopPushToTalk"
    />

    <div v-if="exportMode" class="export-toolbar">
      <label class="export-check-all">
        <input
          type="checkbox"
          :checked="allTurnsSelected"
          @change="toggleSelectAllTurns"
        />
        <span>全选</span>
      </label>

      <div class="export-counter">已选择 {{ selectedTurnCount }} 组对话</div>

      <div class="export-actions">
        <button class="export-action secondary" @click="cancelExportMode">取消</button>
        <button
          class="export-action"
          :disabled="selectedTurnCount === 0 || isCopyingMarkdown"
          @click="handleCopyMarkdown"
        >
          {{ isCopyingMarkdown ? '复制中...' : '复制 Markdown' }}
        </button>
        <button
          class="export-action primary"
          :disabled="selectedTurnCount === 0 || isCopyingImage"
          @click="handleCopyLongImage"
        >
          {{ isCopyingImage ? '生成中...' : '复制长图' }}
        </button>
      </div>
    </div>
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

/* Mobile: Increase z-index to avoid being covered by sidebar */
@media (max-width: 767px) {
  .chat-header {
    z-index: 1000;
    pointer-events: none;
  }

  .chat-header > * {
    pointer-events: auto;
  }

  .connection-status,
  .chat-actions,
  .action-btn {
    pointer-events: auto !important;
  }
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

.tts-btn {
  margin-right: 8px;
}

.tts-btn.active {
  color: var(--color-primary);
  background: rgba(52, 211, 153, 0.1);
  border-color: rgba(52, 211, 153, 0.2);
}

.tts-btn.playing {
  animation: tts-pulse 1.5s ease-in-out infinite;
}

@keyframes tts-pulse {
  0%, 100% { transform: scale(1); box-shadow: 0 0 0 rgba(52, 211, 153, 0); }
  50% { transform: scale(1.1); box-shadow: 0 0 10px rgba(52, 211, 153, 0.3); }
}

.clear-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.2);
}

.export-btn {
  margin-left: 8px;
}

.export-btn:hover {
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  border-color: rgba(56, 189, 248, 0.28);
}

.export-btn.active {
  background: rgba(56, 189, 248, 0.16);
  color: #38bdf8;
  border-color: rgba(56, 189, 248, 0.36);
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


.turn-block {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.turn-block.export-active {
  border: 1px solid rgba(56, 189, 248, 0.22);
  border-radius: 12px;
  padding: 10px 10px 6px;
  background: color-mix(in srgb, var(--color-surface-elevated) 40%, transparent);
}

.turn-selector {
  position: relative;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(56, 189, 248, 0.28);
  background: rgba(15, 23, 42, 0.45);
  color: #7dd3fc;
  font-size: 12px;
  user-select: none;
}

.turn-selector input {
  width: 16px;
  height: 16px;
}

.turn-block.selected {
  border-color: rgba(56, 189, 248, 0.48);
  box-shadow: 0 0 0 1px rgba(56, 189, 248, 0.2) inset;
}

@media (max-width: 768px) {
  .messages-content {
    width: calc(100% - 16px);
    padding: 60px 0 150px;
  }

  .turn-block.export-active {
    padding: 8px 8px 4px;
  }
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

/* 加载消息样式 */
.loading-message {
  /* 确保加载消息正常显示 */
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.messages-area {
  position: relative;
  z-index: 1;
  pointer-events: none;
}

.messages-area.export-mode .messages-content {
  padding-bottom: 270px;
}

.messages-area :deep(.n-scrollbar-container) {
  pointer-events: auto;
}

.export-toolbar {
  position: absolute;
  left: 50%;
  bottom: 84px;
  transform: translateX(-50%);
  width: min(1050px, calc(100% - 48px));
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid var(--color-outline);
  background: color-mix(in srgb, var(--color-surface-elevated) 88%, transparent);
  backdrop-filter: blur(14px);
  z-index: 30;
}

.export-check-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text);
  font-size: 13px;
}

.export-counter {
  color: var(--color-text-dim);
  font-size: 13px;
}

.export-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.export-action {
  border: 1px solid var(--color-outline);
  background: rgba(255, 255, 255, 0.05);
  color: var(--color-text);
  border-radius: 18px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.export-action.secondary:hover {
  border-color: rgba(148, 163, 184, 0.55);
}

.export-action.primary {
  border-color: rgba(56, 189, 248, 0.45);
  background: rgba(56, 189, 248, 0.16);
  color: #38bdf8;
}

.export-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .export-toolbar {
    width: calc(100% - 16px);
    bottom: 72px;
    flex-wrap: wrap;
    gap: 8px;
    padding: 10px;
  }

  .export-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
