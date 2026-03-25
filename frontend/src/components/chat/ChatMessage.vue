<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { ChevronDown, TerminalSquare, FileText } from 'lucide-vue-next'
import type { ChatMessage } from '@/types'

type ToolStep = {
  id?: string
  toolName?: string
  arguments?: string
  result?: string
  isError?: boolean
}

const props = defineProps<{
  message: ChatMessage & {
    toolSteps?: ToolStep[]
  }
  timeLabel: string
}>()

const expanded = ref(false)

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

const renderedContent = computed(() => md.render(processContent(props.message.content)))

const hasToolSteps = computed(() => Array.isArray(props.message.toolSteps) && props.message.toolSteps.length > 0)

const toolStepCountLabel = computed(() => {
  const count = props.message.toolSteps?.length || 0
  return `${count} 个步骤`
})
</script>

<template>
  <div class="message-row" :class="message.role">
    <div class="message-bubble">
      <div class="message-content" v-html="renderedContent"></div>

      <div v-if="message.role === 'assistant' && hasToolSteps" class="tool-steps-panel">
        <button class="tool-steps-toggle" type="button" @click="expanded = !expanded">
          <div class="toggle-left">
            <TerminalSquare :size="15" />
            <span class="toggle-title">执行过程</span>
            <span class="toggle-count">{{ toolStepCountLabel }}</span>
          </div>
          <ChevronDown :size="16" class="toggle-chevron" :class="{ expanded }" />
        </button>

        <div v-show="expanded" class="tool-steps-body">
          <div
            v-for="(step, index) in message.toolSteps"
            :key="step.id || `${step.toolName || 'tool'}-${index}`"
            class="tool-step"
          >
            <div class="tool-step-header">
              <div class="tool-step-title">
                <FileText :size="14" />
                <span>{{ index + 1 }}. {{ safeToolName(step.toolName) }}</span>
              </div>
              <span v-if="step.isError" class="tool-step-status error">失败</span>
              <span v-else class="tool-step-status success">完成</span>
            </div>

            <div v-if="formatToolArguments(step.arguments)" class="tool-block">
              <div class="tool-block-label">命令 / 参数</div>
              <pre class="tool-block-content"><code>{{ formatToolArguments(step.arguments) }}</code></pre>
            </div>

            <div v-if="formatToolResult(step.result)" class="tool-block">
              <div class="tool-block-label">结果</div>
              <pre class="tool-block-content" :class="{ error: step.isError }"><code>{{ formatToolResult(step.result) }}</code></pre>
            </div>
          </div>
        </div>
      </div>
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
  max-width: 85%;
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

.message-row.assistant {
  align-self: flex-start;
}

.message-row.user {
  align-self: flex-end;
  align-items: flex-end;
}

.message-bubble {
  padding: 16px 20px;
  border-radius: 20px;
  transition: all 0.2s ease;
}

.message-row.assistant .message-bubble {
  background: var(--color-bubble-ai-bg);
  border: 1px solid var(--color-bubble-ai-border);
  border-bottom-left-radius: 6px;
}

.message-row.user .message-bubble {
  background: var(--color-bubble-user-bg);
  border: 1px solid var(--color-bubble-user-border);
  border-bottom-right-radius: 6px;
}

.message-bubble:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
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
  color: var(--color-text);
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

.tool-steps-panel {
  margin-top: 14px;
  border-top: 1px solid var(--color-outline);
  padding-top: 12px;
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
</style>
