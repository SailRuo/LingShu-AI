<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { Send, ArrowUp, Sparkles, Mic, MicOff, Loader2, Brain } from 'lucide-vue-next'

const textareaRef = ref<HTMLTextAreaElement | null>(null)

const props = defineProps<{ 
  modelValue: string
  loading: boolean
  disabled?: boolean
  asrEnabled?: boolean
  asrListening?: boolean
  asrRecording?: boolean
  asrProcessing?: boolean
  enableThinking?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'send'): void
  (e: 'toggleAsr'): void
  (e: 'startPushToTalk'): void
  (e: 'stopPushToTalk'): void
  (e: 'toggleThinking'): void
}>()

function adjustTextareaHeight() {
  const textarea = textareaRef.value
  if (!textarea) return
  
  // 重置高度以获取正确的 scrollHeight
  textarea.style.height = 'auto'
  
  // 设置新的高度，最大 300px
  const newHeight = Math.min(textarea.scrollHeight, 300)
  textarea.style.height = `${newHeight}px`
  
  // 动态控制滚动条显示：只有当内容高度超过最大高度时才显示滚动条
  if (textarea.scrollHeight > 300) {
    textarea.style.overflowY = 'auto'
  } else {
    textarea.style.overflowY = 'hidden'
  }
}

function handleInput(e: Event) {
  const target = e.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
  adjustTextareaHeight()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}

// 监听输入值变化，自动调整高度
watch(() => props.modelValue, () => {
  nextTick(() => {
    adjustTextareaHeight()
  })
}, { immediate: true })

function handleMicMouseDown() {
  emit('startPushToTalk')
}

function handleMicMouseUp() {
  emit('stopPushToTalk')
}
</script>

<template>
  <div class="input-container">
    <div class="input-wrapper">
      <button 
        class="thinking-btn"
        :class="{ active: enableThinking }"
        :title="enableThinking ? '已启用推理模式' : '启用推理模式'"
        @click="emit('toggleThinking')"
      >
        <Brain :size="18" />
      </button>
      
      <textarea
        ref="textareaRef"
        :value="modelValue"
        class="input-field"
        placeholder="与灵枢对话...（Shift+Enter 换行）"
        rows="1"
        @input="handleInput"
        @keydown="handleKeydown"
      />
      
      <div class="action-buttons">
        <button 
          v-if="asrEnabled"
          class="mic-btn"
          :class="{ 
            active: asrListening,
            recording: asrRecording,
            processing: asrProcessing
          }"
          :disabled="disabled || asrProcessing"
          @click="emit('toggleAsr')"
          @mousedown="handleMicMouseDown"
          @mouseup="handleMicMouseUp"
          @mouseleave="handleMicMouseUp"
          :title="asrListening ? '点击关闭语音输入' : '点击开启语音输入'"
        >
          <Loader2 v-if="asrProcessing" :size="18" class="spin" />
          <MicOff v-else-if="!asrListening" :size="18" />
          <Mic v-else :size="18" :class="{ pulse: asrRecording }" />
        </button>
        
        <button 
          class="send-btn"
          :disabled="!modelValue.trim() || loading || disabled"
          @click="emit('send')"
        >
          <div v-if="loading" class="loading-dot"></div>
          <ArrowUp v-else :size="20" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.input-container {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20px 0;
  background: linear-gradient(to top, var(--color-background), transparent);
  pointer-events: auto;
  display: flex;
  justify-content: center;
  z-index: 10;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  width: calc(100% - 48px);
  max-width: 1050px;
  margin: 0 auto;
  padding: 12px 24px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 16px;
  pointer-events: auto;
  transition: all 0.2s ease;
}

.input-wrapper:focus-within {
  border-color: var(--color-primary);
  background: var(--color-surface-elevated);
}

.input-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: var(--color-primary-dim);
  border-radius: 10px;
  color: var(--color-primary);
}

.thinking-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: transparent;
  border: 1px solid var(--color-outline);
  border-radius: 10px;
  color: var(--color-text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
}

.thinking-btn:hover:not(.active) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.thinking-btn.active {
  background: var(--color-primary-dim);
  border-color: var(--color-primary);
  color: var(--color-primary);
  box-shadow: 0 0 12px var(--color-primary-dim);
}

.input-field {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--color-text);
  line-height: 1.5;
  resize: none;
  min-height: 24px;
  max-height: 300px;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 0;
}

.input-field::placeholder {
  color: var(--color-text-dim);
}

.input-field::-webkit-scrollbar {
  width: 6px;
}

.input-field::-webkit-scrollbar-track {
  background: transparent;
}

.input-field::-webkit-scrollbar-thumb {
  background: var(--color-outline);
  border-radius: 3px;
}

.input-field::-webkit-scrollbar-thumb:hover {
  background: var(--color-primary);
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mic-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: transparent;
  border: 1px solid var(--color-outline);
  border-radius: 10px;
  color: var(--color-text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
}

.mic-btn:hover:not(:disabled):not(.active):not(.recording) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.mic-btn.active {
  background: var(--color-primary-dim);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.mic-btn.recording {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text-inverse);
  box-shadow: 0 0 20px var(--color-primary-dim);
}

.mic-btn.processing {
  opacity: 0.7;
}

.mic-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: var(--color-primary);
  border: none;
  border-radius: 10px;
  color: var(--color-text-inverse);
  cursor: pointer;
  transition: all 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 0 20px var(--color-primary-dim);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading-dot {
  width: 12px;
  height: 12px;
  background: var(--color-text-inverse);
  border-radius: 50%;
  animation: breathing-dot 1.5s ease-in-out infinite;
}

@keyframes breathing-dot {
  0%, 100% { 
    transform: scale(1);
    opacity: 1;
    box-shadow: 0 0 0 rgba(255, 255, 255, 0);
  }
  50% { 
    transform: scale(1.5);
    opacity: 0.7;
    box-shadow: 0 0 20px rgba(255, 255, 255, 0.5);
  }
}

.spinning {
  animation: spin 1s linear infinite;
}

.spin {
  animation: spin 1s linear infinite;
}

.breathing {
  animation: breathing 1.5s ease-in-out infinite;
}

@keyframes breathing {
  0%, 100% { 
    transform: scale(1);
    opacity: 1;
  }
  50% { 
    transform: scale(1.3);
    opacity: 0.6;
  }
}

.pulse {
  animation: pulse 1s ease-in-out infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.2); }
}
</style>
