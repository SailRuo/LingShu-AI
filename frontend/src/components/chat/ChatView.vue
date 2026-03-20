<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { NScrollbar } from 'naive-ui'
import { useChat } from '@/composables/useChat'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { Sparkles } from 'lucide-vue-next'

const { messages, inputMessage, isTyping, welcomeGreeting, initWelcome, sendMessage, formatTime } = useChat()

const scrollRef = ref<any>(null)

function scrollToBottom() {
  nextTick(() => {
    scrollRef.value?.scrollTo({ top: 999999, behavior: 'smooth' })
  })
}

function handleSend() {
  sendMessage(scrollToBottom)
}

function handleQuickAction(text: string) {
  inputMessage.value = text
  handleSend()
}

onMounted(() => {
  initWelcome()
})
</script>

<template>
  <div class="chat-view">
    <!-- Welcome Section -->
    <div class="welcome-section" v-if="messages.length === 0">
      <div class="welcome-content">
        <div class="soul-orb" :class="{ thinking: isTyping }">
          <div class="orb-core"></div>
          <div class="orb-ring"></div>
          <div class="orb-glow"></div>
        </div>
        <h1 class="welcome-title">{{ welcomeGreeting || '欢迎回来' }}</h1>
        <p class="welcome-subtitle">开始与灵枢对话，探索你的数字伴侣</p>
        
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
    <n-scrollbar ref="scrollRef" class="messages-area" v-if="messages.length > 0">
      <div class="messages-content">
        <ChatMessage
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
