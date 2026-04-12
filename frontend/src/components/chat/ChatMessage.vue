<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import { FileText, Brain, ChevronDown, ChevronRight, Volume2, Sparkles } from 'lucide-vue-next'
import type { ChatMessage, ChatMessageSegment, ChatReasoningSegment, ChatTextSegment, ChatToolSegment } from '@/types'
import { useTts } from '@/composables/useTts'

const props = defineProps<{
  message: ChatMessage
  timeLabel: string
  isLastUserMessage?: boolean
}>()

const { isPlaying, currentPlayingId, speak } = useTts()

const reasoningExpanded = ref(false)
const expandedToolKeys = ref<Set<string>>(new Set())

watch(
  () => {
    const segments = props.message.segments
    const isLoading = props.message.isLoading
    if (!segments || segments.length === 0) return { hasReasoning: false, hasText: false, isLoading }
    
    const hasReasoning = segments.some(isReasoningSegment)
    const hasText = segments.some(isTextSegment)
    return { hasReasoning, hasText, isLoading }
  },
  ({ hasReasoning, hasText, isLoading }) => {
    if (hasReasoning && isLoading && !hasText) {
      reasoningExpanded.value = true
    } else if (hasText && !isLoading) {
      reasoningExpanded.value = false
    }
  },
  { immediate: true }
)

const messageText = computed(() => {
  if (props.message.role !== 'assistant') return ''
  if (Array.isArray(props.message.segments) && props.message.segments.length > 0) {
    return props.message.segments
      .filter(isTextSegment)
      .map(s => s.content)
      .join('\n')
  }
  return props.message.content || ''
})

const isThisMessagePlaying = computed(() => {
  return isPlaying.value && String(currentPlayingId.value ?? '') === String(props.message.id ?? '')
})

async function handleTtsClick() {
  if (!messageText.value) return
  await speak(messageText.value, String(props.message.id ?? ''))
}

function toggleReasoning() {
  reasoningExpanded.value = !reasoningExpanded.value
}

function isTextSegment(segment: ChatMessageSegment): segment is ChatTextSegment {
  return segment.type === 'text' && 'content' in segment && Boolean(segment.content)
}

function isReasoningSegment(segment: ChatMessageSegment): segment is ChatReasoningSegment {
  return segment.type === 'reasoning' && 'content' in segment && Boolean(segment.content)
}

function toolStepKey(segment: ChatToolSegment, index: number): string {
  return segment.toolCallId || segment.id || `${segment.toolName || 'tool'}-${index}`
}

function isToolExpanded(segment: ChatToolSegment, index: number): boolean {
  return expandedToolKeys.value.has(toolStepKey(segment, index))
}

function toggleToolStep(segment: ChatToolSegment, index: number) {
  const key = toolStepKey(segment, index)
  const next = new Set(expandedToolKeys.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedToolKeys.value = next
}

const md = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  typographer: true
})

const defaultLinkOpenRenderer = md.renderer.rules.link_open || ((tokens, idx, options, _env, self) => {
  return self.renderToken(tokens, idx, options)
})

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const targetIndex = tokens[idx].attrIndex('target')
  if (targetIndex < 0) {
    tokens[idx].attrPush(['target', '_blank'])
  } else {
    tokens[idx].attrs![targetIndex][1] = '_blank'
  }

  const relIndex = tokens[idx].attrIndex('rel')
  const relValue = 'noopener noreferrer nofollow'
  if (relIndex < 0) {
    tokens[idx].attrPush(['rel', relValue])
  } else {
    tokens[idx].attrs![relIndex][1] = relValue
  }

  return defaultLinkOpenRenderer(tokens, idx, options, env, self)
}

function processContent(content: string): string {
  let processed = content ?? ''
  const markdownMatch = processed.match(/^markdown#?\s*/)
  if (markdownMatch) {
    processed = processed.slice(markdownMatch[0].length).trim()
  }
  // URLs wrapped in inline code cannot be clicked in markdown. Unwrap them.
  processed = processed.replace(/`(https?:\/\/[^`\s]+)`/g, '$1')
  // Ensure plain http/https URLs become clickable links.
  processed = processed.replace(
    /(^|[\s(\[\{<\u3000])((https?:\/\/[^\s<>"'`\u3002\uff0c\uff1b\uff01\uff1f]+))/g,
    '$1<$2>'
  )
  return processed
}

function handleMessageContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const link = target?.closest('a') as HTMLAnchorElement | null
  if (!link) return

  const href = link.getAttribute('href') || ''
  if (!/^https?:\/\//i.test(href)) return

  event.preventDefault()
  window.open(href, '_blank', 'noopener,noreferrer')
}

function safeToolName(name?: string): string {
  if (!name) return '宸ュ叿璋冪敤'
  if (name === 'executeCommand') return '鍛戒护鎵ц'
  if (name === 'execute_command') return '命令执行'
  if (name === 'readLocalFile') return '鏂囦欢璇诲彇'
  if (name === 'read_file') return '文件读取'
  if (name === 'activate_skill') return '激活技能'
  if (name === 'read_skill_resource') return '读取技能资源'
  if (name === 'write_file') return '文件写入'
  return name
}

function parseSkillName(raw?: string): string {
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed === 'string') return parsed.trim()
    if (parsed && typeof parsed === 'object') {
      const candidate =
        parsed.skill_name ??
        parsed.skillName ??
        parsed.skill ??
        parsed.name ??
        ''
      return typeof candidate === 'string' ? candidate.trim() : ''
    }
    return ''
  } catch {
    return raw.trim()
  }
}

const skillNames = computed(() => {
  if (props.message.role !== 'assistant') return []

  const steps = props.message.toolSteps ?? []
  const segmentSteps = Array.isArray(props.message.segments)
    ? props.message.segments.filter((segment) => segment.type === 'tool')
    : []
  const names: string[] = []
  let activeSkill = ''

  for (const step of [...steps, ...segmentSteps]) {
    const toolName = step.toolName || ''
    const parsedSkill =
      step.skillName?.trim() ||
      (toolName === 'activate_skill' || toolName === 'read_skill_resource'
        ? parseSkillName(step.arguments)
        : '')

    if (toolName === 'activate_skill') {
      activeSkill = parsedSkill || activeSkill
      if (activeSkill && !names.includes(activeSkill)) {
        names.push(activeSkill)
      }
      continue
    }

    if (toolName === 'read_skill_resource') {
      const skill = parsedSkill || activeSkill
      if (skill && !names.includes(skill)) {
        names.push(skill)
      }
      continue
    }

    if (toolName.includes('skill') && parsedSkill && !names.includes(parsedSkill)) {
      names.push(parsedSkill)
    }
  }

  return names
})

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

function toolArgumentsPreview(raw?: string): string {
  const text = formatToolArguments(raw).replace(/\s+/g, ' ').trim()
  if (!text) return ''
  return text.length > 72 ? `${text.slice(0, 72)}...` : text
}

function formatToolResult(raw?: string): string {
  return raw?.trim() || ""
}

function toolArtifactImages(step: ChatToolSegment): string[] {
  return (step.artifacts || [])
    .filter((artifact) => artifact.artifactType === "image")
    .map((artifact) => {
      if (artifact.url) return artifact.url
      if (artifact.base64Data) {
        return `data:${artifact.mimeType || "image/png"};base64,${artifact.base64Data}`
      }
      return ""
    })
    .filter(Boolean)
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

  // 濡傛灉鏄姞杞界姸鎬佷笖娌℃湁鍐呭锛岃繑鍥炵┖鏁扮粍
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
    <!-- AI 鍔犺浇鐘舵€侊細鍙樉绀哄姞杞藉姩鐢伙紝涓嶆樉绀烘皵娉℃ -->
    <div v-if="message.role === 'assistant' && message.isLoading && !message.content && !displaySegments.length" class="assistant-loading-container">
      <div class="loading-stars">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>

    <!-- 姝ｅ父娑堟伅姘旀场妗?-->
    <div v-else class="message-bubble">
      <!-- 娓叉煋鐢ㄦ埛鍙戦€佺殑鍥剧墖 -->
      <div v-if="message.role === 'user' && message.images && message.images.length > 0" class="message-images">
        <img v-for="(img, index) in message.images" :key="index" :src="img" alt="User uploaded image" class="message-image" />
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
                <span>推理过程</span>
              </div>
              <div class="reasoning-toggle">
                <ChevronDown v-if="reasoningExpanded" :size="16" />
                <ChevronRight v-else :size="16" />
              </div>
            </div>
            <div v-show="reasoningExpanded" class="reasoning-content" v-html="renderContent(segment.content)"></div>
          </div>

          <div
            v-else-if="segment.type === 'tool'"
            class="tool-step"
            :class="{ expanded: isToolExpanded(segment, index), running: getToolStepStatus(segment) === 'running' }"
          >
            <div class="tool-step-header tool-step-toggle" @click="toggleToolStep(segment, index)">
              <div class="tool-step-main">
                <div class="tool-step-title">
                  <FileText :size="14" />
                  <span>{{ safeToolName(segment.toolName) }}</span>
                </div>
                <div v-if="!isToolExpanded(segment, index) && toolArgumentsPreview(segment.arguments)" class="tool-step-preview">
                  {{ toolArgumentsPreview(segment.arguments) }}
                </div>
              </div>
              <div class="tool-step-header-right">
                <span v-if="getToolStepStatus(segment) === 'error'" class="tool-step-status error">失败</span>
                <span v-else-if="getToolStepStatus(segment) === 'running'" class="tool-step-status running">运行中</span>
                <span v-else class="tool-step-status success">完成</span>
                <ChevronDown v-if="isToolExpanded(segment, index)" :size="16" />
                <ChevronRight v-else :size="16" />
              </div>
            </div>

            <div v-if="isToolExpanded(segment, index) && formatToolArguments(segment.arguments)" class="tool-block">
              <div class="tool-block-label">命令 / 参数</div>
              <pre class="tool-block-content"><code>{{ formatToolArguments(segment.arguments) }}</code></pre>
            </div>

            <div v-if="isToolExpanded(segment, index) && formatToolResult(segment.result)" class="tool-block">
              <div class="tool-block-label">结果</div>
              <pre class="tool-block-content tool-block-result" :class="{ error: segment.isError }"><code>{{ formatToolResult(segment.result) }}</code></pre>

            <div v-if="isToolExpanded(segment, index) && toolArtifactImages(segment).length" class="tool-images">
              <img
                v-for="(img, imgIndex) in toolArtifactImages(segment)"
                :key="`tool-image-${imgIndex}`"
                :src="img"
                alt="Tool artifact"
                class="tool-image"
              />
            </div>
            </div>
          </div>

            <div
              v-else-if="segment.type === 'text' && segment.content"
              class="message-content"
              @click="handleMessageContentClick"
              v-html="renderContent(segment.content)"
            ></div>
        </div>
      </template>

      <div v-else-if="message.content" class="message-content" @click="handleMessageContentClick" v-html="renderedContent"></div>
    </div>

    <div class="message-meta">
      <span class="meta-role">{{ message.role === 'assistant' ? '灵枢' : '你' }}</span>
      <span class="meta-time">{{ timeLabel }}</span>
      <span
        v-if="skillNames.length"
        class="skill-pill"
        :title="skillNames.join('，')"
      >
        <Sparkles :size="12" />
        <span class="skill-pill-label">Skill</span>
        <span class="skill-pill-name">{{ skillNames[0] }}</span>
        <span v-if="skillNames.length > 1" class="skill-pill-more">+{{ skillNames.length - 1 }}</span>
      </span>
      <button
        v-if="message.role === 'assistant' && messageText && !message.isLoading"
        class="tts-btn"
        :class="{ active: isThisMessagePlaying }"
        @click="handleTtsClick"
        :title="isThisMessagePlaying ? '停止播放' : '朗读此回复'"
      >
        <Volume2 :size="14" :class="{ 'playing-icon': isThisMessagePlaying }" />
      </button>
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

@media (max-width: 768px) {
  .message-row {
    max-width: 95%;
  }
}

.message-row.assistant {
  align-self: flex-start;
}

.message-row.user {
  align-self: flex-end;
  align-items: flex-end;
}

/* 鐢ㄦ埛娑堟伅鍏ュ満鍔ㄧ敾 */
.message-row.user {
  animation: message-in 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

/* AI 娑堟伅鍏ュ満鍔ㄧ敾锛堝姞杞藉畬鎴愬悗锛?*/
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

/* AI 鍔犺浇瀹瑰櫒 */
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
  transition: all 0.2s ease;
}

.reasoning-block.collapsed {
  padding: 10px 12px;
}

.reasoning-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #8b5cf6;
  margin-bottom: 10px;
  cursor: pointer;
  user-select: none;
}

.reasoning-block.collapsed .reasoning-header {
  margin-bottom: 0;
}

.reasoning-header:hover {
  opacity: 0.8;
}

.reasoning-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reasoning-toggle {
  display: flex;
  align-items: center;
  color: #8b5cf6;
  transition: transform 0.2s ease;
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

.tool-step-toggle {
  cursor: pointer;
  user-select: none;
  transition: background 0.16s ease, border-color 0.16s ease;
}

.tool-step-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text-dim);
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
  background: color-mix(in srgb, var(--color-surface) 65%, transparent);
  border: 1px solid var(--color-outline);
  border-radius: 10px;
  padding: 8px 10px;
}

.tool-step.expanded {
  padding: 10px 12px;
}

.tool-step.running {
  border-color: color-mix(in srgb, var(--color-warning) 35%, var(--color-outline));
}

.tool-step-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 0;
  border-radius: 8px;
  padding: 2px 4px;
}

.tool-step.expanded .tool-step-header {
  margin-bottom: 10px;
}

.tool-step-header:hover {
  background: color-mix(in srgb, var(--color-surface) 75%, transparent);
}

.tool-step-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.tool-step-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
}

.tool-step-preview {
  font-size: 11px;
  color: var(--color-text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 440px;
}

.tool-step-status {
  font-size: 10px;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
  line-height: 1.7;
  font-weight: 600;
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
  margin-top: 8px;
}

.tool-block-label {
  font-size: 11px;
  color: var(--color-text-dim);
  margin-bottom: 5px;
}

.tool-block-content {
  margin: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 8px;
  padding: 8px 10px;
  overflow-x: auto;
  font-size: 11px;
  line-height: 1.5;
  font-family: 'Fira Code', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
}

.tool-block-result {
  max-height: 260px;
  overflow-y: auto;
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

.skill-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(59, 130, 246, 0.25);
  background: rgba(59, 130, 246, 0.08);
  color: var(--color-primary);
  font-size: 11px;
  line-height: 1;
  max-width: 100%;
}

.skill-pill-label {
  opacity: 0.7;
}

.skill-pill-name {
  font-weight: 700;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-pill-more {
  opacity: 0.75;
}

.tts-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-left: 4px;
  padding: 0;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--color-text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
  opacity: 0.6;
}

.tts-btn:hover {
  opacity: 1;
  background: var(--color-surface);
  border-color: var(--color-outline);
  color: var(--color-primary);
}

.tts-btn.active {
  opacity: 1;
  color: var(--color-primary);
}

.playing-icon {
  animation: speaker-pulse 1.2s infinite ease-in-out;
}

@keyframes speaker-pulse {
  0% { transform: scale(1); opacity: 0.7; }
  50% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(1); opacity: 0.7; }
}

/* AI 鍔犺浇鍔ㄧ敾 */
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

@media (prefers-reduced-motion: reduce) {
  .message-row.user,
  .message-row.assistant:not(.is-loading) {
    animation: none !important;
  }

  .loading-stars span,
  .playing-icon {
    animation: none !important;
    opacity: 0.8;
    box-shadow: none;
  }

  .pulse-dot {
    animation: none !important;
    box-shadow: none;
  }
}
</style>


<style scoped>
.tool-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.tool-image {
  max-width: 320px;
  max-height: 320px;
  border-radius: 8px;
  object-fit: contain;
  border: 1px solid var(--color-outline);
}
</style>
