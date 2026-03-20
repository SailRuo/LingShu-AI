<script setup lang="ts">
import { Send, Sparkles, Mic } from 'lucide-vue-next'

defineProps<{ modelValue: string; loading: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'send'): void
}>()

function handleInput(e: Event) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
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
      
      <button 
        class="send-btn"
        :disabled="!modelValue.trim() || loading"
        @click="emit('send')"
      >
        <Send :size="18" :class="{ spinning: loading }" />
      </button>
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

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
