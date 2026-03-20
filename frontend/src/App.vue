<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { 
  NConfigProvider, NGlobalStyle, NLayout, NLayoutHeader, NLayoutContent, 
  NLayoutFooter, NInput, NButton, NScrollbar, NAvatar, NText,
  darkTheme, useMessage
} from 'naive-ui'
import { SendOutlined } from '@vicons/antd'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()
const inputMessage = ref('')
const messages = ref<{ role: 'user' | 'assistant', content: string }[]>([])
const isTyping = ref(false)
const scrollbarRef = ref<any>(null)

const scrollToBottom = async () => {
  await nextTick()
  if (scrollbarRef.value) {
    scrollbarRef.value.scrollTo({ position: 'bottom', silent: true })
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isTyping.value) return

  const userMsg = inputMessage.value
  messages.value.push({ role: 'user', content: userMsg })
  inputMessage.value = ''
  isTyping.value = true
  scrollToBottom()

  // Add a placeholder for the assistant message
  const assistantMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })

  try {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: userMsg
    })

    if (!response.body) throw new Error('No body')
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      
      const chunk = decoder.decode(value, { stream: true })
      messages.value[assistantMsgIndex].content += chunk
      scrollToBottom()
    }
  } catch (err) {
    console.error('Error streaming chat:', err)
    messages.value[assistantMsgIndex].content = 'ERROR: Failed to connect to backend.'
  } finally {
    isTyping.value = false
  }
}

onMounted(() => {
  messages.value.push({ role: 'assistant', content: '你好，我是“灵枢”。我能为你做些什么？' })
})
</script>

<template>
  <n-config-provider :theme="darkTheme">
    <n-global-style />
    <n-layout full-screen class="app-container">
      <n-layout-header bordered class="header">
        <div class="logo">
          <span class="logo-emoji">🎐</span>
          <n-text strong size="large">灵枢 (LingShu-AI)</n-text>
        </div>
      </n-layout-header>

      <n-layout-content content-style="padding: 24px;" class="chat-area">
        <n-scrollbar ref="scrollbarRef">
          <div class="message-list">
            <div 
              v-for="(msg, index) in messages" 
              :key="index"
              :class="['message-item', msg.role]"
            >
              <n-avatar 
                round 
                size="medium" 
                :style="{ backgroundColor: msg.role === 'user' ? '#4f46e5' : '#10b981' }"
              >
                {{ msg.role === 'user' ? 'ME' : 'LS' }}
              </n-avatar>
              <div class="message-bubble">
                <div v-html="md.render(msg.content)"></div>
              </div>
            </div>
          </div>
        </n-scrollbar>
      </n-layout-content>

      <n-layout-footer bordered class="footer">
        <div class="input-wrapper">
          <n-input
            v-model:value="inputMessage"
            type="textarea"
            placeholder="与灵枢交流..."
            :autosize="{ minRows: 1, maxRows: 5 }"
            @keyup.enter.prevent="sendMessage"
            class="chat-input"
          />
          <n-button 
            type="primary" 
            circle 
            size="large" 
            @click="sendMessage"
            :loading="isTyping"
            class="send-btn"
          >
            <template #icon><send-outlined /></template>
          </n-button>
        </div>
      </n-layout-footer>
    </n-layout>
  </n-config-provider>
</template>

<style scoped>
.app-container {
  background: radial-gradient(circle at top left, #1a1a2e, #16213e);
}

.header {
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-emoji {
  font-size: 24px;
}

.chat-area {
  height: calc(100vh - 140px);
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.message-item {
  display: flex;
  gap: 16px;
  max-width: 85%;
}

.message-item.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 15px;
  line-height: 1.6;
  background: rgba(255, 255, 255, 0.08);
}

.user .message-bubble {
  background: #4f46e5;
  color: white;
}

.assistant .message-bubble {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.footer {
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
}

.input-wrapper {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  border-radius: 20px;
}

.send-btn {
  margin-bottom: 4px;
}
</style>
