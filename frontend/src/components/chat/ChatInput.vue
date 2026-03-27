<script setup lang="ts">
import { Send, Sparkles, Mic, MicOff, Loader2 } from 'lucide-vue-next'

defineProps<{ 
  modelValue: string
  loading: boolean
  disabled?: boolean
  asrEnabled?: boolean
  asrListening?: boolean
  asrRecording?: boolean
  asrProcessing?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'send'): void
  (e: 'toggleAsr'): void
  (e: 'startPushToTalk'): void
  (e: 'stopPushToTalk'): void
}>()

function handleInput(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}

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
      <div class="input-icon">
        <Sparkles :size="18" />
      </div>
      
      <input
        :value="modelValue"
        type="text"
        class="input-field"
        placeholder="与灵枢对话..."
        @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
        @keydown="handleInput"
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
          <Send :size="18" :class="{ spinning: loading }" />
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
  padding: 20px 24px;
  background: linear-gradient(to top, var(--color-background), transparent);
  pointer-events: none;
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 800px;
  margin: 0 auto;
  padding: 12px 16px;
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

.input-field {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--color-text);
  line-height: 1.5;
}

.input-field::placeholder {
  color: var(--color-text-dim);
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

.mic-btn:hover:not(:disabled) {
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

.spinning {
  animation: spin 1s linear infinite;
}

.spin {
  animation: spin 1s linear infinite;
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
