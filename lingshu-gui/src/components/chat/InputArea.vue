<script setup lang="ts">
import { ref, onUnmounted, nextTick } from 'vue';
import IconFaceSmileFill from '@arco-design/web-vue/es/icon/icon-face-smile-fill';
import IconFolder from '@arco-design/web-vue/es/icon/icon-folder';
import IconScissor from '@arco-design/web-vue/es/icon/icon-scissor';
import IconVoice from '@arco-design/web-vue/es/icon/icon-voice';
import IconDown from '@arco-design/web-vue/es/icon/icon-down';

import IconClose from '@arco-design/web-vue/es/icon/icon-close';
import IconFile from '@arco-design/web-vue/es/icon/icon-file';

const emit = defineEmits<{
  send: [content: string, attachments: any[]];
}>();

const inputText = ref('');
const inputHeight = ref(180);
const isResizing = ref(false);
const startY = ref(0);
const startHeight = ref(0);

// 附件列表
const attachments = ref<{
  id: string;
  type: 'image' | 'file';
  url: string; // 预览地址
  file: File;
  name: string;
  size: number;
}[]>([]);

const fileInputRef = ref<HTMLInputElement | null>(null);
const textareaRef = ref<HTMLTextAreaElement | null>(null);

function handleSend() {
  const text = inputText.value.trim();
  if (!text && attachments.value.length === 0) return;
  
  // 发送文本和附件副本，并清空当前状态
  emit('send', text, [...attachments.value]);
  inputText.value = '';
  attachments.value = [];
  nextTick(() => textareaRef.value?.focus());
}

// 处理粘贴事件
function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items;
  if (!items) return;

  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    if (item.kind === 'file') {
      const file = item.getAsFile();
      if (file) {
        addFile(file);
      }
    }
  }
}

async function handleFileSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files;
  if (!files) return;
  for (let i = 0; i < files.length; i++) {
    addFile(files[i]);
  }
  if (fileInputRef.value) fileInputRef.value.value = '';
  nextTick(() => textareaRef.value?.focus());
}

function addFile(file: File) {
  const isImage = file.type.startsWith('image/');
  
  const id = Math.random().toString(36).substring(2, 9);
  
  if (isImage) {
    const url = URL.createObjectURL(file);
    attachments.value.push({
      id,
      type: 'image',
      url,
      file,
      name: file.name,
      size: file.size
    });
  } else {
    attachments.value.push({
      id,
      type: 'file',
      url: '',
      file,
      name: file.name,
      size: file.size
    });
  }
  nextTick(() => textareaRef.value?.focus());
}

function removeAttachment(id: string) {
  const idx = attachments.value.findIndex(a => a.id === id);
  if (idx !== -1) {
    const item = attachments.value[idx];
    if (item.url) URL.revokeObjectURL(item.url);
    attachments.value.splice(idx, 1);
  }
  nextTick(() => textareaRef.value?.focus());
}

function formatSize(bytes: number) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

function startResize(e: MouseEvent) {
  isResizing.value = true;
  startY.value = e.clientY;
  startHeight.value = inputHeight.value;
  document.addEventListener('mousemove', handleResize);
  document.addEventListener('mouseup', stopResize);
  document.body.style.cursor = 'row-resize';
}

function handleResize(e: MouseEvent) {
  if (!isResizing.value) return;
  const deltaY = startY.value - e.clientY;
  const newHeight = startHeight.value + deltaY;
  
  // 优化拉伸限制：
  // 1. 最小高度 120px
  // 2. 最大高度限制在 300px，或者不超过屏幕高度的 40% (取较小值)
  const maxHeight = Math.min(300, window.innerHeight * 0.4);
  inputHeight.value = Math.min(maxHeight, Math.max(120, newHeight));
}

function stopResize() {
  isResizing.value = false;
  document.removeEventListener('mousemove', handleResize);
  document.removeEventListener('mouseup', stopResize);
  document.body.style.cursor = '';
}

onUnmounted(() => {
  stopResize();
});
</script>

<template>
  <footer 
    class="input-container" 
    :style="{ height: inputHeight + 'px' }"
  >
    <div class="input-card">
      <!-- Resize Handle -->
      <div class="resize-handle" @mousedown="startResize"></div>
      
      <div class="input-body">
        <div v-if="attachments.length > 0" class="attachments-preview">
          <div 
            v-for="item in attachments" 
            :key="item.id" 
            class="attachment-item"
            :class="item.type"
          >
            <div class="delete-overlay" @click="removeAttachment(item.id)">
              <IconClose size="12" />
            </div>
            
            <img v-if="item.type === 'image'" :src="item.url" class="preview-img" />
            <div v-else class="file-card">
              <div class="file-icon">
                <IconFile :size="24" />
              </div>
              <div class="file-info">
                <div class="file-name text-ellipsis">{{ item.name }}</div>
                <div class="file-size">{{ formatSize(item.size) }}</div>
              </div>
            </div>
          </div>
        </div>
        
        <textarea
          ref="textareaRef"
          v-model="inputText"
          class="text-input"
          placeholder=""
          @keydown="handleKeydown"
          @paste="handlePaste"
        />
      </div>
      
      <div class="input-footer">
        <div class="toolbar">
          <button class="tool-btn" title="表情">
            <IconFaceSmileFill :size="18" />
          </button>
          <button class="tool-btn" title="文件" @click="fileInputRef?.click()">
            <IconFolder :size="18" />
          </button>
          <div class="tool-group">
            <button class="tool-btn" title="截图">
              <IconScissor :size="18" />
            </button>
            <IconDown :size="10" class="down-arrow" />
          </div>
          <button class="tool-btn" title="语音">
            <IconVoice :size="18" />
          </button>
        </div>
        
        <button
          class="send-btn"
          :disabled="!inputText.trim() && attachments.length === 0"
          @click="handleSend"
        >
          发送
        </button>
      </div>
      
      <input 
        ref="fileInputRef"
        type="file" 
        multiple 
        style="display: none" 
        @change="handleFileSelect"
      />
    </div>
  </footer>
</template>

<style scoped>
.input-container {
  padding: 0 8px 8px;
  background-color: transparent;
  flex-shrink: 0;
  position: relative;
  /* 删掉 transition，它是卡顿的元凶 */
}

.input-card {
  height: 100%; /* 让卡片充满已设置高度的容器 */
  background-color: transparent; /* 既然要透明，那就彻底透明 */
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: border-color var(--transition-fast);
  box-shadow: var(--shadow-sm);
  position: relative;
}

.resize-handle {
  position: absolute;
  top: -4px;
  left: 0;
  right: 0;
  height: 8px;
  cursor: row-resize;
  z-index: 10;
}

.resize-handle:hover::after {
  content: "";
  position: absolute;
  top: 3px;
  left: 50%;
  transform: translateX(-50%);
  width: 40px;
  height: 2px;
  background-color: var(--color-primary);
  border-radius: 1px;
  opacity: 0.5;
}

.input-card:focus-within {
  border-color: var(--border-color-dark);
}

.input-body {
  flex: 1;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.attachments-preview {
  display: flex;
  flex-wrap: nowrap;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  overflow-x: auto;
  border-bottom: 1px solid var(--border-color);
}

/* 隐藏附件区域的默认滚动条，使用极细样式 */
.attachments-preview::-webkit-scrollbar {
  height: 4px;
}
.attachments-preview::-webkit-scrollbar-thumb {
  background: var(--text-placeholder);
  border-radius: 2px;
}

.attachment-item {
  position: relative;
  background: var(--bg-hover);
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  flex-shrink: 0;
  transition: all 0.2s;
}

.attachment-item.image {
  width: 80px;
  height: 80px;
}

.attachment-item.file {
  width: 180px;
  height: 60px;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file-card {
  display: flex;
  align-items: center;
  padding: 8px;
  gap: 8px;
  height: 100%;
}

.file-icon {
  width: 36px;
  height: 36px;
  background: var(--bg-chat-window);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.file-size {
  font-size: 11px;
  color: var(--text-tertiary);
}

.delete-overlay {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 5;
}

.attachment-item:hover .delete-overlay {
  opacity: 1;
}

.text-input {
  flex: 1;
  width: 100%;
  border: none;
  font-size: var(--font-size-md);
  line-height: 1.5;
  color: var(--text-primary);
  resize: none;
  font-family: inherit;
  outline: none;
  background: transparent;
  min-height: 40px;
}

.input-footer {
  height: 40px;
  padding: 0 12px 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px; /* 16px -> 8px，更紧凑 */
}

.tool-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  background: none;
  border: none;
  padding: 6px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tool-btn:hover {
  color: var(--text-primary);
  background-color: var(--bg-hover);
  border-radius: var(--radius-sm);
}

.tool-group {
  display: flex;
  align-items: center;
  gap: 2px;
}

.down-arrow {
  color: var(--text-placeholder);
}

.send-btn {
  height: 32px;
  padding: 0 20px;
  border-radius: var(--radius-sm);
  background-color: var(--bg-hover);
  color: var(--color-primary);
  font-size: var(--font-size-md);
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.send-btn:hover:not(:disabled) {
  background-color: var(--bg-selected);
}

.send-btn:not(:disabled) {
  background-color: var(--bg-hover);
  color: var(--color-primary);
}

.send-btn:disabled {
  color: var(--text-placeholder);
  opacity: 0.5;
  cursor: not-allowed;
}
</style>


