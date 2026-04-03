<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { FileText, Brain, ChevronDown, ChevronRight } from 'lucide-vue-next'
import type { ChatMessage, ChatMessageSegment, ChatToolSegment } from '@/types'

const props = defineProps<{
  message: ChatMessage
  timeLabel: string
  isLastUserMessage?: boolean
}>()

const reasoningExpanded = ref(false)
const expandedToolSegments = ref<Set<string>>(new Set())

function toggleReasoning() {
  reasoningExpanded.value = !reasoningExpanded.value
}

function escapeAttribute(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function encodeCode(value: string): string {
  return encodeURIComponent(value)
}

function normalizeLanguage(lang?: string): string {
  if (!lang) return 'TEXT'
  return lang.replace(/[^\w#+.-]/g, '').toUpperCase() || 'TEXT'
}

function inferCodeLanguage(code: string): string {
  const source = code.trim()

  if (/^\s*(from\s+\w+\s+import|import\s+\w+|def\s+\w+\s*\(|print\s*\(|for\s+\w+\s+in\s+range|if\s+__name__\s*==)/m.test(source)) {
    return 'python'
  }

  if (/^\s*(const|let|var|function|export\s+default|import\s+.+from|console\.log|=>)/m.test(source)) {
    return 'javascript'
  }

  if (/^\s*(interface|type\s+\w+\s*=|const|let|import\s+type|export\s+type)/m.test(source)) {
    return 'typescript'
  }

  if (/^\s*(public\s+class|private\s+|public\s+static\s+void|System\.out\.println|@(?:Override|RestController|Service))/m.test(source)) {
    return 'java'
  }

  if (/^\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|WITH)\b/im.test(source)) {
    return 'sql'
  }

  if (/^\s*[{[]([\s\S]*)[}\]]\s*$/.test(source)) {
    return 'json'
  }

  if (/^\s*</.test(source)) {
    return 'html'
  }

  if (/^\s*([.#][\w-]+|\w[\w-]*\s*\{)/m.test(source)) {
    return 'css'
  }

  if (/^\s*(#!\/bin\/|echo\s+|if\s+\[|fi$|grep\s+|find\s+|chmod\s+)/m.test(source)) {
    return 'bash'
  }

  return ''
}

function looksLikeCode(code: string): boolean {
  const source = code.trim()
  if (!source) return false

  const lineCount = source.split('\n').length
  const hasCodeKeyword = /\b(def|class|function|return|import|from|const|let|var|public|private|SELECT|INSERT|for|while|if|else|elif|print|console\.log)\b/.test(source)
  const hasStructuralChars = /[{}[\]();=<>\-+*/]/.test(source)
  const hasIndentedLine = /\n\s{2,}\S/.test(source)
  const hasAssignment = /\b\w+\s*=\s*.+/.test(source)

  return (
    lineCount >= 2 ||
    (source.length >= 28 && hasCodeKeyword) ||
    (hasCodeKeyword && hasStructuralChars) ||
    (hasAssignment && hasStructuralChars) ||
    hasIndentedLine
  )
}

function wrapAsCodeFence(code: string): string {
  const trimmed = code.trim()
  const language = inferCodeLanguage(trimmed)
  return `\n\`\`\`${language}\n${trimmed}\n\`\`\`\n`
}

function normalizeToken(value: string): string {
  return value
    .toLowerCase()
    .replace(/[`"':;\uFF1A\s_-]+/g, '')
    .trim()
}

function isLikelyMarkdownBody(text: string): boolean {
  const source = text.trim()
  if (!source) return false

  return (
    /^\s{0,3}#{1,6}\s+/m.test(source) ||
    /^\s{0,3}[-*+]\s+/m.test(source) ||
    /^\s{0,3}\d+\.\s+/m.test(source) ||
    /^\s{0,3}>\s+/m.test(source) ||
    /\[[^\]]+\]\([^)]+\)/.test(source) ||
    /\*\*[^*]+\*\*/.test(source)
  )
}

function normalizeCodeFenceBody(lang: string, code: string): string {
  const trimmed = code.trim()
  if (!trimmed) return trimmed

  const lines = trimmed.split('\n')
  if (lines.length <= 1) {
    return trimmed
  }

  const firstLine = lines[0]?.trim() ?? ''
  const firstToken = normalizeToken(firstLine)
  const langToken = normalizeToken(lang)

  if (firstToken === langToken || firstToken === `language${langToken}`) {
    return lines.slice(1).join('\n').trim()
  }

  return trimmed
}

function stripDuplicateLanguageLine(code: string, lang?: string): string {
  const language = (lang ?? '').trim().toLowerCase()
  if (!language) {
    return code
  }

  const normalized = code.replace(/\r\n/g, '\n').trim()
  if (!normalized) {
    return normalized
  }

  const lines = normalized.split('\n')
  if (lines.length <= 1) {
    return normalized
  }

  const firstToken = normalizeToken(lines[0] ?? '')
  const langToken = normalizeToken(language)

  if (firstToken === langToken || firstToken === `language${langToken}`) {
    return lines.slice(1).join('\n').trim()
  }

  return normalized
}

const md = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: true
})

function renderCodeBlock(rawCode: string, langInfo?: string): string {
  const language = (langInfo ?? '').trim().toLowerCase()
  const normalizedSource = stripDuplicateLanguageLine(rawCode, language)
  const validLanguage = language && hljs.getLanguage(language) ? language : ''
  const highlighted = validLanguage
    ? hljs.highlight(normalizedSource, { language: validLanguage, ignoreIllegals: true }).value
    : md.utils.escapeHtml(normalizedSource)
  const lineCount = normalizedSource.split('\n').length
  const blockClass = lineCount <= 1 ? 'code-block is-compact' : 'code-block'

  return `
    <div class="${blockClass}">
      <div class="code-block-header">
        <span class="code-block-lang">${normalizeLanguage(validLanguage || language)}</span>
        <button
          type="button"
          class="code-copy-button"
          data-code="${escapeAttribute(encodeCode(normalizedSource))}"
        >
          Copy
        </button>
      </div>
      <pre class="hljs"><code>${highlighted}</code></pre>
    </div>
  `
}

const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer nofollow')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

md.renderer.rules.fence = (tokens: any[], idx: number) => {
  const token = tokens[idx]
  const info = (token.info ?? '').trim()
  const lang = info.split(/\s+/g)[0] || ''
  const code = token.content ?? ''
  return renderCodeBlock(code, lang)
}

function processContent(content: string): string {
  let processed = (content ?? '').replace(/\r\n/g, '\n').trim()

  processed = processed
    .replace(/^\s*`\s*\n/, '')
    .replace(/\n\s*`\s*$/, '')
    .trim()

  processed = processed.replace(/```([a-zA-Z0-9_+#.-]+)[ \t]+([\s\S]*?)```/g, (_, lang, code) => {
    const normalized = normalizeCodeFenceBody(String(lang), String(code))
    return `\n\`\`\`${String(lang).toLowerCase()}\n${normalized}\n\`\`\`\n`
  })

  processed = processed.replace(/```([a-zA-Z0-9_+#.-]+)\n([\s\S]*?)```/g, (_, lang, code) => {
    const normalized = normalizeCodeFenceBody(String(lang), String(code))
    const language = String(lang).toLowerCase()

    if ((language === 'markdown' || language === 'md') && isLikelyMarkdownBody(normalized)) {
      return `\n${normalized}\n`
    }

    return `\n\`\`\`${language}\n${normalized}\n\`\`\`\n`
  })

  const singleWrapped = processed.match(/^\s*`([\s\S]*)`\s*$/)
  if (singleWrapped) {
    const inner = String(singleWrapped[1] ?? '').trim()
    if (!inner) {
      return ''
    }

    const strippedMarkdownPrefix = inner.replace(/^\s*markdown#?\s*\n?/i, '')
    if (isLikelyMarkdownBody(strippedMarkdownPrefix)) {
      return strippedMarkdownPrefix.trim()
    }

    if (looksLikeCode(inner) && !isLikelyMarkdownBody(inner)) {
      return wrapAsCodeFence(inner).trim()
    }

    return inner
  }

  if (/^\s*markdown#?\s*(\n|$)/i.test(processed)) {
    const stripped = processed.replace(/^\s*markdown#?\s*(\n|$)/i, '')
    if (isLikelyMarkdownBody(stripped) || stripped.includes('```')) {
      processed = stripped
    }
  }

  return processed.trim()
}
function safeToolName(name?: string): string {
  if (!name) return 'Tool Call'
  if (name === 'executeCommand') return 'Execute Command'
  if (name === 'readLocalFile') return 'Read File'
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

function getToolSegmentKey(step: ChatToolSegment, index: number): string {
  return step.toolCallId || step.id || `${step.toolName || 'tool'}-${index}`
}

function isToolExpanded(step: ChatToolSegment, index: number): boolean {
  return expandedToolSegments.value.has(getToolSegmentKey(step, index))
}

function toggleToolExpanded(step: ChatToolSegment, index: number) {
  const key = getToolSegmentKey(step, index)
  const next = new Set(expandedToolSegments.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedToolSegments.value = next
}

function renderContent(content: string): string {
  return md.render(processContent(content))
}

async function copyText(text: string, button: HTMLButtonElement) {
  try {
    await navigator.clipboard.writeText(text)
    const originalText = button.textContent
    button.textContent = 'Copied'
    button.dataset.copied = 'true'
    window.setTimeout(() => {
      button.textContent = originalText ?? 'Copy'
      delete button.dataset.copied
    }, 1600)
  } catch {
    button.textContent = 'Failed'
    window.setTimeout(() => {
      button.textContent = 'Copy'
    }, 1600)
  }
}

function handleRichContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const button = target?.closest('.code-copy-button') as HTMLButtonElement | null
  if (!button) return

  const encoded = button.dataset.code
  if (!encoded) return

  const text = decodeURIComponent(encoded)
  void copyText(text, button)
}

const renderedContent = computed(() => renderContent(props.message.content))

const displaySegments = computed<ChatMessageSegment[]>(() => {
  if (props.message.role !== 'assistant') {
    return []
  }

  if (Array.isArray(props.message.segments) && props.message.segments.length > 0) {
    return props.message.segments
  }

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
  <div
    class="message-row"
    :class="[message.role, { 'is-loading': message.role === 'assistant' && message.isLoading && !message.content && !displaySegments.length }]"
  >
    <div
      v-if="message.role === 'assistant' && message.isLoading && !message.content && !displaySegments.length"
      class="assistant-loading-container"
    >
      <div class="loading-stars">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>

    <div v-else class="message-bubble" @click="handleRichContentClick">
      <div v-if="message.role === 'user' && message.images && message.images.length > 0" class="message-images">
        <img
          v-for="(img, index) in message.images"
          :key="index"
          :src="img"
          alt="User uploaded image"
          class="message-image"
        />
      </div>

      <template v-if="message.role === 'assistant' && displaySegments.length">
        <div
          v-for="(segment, index) in displaySegments"
          :key="segment.type === 'tool' ? (segment.toolCallId || segment.id || `${segment.toolName || 'tool'}-${index}`) : segment.type === 'reasoning' ? `reasoning-${index}` : `text-${index}`"
          class="message-segment"
        >
          <div v-if="segment.type === 'reasoning'" class="reasoning-block" :class="{ collapsed: !reasoningExpanded }">
            <div class="reasoning-header" @click="toggleReasoning">
              <div class="reasoning-header-left">
                <Brain :size="14" />
                <span>Reasoning</span>
              </div>
              <div class="reasoning-toggle">
                <ChevronDown v-if="reasoningExpanded" :size="16" />
                <ChevronRight v-else :size="16" />
              </div>
            </div>
            <div v-show="reasoningExpanded" class="reasoning-content markdown-body" v-html="renderContent(segment.content)"></div>
          </div>

          <div v-else-if="segment.type === 'tool'" class="tool-step">
            <button class="tool-step-header" type="button" @click.stop="toggleToolExpanded(segment, index)">
              <div class="tool-step-title">
                <FileText :size="14" />
                <span>{{ index + 1 }}. {{ safeToolName(segment.toolName) }}</span>
              </div>
              <div class="tool-step-meta">
                <span v-if="getToolStepStatus(segment) === 'error'" class="tool-step-status error">Failed</span>
                <span v-else-if="getToolStepStatus(segment) === 'running'" class="tool-step-status running">Running</span>
                <span v-else class="tool-step-status success">Done</span>
                <ChevronDown v-if="isToolExpanded(segment, index)" :size="14" class="tool-step-chevron" />
                <ChevronRight v-else :size="14" class="tool-step-chevron" />
              </div>
            </button>

            <div v-if="isToolExpanded(segment, index) && formatToolArguments(segment.arguments)" class="tool-block">
              <div class="tool-block-label">Command / Args</div>
              <pre class="tool-block-content"><code>{{ formatToolArguments(segment.arguments) }}</code></pre>
            </div>

            <div v-if="isToolExpanded(segment, index) && formatToolResult(segment.result)" class="tool-block">
              <div class="tool-block-label">Result</div>
              <pre class="tool-block-content" :class="{ error: segment.isError }"><code>{{ formatToolResult(segment.result) }}</code></pre>
            </div>
          </div>

          <div
            v-else-if="segment.type === 'text' && segment.content"
            class="message-content markdown-body"
            v-html="renderContent(segment.content)"
          ></div>
        </div>
      </template>

      <div v-else-if="message.content" class="message-content markdown-body" v-html="renderedContent"></div>
    </div>

    <div class="message-meta">
      <span class="meta-role">{{ message.role === 'assistant' ? 'Assistant' : 'You' }}</span>
      <span class="meta-dot">·</span>
      <span class="meta-time">{{ timeLabel }}</span>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: min(880px, 82%);
}

.message-row.assistant {
  align-self: flex-start;
}

.message-row.user {
  align-self: flex-end;
  align-items: flex-end;
}

.message-row.user,
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

.assistant-loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px 20px;
}

.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.message-image {
  max-width: 300px;
  max-height: 300px;
  border-radius: 14px;
  object-fit: contain;
  border: 1px solid color-mix(in srgb, var(--color-outline) 90%, transparent);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.12);
}

.message-bubble {
  width: 100%;
  position: relative;
  overflow: hidden;
  padding: 18px 20px;
  border-radius: 22px;
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.message-bubble:hover {
  transform: translateY(-1px);
}

.message-row.assistant .message-bubble {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--color-bubble-ai-bg) 92%, transparent), color-mix(in srgb, var(--color-surface-elevated) 35%, var(--color-bubble-ai-bg))),
    radial-gradient(circle at top left, color-mix(in srgb, var(--color-primary) 10%, transparent), transparent 48%);
  border: 1px solid color-mix(in srgb, var(--color-bubble-ai-border) 88%, transparent);
  border-bottom-left-radius: 8px;
  box-shadow:
    0 16px 36px rgba(15, 23, 42, 0.10),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.message-row.user .message-bubble {
  background: linear-gradient(180deg, var(--color-bubble-user-bg), color-mix(in srgb, var(--color-bubble-user-bg) 76%, var(--color-surface-elevated)));
  border: 1px solid color-mix(in srgb, var(--color-bubble-user-border) 85%, transparent);
  border-bottom-right-radius: 8px;
  box-shadow:
    0 18px 38px rgba(15, 23, 42, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.message-content,
.reasoning-content {
  font-size: 15px;
  line-height: 1.78;
  color: var(--color-text);
}

.markdown-body :deep(*) {
  min-width: 0;
}

.markdown-body :deep(p) {
  margin: 0 0 4px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(p + .code-block) {
  margin-top: 14px;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 24px 0 12px;
  line-height: 1.3;
  font-weight: 700;
  color: var(--color-text);
}

.markdown-body :deep(h1) {
  font-size: 1.4rem;
}

.markdown-body :deep(h2) {
  font-size: 1.2rem;
}

.markdown-body :deep(h3) {
  font-size: 1.05rem;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0 0 16px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin: 6px 0;
}

.markdown-body :deep(li > p) {
  margin-bottom: 8px;
}

.markdown-body :deep(a) {
  color: var(--color-primary);
  text-decoration: none;
  border-bottom: 1px solid color-mix(in srgb, var(--color-primary) 35%, transparent);
}

.markdown-body :deep(a:hover) {
  color: color-mix(in srgb, var(--color-primary) 80%, white);
  border-bottom-color: var(--color-primary);
}

.markdown-body :deep(strong) {
  font-weight: 700;
  color: var(--color-text);
}

.markdown-body :deep(em) {
  font-style: italic;
}

.markdown-body :deep(code:not(pre code)) {
  display: inline-block;
  padding: 0.16rem 0.5rem;
  margin: 0 0.1rem;
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-surface-elevated) 82%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-outline) 80%, transparent);
  color: var(--color-primary);
  font-size: 0.88em;
  font-family: 'Fira Code', 'JetBrains Mono', 'Cascadia Code', monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

.markdown-body :deep(.code-block) {
  margin: 14px 0;
  padding: 8px 4px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(13, 19, 35, 0.96), rgba(15, 23, 42, 0.94));
  box-shadow:
    0 1px 2px rgba(2, 6, 23, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.02);
}

.markdown-body :deep(.code-block + .code-block),
.markdown-body :deep(.code-block + p),
.markdown-body :deep(.code-block + .code-block.is-compact),
.markdown-body :deep(.code-block.is-compact + .code-block) {
  margin-top: 14px;
}

.markdown-body :deep(.code-block-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 2px 10px;
  min-height: 22px;
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.55), rgba(15, 23, 42, 0.38));
  border-bottom: 1px solid rgba(148, 163, 184, 0.08);
}

.markdown-body :deep(.code-block-lang) {
  font-size: 9px;
  line-height: 1;
  letter-spacing: 0.1em;
  font-weight: 600;
  color: rgba(226, 232, 240, 0.80);
}

.markdown-body :deep(.code-copy-button) {
  appearance: none;
  -webkit-appearance: none;
  box-sizing: border-box;
  width: auto;
  min-width: 0;
  height: 18px;
  min-height: 18px;
  padding: 2px 6px;
  border: 1px solid rgba(148, 163, 184, 0.20);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.03);
  color: rgba(226, 232, 240, 0.88);
  font-size: 9px;
  line-height: 1;
  white-space: nowrap;
  font-weight: 500;
  font-family: 'Inter', system-ui, sans-serif;
  cursor: pointer;
  transition: all 0.15s ease;
}

.markdown-body :deep(.code-copy-button:hover) {
  background: rgba(255, 255, 255, 0.10);
  border-color: rgba(226, 232, 240, 0.28);
}

.markdown-body :deep(.code-copy-button[data-copied="true"]) {
  color: #86efac;
  border-color: rgba(134, 239, 172, 0.32);
}

.markdown-body :deep(pre) {
  margin: 0 !important;
  padding: 0;
  overflow-x: auto;
  background: transparent !important;
  line-height: 1;
}

.markdown-body :deep(pre code) {
  display: block;
  padding: 0;
  border: 0;
  background: transparent !important;
  color: #dbe7ff;
  font-size: 12.5px;
  line-height: 1.4;
  white-space: pre;
  word-break: normal;
  font-family: 'Fira Code', 'JetBrains Mono', 'Cascadia Code', monospace;
}

.markdown-body :deep(.code-block.is-compact) {
  border-radius: 6px;
  box-shadow:
    0 1px 2px rgba(2, 6, 23, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.02);
}

.markdown-body :deep(.code-block.is-compact .code-block-header) {
  min-height: 20px;
  padding: 2px 8px;
}

.markdown-body :deep(.code-block.is-compact .code-copy-button) {
  height: 15px;
  min-height: 15px;
  padding: 1px 4px;
  font-size: 8px;
}

.markdown-body :deep(.code-block.is-compact pre) {
  padding: 0;
}

.markdown-body :deep(.code-block.is-compact pre code) {
  font-size: 12px;
  line-height: 1.35;
}

.markdown-body :deep(.hljs) {
  background: transparent !important;
  color: #dbe7ff;
  padding: 0 8px;
}

.markdown-body :deep(blockquote) {
  margin: 16px 0;
  padding: 12px 16px;
  border-left: 3px solid var(--color-primary);
  border-radius: 0 14px 14px 0;
  background: color-mix(in srgb, var(--color-primary-dim) 48%, transparent);
  color: var(--color-text-dim);
}

.markdown-body :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(hr) {
  margin: 18px 0;
  border: 0;
  border-top: 1px solid color-mix(in srgb, var(--color-outline) 85%, transparent);
}

.markdown-body :deep(table) {
  width: 100%;
  margin: 16px 0;
  border-collapse: separate;
  border-spacing: 0;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--color-outline) 88%, transparent);
  border-radius: 14px;
  background: color-mix(in srgb, var(--color-surface) 88%, transparent);
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 12px 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-outline) 74%, transparent);
  text-align: left;
  vertical-align: top;
}

.markdown-body :deep(th) {
  font-weight: 700;
  background: color-mix(in srgb, var(--color-surface-elevated) 88%, transparent);
}

.markdown-body :deep(tr:last-child td) {
  border-bottom: 0;
}

.message-segment + .message-segment {
  margin-top: 14px;
}

.reasoning-block {
  background: color-mix(in srgb, var(--color-primary-dim) 52%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-primary) 18%, transparent);
  border-radius: 16px;
  padding: 12px 14px;
}

.reasoning-block.collapsed {
  padding-bottom: 10px;
}

.reasoning-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary);
}

.reasoning-header-left,
.reasoning-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reasoning-content {
  margin-top: 12px;
  color: var(--color-text-dim);
}

.tool-step {
  padding: 14px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--color-surface) 92%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-outline) 86%, transparent);
}

.tool-step-header {
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  text-align: left;
}

.tool-step-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.tool-step-chevron {
  color: var(--color-text-dim);
}

.tool-step-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
}

.tool-step-status {
  font-size: 11px;
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.tool-step-status.success {
  color: var(--color-success);
  background: color-mix(in srgb, var(--color-success) 10%, transparent);
  border-color: color-mix(in srgb, var(--color-success) 24%, transparent);
}

.tool-step-status.running {
  color: var(--color-warning);
  background: color-mix(in srgb, var(--color-warning) 10%, transparent);
  border-color: color-mix(in srgb, var(--color-warning) 24%, transparent);
}

.tool-step-status.error {
  color: var(--color-error);
  background: color-mix(in srgb, var(--color-error) 10%, transparent);
  border-color: color-mix(in srgb, var(--color-error) 24%, transparent);
}

.tool-block + .tool-block {
  margin-top: 10px;
}

.tool-step .tool-block:first-of-type {
  margin-top: 12px;
}

.tool-block-label {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--color-text-dim);
}

.tool-block-content {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  border-radius: 12px;
  background: color-mix(in srgb, var(--color-surface-elevated) 86%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-outline) 84%, transparent);
  color: var(--color-text);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Fira Code', monospace;
}

.tool-block-content.error {
  color: var(--color-error);
  border-color: color-mix(in srgb, var(--color-error) 30%, transparent);
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 6px;
}

.meta-role,
.meta-time,
.meta-dot {
  font-size: 12px;
  color: var(--color-text-dim);
}

.loading-stars {
  display: flex;
  gap: 8px;
}

.loading-stars span {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--color-primary);
  box-shadow: 0 0 12px var(--color-primary);
  animation: star-pulse 2s infinite;
}

.loading-stars span:nth-child(2) {
  animation-delay: 0.3s;
}

.loading-stars span:nth-child(3) {
  animation-delay: 0.6s;
}

@keyframes star-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.45);
    opacity: 1;
    box-shadow: 0 0 20px var(--color-primary);
  }
}

@media (max-width: 768px) {
  .message-row {
    max-width: 100%;
  }

  .message-bubble {
    padding: 16px;
  }

  .markdown-body :deep(table) {
    display: block;
    overflow-x: auto;
    white-space: nowrap;
  }

  .markdown-body :deep(.code-block-header) {
    padding: 4px 8px;
    min-height: 28px;
    gap: 6px;
  }

  .markdown-body :deep(.code-copy-button) {
    height: 20px;
    min-height: 20px;
    padding: 2px 7px;
    font-size: 9px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .message-row.user,
  .message-row.assistant:not(.is-loading),
  .loading-stars span {
    animation: none !important;
  }

  .message-bubble,
  .markdown-body :deep(.code-copy-button) {
    transition: none !important;
  }
}
</style>
