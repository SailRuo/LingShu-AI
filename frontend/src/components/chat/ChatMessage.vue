<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import type { ChatMessage } from '@/types'

const props = defineProps<{
  message: ChatMessage
  timeLabel: string
}>()

const md = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  typographer: true
})

function processContent(content: string): string {
  let processed = content
  // 处理 markdown 前缀（支持 markdown 或 markdown# 格式）
  const markdownMatch = processed.match(/^markdown#?\s*/)
  if (markdownMatch) {
    processed = processed.slice(markdownMatch[0].length).trim()
  }
  return processed
}

const renderedContent = computed(() => md.render(processContent(props.message.content)))
</script>

<template>
  <div class="message-row" :class="message.role">
    <div class="message-bubble">
      <div class="message-content" v-html="renderedContent"></div>
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

.message-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 4px;
}

.meta-role {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-dim);
}

.meta-dot {
  color: var(--color-outline);
}

.meta-time {
  font-size: 11px;
  color: var(--color-text-dim);
  opacity: 0.7;
}
</style>
