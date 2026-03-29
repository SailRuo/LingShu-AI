<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { Send, ArrowUp, Sparkles, Mic, MicOff, Loader2, Brain, X } from 'lucide-vue-next'

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
  images?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'update:images', v: string[]): void
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

// 压缩图片
function compressImage(file: File, maxWidth = 1024, quality = 0.8): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = (e) => {
      const img = new Image()
      img.src = e.target?.result as string
      img.onload = () => {
        const canvas = document.createElement('canvas')
        let width = img.width
        let height = img.height
        if (width > maxWidth) {
          height = Math.round((height * maxWidth) / width)
          width = maxWidth
        }
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        ctx?.drawImage(img, 0, 0, width, height)
        resolve(canvas.toDataURL('image/jpeg', quality))
      }
      img.onerror = reject
    }
    reader.onerror = reject
  })
}

// 处理粘贴事件
async function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return

  const newImages: string[] = []
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.type.indexOf('image') !== -1) {
      const file = item.getAsFile()
      if (file) {
        try {
          const base64 = await compressImage(file)
          newImages.push(base64)
        } catch (err) {
          console.error('图片压缩失败:', err)
        }
      }
    }
  }

  if (newImages.length > 0) {
    e.preventDefault() // 如果粘贴了图片，阻止默认粘贴行为（避免在某些浏览器中出现奇怪的字符）
    const currentImages = props.images || []
    emit('update:images', [...currentImages, ...newImages])
  }
}

function removeImage(index: number) {
  const currentImages = [...(props.images || [])]
  currentImages.splice(index, 1)
  emit('update:images', currentImages)
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
    <div class="input-wrapper" :class="{ 'has-images': images && images.length > 0 }">
      <!-- 图片预览区 -->
      <div v-if="images && images.length > 0" class="image-preview-container">
        <div v-for="(img, index) in images" :key="index" class="image-preview-item">
          <img :src="img" alt="Pasted image" />
          <button class="remove-image-btn" @click="removeImage(index)">
            <X :size="14" />
          </button>
        </div>
      </div>

      <div class="input-row">
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
          placeholder="与灵枢对话...（支持粘贴图片，Shift+Enter 换行）"
          rows="1"
          @input="handleInput"
          @keydown="handleKeydown"
          @paste="handlePaste"
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
            :disabled="(!modelValue.trim() && (!images || images.length === 0)) || loading || disabled"
            @click="emit('send')"
          >
            <div v-if="loading" class="loading-dot"></div>
            <ArrowUp v-else :size="20" />
          </button>
        </div>
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
  flex-direction: column;
  gap: 12px;
  width: calc(100% - 48px);
  max-width: 1050px;
  margin: 0 auto;
  padding: 12px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 16px;
  pointer-events: auto;
  transition: all 0.2s ease;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  width: 100%;
}

.image-preview-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-outline);
}

.image-preview-item {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--color-outline);
}

.image-preview-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-image-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: white;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  transition: background 0.2s;
}

.remove-image-btn:hover {
  background: rgba(255, 0, 0, 0.8);
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
  position: relative;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 0 20px var(--color-primary-dim);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--color-outline);
}

.send-btn:disabled::after {
  content: 'AI 正在回复';
  position: absolute;
  bottom: 100%;
  right: 0;
  background: var(--color-surface);
  color: var(--color-text-dim);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  margin-bottom: 8px;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.2s ease;
  pointer-events: none;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.send-btn:disabled:hover::after {
  opacity: 1;
  transform: translateY(0);
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
