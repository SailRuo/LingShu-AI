<script setup lang="ts">
import { ref } from 'vue';
import IconFaceSmileFill from '@arco-design/web-vue/es/icon/icon-face-smile-fill';
import IconAttachment from '@arco-design/web-vue/es/icon/icon-attachment';
import IconImage from '@arco-design/web-vue/es/icon/icon-image';
import IconScissor from '@arco-design/web-vue/es/icon/icon-scissor';
import IconVoice from '@arco-design/web-vue/es/icon/icon-voice';

const emit = defineEmits<{
  send: [content: string];
}>();

const inputText = ref('');

function handleSend() {
  const text = inputText.value.trim();
  if (!text) return;
  emit('send', text);
  inputText.value = '';
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}
</script>

<template>
  <footer class="input-area">
    <div class="toolbar">
      <button class="tool-btn" title="表情">
        <IconFaceSmileFill :size="20" />
      </button>
      <button class="tool-btn" title="文件">
        <IconAttachment :size="20" />
      </button>
      <button class="tool-btn" title="图片">
        <IconImage :size="20" />
      </button>
      <button class="tool-btn" title="截图">
        <IconScissor :size="20" />
      </button>
      <button class="tool-btn" title="语音">
        <IconVoice :size="20" />
      </button>
    </div>
    <div class="input-wrapper">
      <textarea
        ref="textareaEl"
        v-model="inputText"
        class="text-input"
        placeholder=""
        @keydown="handleKeydown"
      />
      <div class="footer-actions">
        <span class="tip">按下 Enter 发送，Shift+Enter 换行</span>
        <button
          class="send-btn"
          :disabled="!inputText.trim()"
          @click="handleSend"
        >
          发送(S)
        </button>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.input-area {
  background-color: #f5f5f5;
  border-top: 1px solid #e5e5e5;
  padding: 0 20px 12px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  height: 200px;
}

.toolbar {
  display: flex;
  gap: 12px;
  padding: 10px 0;
}

.tool-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #515151;
  transition: all var(--transition-fast);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
}

.tool-btn:hover {
  color: #000;
}

.input-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.text-input {
  flex: 1;
  width: 100%;
  padding: 0;
  background-color: transparent;
  border: none;
  font-size: 15px;
  line-height: 1.6;
  color: #000;
  resize: none;
  font-family: inherit;
  outline: none;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.tip {
  font-size: 12px;
  color: #999;
}

.send-btn {
  height: 32px;
  padding: 0 24px;
  border-radius: 4px;
  background-color: #e9e9e9;
  color: #07c160;
  font-size: 14px;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.send-btn:hover:not(:disabled) {
  background-color: #d2d2d2;
}

.send-btn:disabled {
  color: #c0c0c0;
  cursor: not-allowed;
}
</style>
