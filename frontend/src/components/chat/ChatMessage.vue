<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import { FileText, Brain, Loader2 } from 'lucide-vue-next'
import type { ChatMessage, ChatMessageSegment, ChatToolSegment, ChatReasoningSegment } from '@/types'

const props = defineProps<{
  message: ChatMessage
  timeLabel: string
  isLastUserMessage?: boolean  // 是否是最后一条用户消息（用于显示加载状态）
}>()

const md = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  typographer: true
})

function processContent(content: string): string {
  let processed = content ?? ''
  const markdownMatch = processed.match(/^markdown#?\s*/)
  if (markdownMatch) {
    processed = processed.slice(markdownMatch[0].length).trim()
  }
  return processed
}

function safeToolName(name?: string): string {
  if (!name) return '工具调用'
  if (name === 'executeCommand') return '命令执行'
  if (name === 'readLocalFile') return '文件读取'
  return name
}

function formatToolArguments(raw?: string): string {
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed === 'string') return parsed
    if (parsed && typeof parsed === 'object') {
      if (typeof parsed.command === 'string') return parsed.command
      if (typeof parsed.path === 'string') return parsed.path
      return JSON.stringify(parsed, null, 2)
    }
    return raw
  } catch {
    return raw
  }
}

function formatToolResult(raw?: string): string {
  return raw?.trim() || ''
}

function getToolStepStatus(step: ChatToolSegment): 'running' | 'success' | 'error' {
  if (step.status) return step.status
  if (step.isError) return 'error'
  if (step.result?.trim()) return 'success'
  return 'running'
}

function renderContent(content: string): string {
  return md.render(processContent(content))
}

const renderedContent = computed(() => renderContent(props.message.content))

const displaySegments = computed<ChatMessageSegment[]>(() => {
  if (props.message.role !== 'assistant') {
    return []
  }

  if (Array.isArray(props.message.segments) && props.message.segments.length > 0) {
    return props.message.segments
  }

  // 如果是加载状态且没有内容，返回空数组
  if (props.message.isLoading && !props.message.content) {
    return []
  }

  if (props.message.content) {
    return [{
      type: 'text',
      content: props.message.content,
      timestamp: props.message.timestamp
    }]
  }

  return []
})
</script>

<template>
  <div class="message-row" :class="[message.role, { 'is-loading': message.role === 'assistant' && message.isLoading && !message.content && !displaySegments.length }]">
    <!-- AI 加载状态：只显示加载动画，不显示气泡框 -->
    <div v-if="message.role === 'assistant' && message.isLoading && !message.content && !displaySegments.length" class="assistant-loading-container">
      <div class="loading-stars">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>
    
    <!-- 正常消息气泡框 -->
    <div v-else class="message-bubble">
      <!-- 渲染用户发送的图片 -->
      <div v-if="message.role === 'user' && message.images && message.images.length > 0" class="message-images">
        <img v-for="(img, index) in message.images" :key="index" :src="img" alt="User uploaded image" class="message-image" />
      </div>

      <template v-if="message.role === 'assistant' && displaySegments.length">
        <div
          v-for="(segment, index) in displaySegments"
          :key="segment.type === 'tool' ? (segment.toolCallId || segment.id || `${segment.toolName || 'tool'}-${index}`) : segment.type === 'reasoning' ? `reasoning-${index}` : `text-${index}`"
          class="message-segment"
        >
          <div v-if="segment.type === 'reasoning'" class="reasoning-block">
            <div class="reasoning-header">
              <Brain :size="14" />
              <span>推理过程</span>
            </div>
            <div class="reasoning-content" v-html="renderContent(segment.content)"></div>
          </div>

          <div v-else-if="segment.type === 'tool'" class="tool-step">
            <div class="tool-step-header">
              <div class="tool-step-title">
                <FileText :size="14" />
                <span>{{ index + 1 }}. {{ safeToolName(segment.toolName) }}</span>
              </div>
              <span v-if="getToolStepStatus(segment) === 'error'" class="tool-step-status error">失败</span>
              <span v-else-if="getToolStepStatus(segment) === 'running'" class="tool-step-status running">运行中</span>
              <span v-else class="tool-step-status success">完成</span>
            </div>

            <div v-if="formatToolArguments(segment.arguments)" class="tool-block">
              <div class="tool-block-label">命令 / 参数</div>
              <pre class="tool-block-content"><code>{{ formatToolArguments(segment.arguments) }}</code></pre>
            </div>

            <div v-if="formatToolResult(segment.result)" class="tool-block">
              <div class="tool-block-label">结果</div>
              <pre class="tool-block-content" :class="{ error: segment.isError }"><code>{{ formatToolResult(segment.result) }}</code></pre>
            </div>
          </div>

          <div
            v-else-if="segment.content"
            class="message-content"
            v-html="renderContent(segment.content)"
          ></div>
        </div>
      </template>

      <div v-else-if="message.content" class="message-content" v-html="renderedContent"></div>
    </div>

    <div class="message-meta">
      <span class="meta-role">{{ message.role === 'assistant' ? '灵枢' : '你' }}</span>
      <span class="meta-dot">·</span>
      <span class="meta-time">{{ timeLabel }}</span>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 80%;
}

.message-row.assistant {
  align-self: flex-start;
}

.message-row.user {
  align-self: flex-end;
  align-items: flex-end;
}

/* 用户消息入场动画 */
.message-row.user {
  animation: message-in 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

/* AI 消息入场动画（加载完成后） */
.message-row.assistant:not(.is-loading) {
  animation: message-in 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* AI 加载容器 */
.assistant-loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 20px;
}

.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.message-images + .message-content {
  margin-top: 4px;
}

.message-image {
  max-width: 300px;
  max-height: 300px;
  border-radius: 8px;
  object-fit: contain;
  border: 1px solid var(--color-outline);
}

.message-bubble {
  padding: 16px 20px;
  border-radius: 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  width: 100%;
  position: relative;
  overflow: hidden;
}

.message-row.assistant .message-bubble {
  background: var(--color-bubble-ai-bg);
  border: 1px solid var(--color-bubble-ai-border);
  border-bottom-left-radius: 6px;
  box-shadow: 0 2px 12px rgba(52, 211, 153, 0.1);
}

.message-row.user .message-bubble {
  background: var(--color-bubble-user-bg);
  border: 1px solid var(--color-bubble-user-border);
  border-bottom-right-radius: 6px;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.message-bubble:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.15);
}

.message-row.assistant .message-bubble::before {
  display: none;
}

.message-row.user .message-bubble::before {
  display: none;
}

.message-content {
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-text);
}

.message-content :deep(p) {
  margin: 0 0 12px;
}

.message-content :deep(p:last-child) {
  margin: 0;
}

.message-content :deep(code) {
  background: var(--color-surface);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Fira Code', monospace;
  color: var(--color-primary);
}

.message-content :deep(pre) {
  background: var(--color-surface);
  padding: 12px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.message-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) {
  margin: 16px 0 8px;
  font-weight: 600;
  color: var(--color-text);
}

.message-content :deep(h1) { font-size: 20px; }
.message-content :deep(h2) { font-size: 18px; }
.message-content :deep(h3) { font-size: 16px; }

.message-content :deep(ul),
.message-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.message-content :deep(li) {
  margin: 4px 0;
  line-height: 1.5;
}

.message-content :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 3px solid var(--color-primary);
  background: var(--color-surface);
  border-radius: 0 8px 8px 0;
  color: var(--color-text-dim);
}

.message-content :deep(blockquote p) {
  margin: 0;
}

.message-content :deep(a) {
  color: var(--color-primary);
  text-decoration: none;
}

.message-content :deep(a:hover) {
  text-decoration: underline;
}

.message-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--color-outline);
  margin: 16px 0;
}

.message-content :deep(strong) {
  font-weight: 600;
  color: var(--color-primary);
}

.message-content :deep(em) {
  font-style: italic;
}

.message-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.message-content :deep(th),
.message-content :deep(td) {
  border: 1px solid var(--color-outline);
  padding: 8px 12px;
  text-align: left;
}

.message-content :deep(th) {
  background: var(--color-surface);
  font-weight: 600;
}

.message-segment + .message-segment {
  margin-top: 14px;
}

.reasoning-block {
  background: rgba(139, 92, 246, 0.08);
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 14px;
}

.reasoning-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #8b5cf6;
  margin-bottom: 10px;
}

.reasoning-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-dim);
}

.reasoning-content :deep(p) {
  margin: 0 0 8px;
}

.reasoning-content :deep(p:last-child) {
  margin: 0;
}

.tool-steps-panel {
  margin-bottom: 14px;
  border-bottom: 1px solid var(--color-outline);
  padding-bottom: 12px;
}

.tool-steps-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: transparent;
  border: none;
  padding: 0;
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
}

.toggle-left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.toggle-title {
  font-size: 13px;
  font-weight: 600;
}

.toggle-count {
  font-size: 12px;
  color: var(--color-text-dim);
}

.toggle-chevron {
  transition: transform 0.2s ease;
  color: var(--color-text-dim);
}

.toggle-chevron.expanded {
  transform: rotate(180deg);
}

.tool-steps-body {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-step {
  background: rgba(0, 0, 0, 0.03);
  border: 1px solid var(--color-outline);
  border-radius: 12px;
  padding: 12px;
}

.tool-step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.tool-step-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.tool-step-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.tool-step-status.success {
  color: var(--color-success);
  border-color: color-mix(in srgb, var(--color-success) 30%, transparent);
  background: color-mix(in srgb, var(--color-success) 10%, transparent);
}

.tool-step-status.running {
  color: var(--color-warning);
  border-color: color-mix(in srgb, var(--color-warning) 30%, transparent);
  background: color-mix(in srgb, var(--color-warning) 10%, transparent);
}

.tool-step-status.error {
  color: var(--color-error);
  border-color: color-mix(in srgb, var(--color-error) 30%, transparent);
  background: color-mix(in srgb, var(--color-error) 10%, transparent);
}

.tool-block + .tool-block {
  margin-top: 10px;
}

.tool-block-label {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 6px;
}

.tool-block-content {
  margin: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
  font-family: 'Fira Code', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
}

.tool-block-content.error {
  border-color: color-mix(in srgb, var(--color-error) 35%, transparent);
  color: var(--color-error);
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 4px;
}

.meta-role {
  font-size: 12px;
  color: var(--color-text-dim);
}

.meta-dot {
  font-size: 10px;
  color: var(--color-text-dim);
}

.meta-time {
  font-size: 12px;
  color: var(--color-text-dim);
}

/* AI 加载动画 */
.loading-stars {
  display: flex;
  gap: 8px;
}

.loading-stars span {
  width: 10px;
  height: 10px;
  background: var(--color-primary);
  border-radius: 50%;
  box-shadow: 0 0 12px var(--color-primary);
  animation: star-pulse 2s infinite;
}

.loading-stars span:nth-child(2) { animation-delay: 0.3s; }
.loading-stars span:nth-child(3) { animation-delay: 0.6s; }

@keyframes star-pulse {
  0%, 100% { 
    transform: scale(1); 
    opacity: 0.6;
  }
  50% { 
    transform: scale(1.5); 
    opacity: 1;
    box-shadow: 0 0 20px var(--color-primary);
  }
}
</style>
