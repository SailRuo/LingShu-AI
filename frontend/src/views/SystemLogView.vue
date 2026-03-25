<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { NInput, NButton, NTag, useMessage } from 'naive-ui'
import { Search, Trash2, Download } from 'lucide-vue-next'

interface LogLine {
  time: string
  type: string
  section: string
  content: string
  isUserMessage: boolean
  isFactComplete: boolean
}

const logs = ref<LogLine[]>([])
const searchQuery = ref('')
const activeFilters = ref<Set<string>>(new Set())
let logEventSource: EventSource | null = null
const maxLines = 500
const message = useMessage()
const logContainer = ref<HTMLElement | null>(null)

const SECTION_OPTIONS = [
  { key: 'MEMORY', label: 'MEMORY', color: '#8b5cf6' },
  { key: 'CHAT', label: 'CHAT', color: '#3b82f6' },
  { key: 'LLM', label: 'LLM', color: '#10b981' },
  { key: 'POST_PROCESS', label: 'POST_PROCESS', color: '#ef4444' },
  { key: 'EMOTION', label: 'EMOTION', color: '#f97316' },
  { key: 'FACT', label: 'FACT', color: '#f59e0b' },
  { key: 'SYSTEM', label: 'SYSTEM', color: '#6b7280' }
]

const toggleFilter = (key: string) => {
  if (activeFilters.value.has(key)) {
    activeFilters.value.delete(key)
  } else {
    activeFilters.value.add(key)
  }
}

const connectLogs = () => {
  if (logEventSource) logEventSource.close()
  
  logs.value = []
  
  logEventSource = new EventSource('/api/logs/stream')
  
  logEventSource.onmessage = (event) => {
    try {
      const logData = JSON.parse(event.data)
      if (logData.type === 'PING') return
      
      const timestamp = logData.time || new Date().toLocaleTimeString()
      const type = (logData.type || 'INFO').toUpperCase()
      const section = (logData.section || 'SYSTEM').toUpperCase()
      const content = logData.content || ''
      const isUserMessage = content.includes('收到用户消息')
      const isFactComplete = content.includes('事实提取分析完成') || 
                             content.includes('记忆脉冲处理完成') ||
                             content.includes('持久化新事实')
      
      logs.value.push({
        time: timestamp,
        type,
        section,
        content,
        isUserMessage,
        isFactComplete
      })
      
      if (logs.value.length > maxLines) {
        logs.value = logs.value.slice(-maxLines)
      }
      
      nextTick(() => {
        if (logContainer.value) {
          logContainer.value.scrollTop = logContainer.value.scrollHeight
        }
      })
    } catch (e) {
      console.error('Parse error:', e)
    }
  }

  logEventSource.onerror = () => {
    logEventSource?.close()
    setTimeout(connectLogs, 5000)
  }
}

const clearLogs = async () => {
  try {
    await fetch('/api/logs', { method: 'DELETE' })
    logs.value = []
    message.success('日志已清空（包括历史记录）')
  } catch (e) {
    message.error('清空失败')
  }
}

const exportLogs = () => {
  const text = logs.value.map(l => `[${l.time}] [${l.type}] [${l.section}] ${l.content}`).join('\n')
  const blob = new Blob([text], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `lingshu-logs-${new Date().toISOString().slice(0,10)}.txt`
  a.click()
  URL.revokeObjectURL(url)
  message.success('日志已导出')
}

const filteredLogs = computed(() => {
  let result = logs.value
  
  if (activeFilters.value.size > 0) {
    result = result.filter(l => activeFilters.value.has(l.section))
  }
  
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(l => 
      l.content.toLowerCase().includes(q) || 
      l.section.toLowerCase().includes(q) ||
      l.type.toLowerCase().includes(q)
    )
  }
  
  return result
})

const getTypeClass = (type: string) => {
  switch (type) {
    case 'ERROR': return 'log-error'
    case 'WARN': return 'log-warn'
    case 'SUCCESS': return 'log-success'
    case 'DEBUG': return 'log-debug'
    default: return 'log-info'
  }
}

onMounted(() => {
  connectLogs()
})

onUnmounted(() => {
  logEventSource?.close()
})
</script>

<template>
  <div class="console-view">
    <header class="console-header">
      <div class="header-left">
        <span class="console-title">系统日志控制台</span>
        <span class="console-status">
          <span class="status-dot"></span>
          SSE 已连接
        </span>
      </div>
      
      <div class="header-actions">
        <n-input 
          v-model:value="searchQuery" 
          placeholder="搜索日志内容/类型..." 
          clearable 
          size="small"
          class="search-input"
        >
          <template #prefix>
            <Search :size="14" />
          </template>
        </n-input>
        
        <n-button size="small" secondary @click="clearLogs">
          <template #icon><Trash2 :size="14" /></template>
          清空
        </n-button>
        
        <n-button size="small" type="primary" @click="exportLogs">
          <template #icon><Download :size="14" /></template>
          导出
        </n-button>
      </div>
    </header>
    
    <div class="filter-bar">
      <span class="filter-label">筛选:</span>
      <n-tag 
        v-for="opt in SECTION_OPTIONS" 
        :key="opt.key"
        :checked="activeFilters.has(opt.key)"
        checkable
        :color="{ color: activeFilters.has(opt.key) ? opt.color : 'transparent', textColor: activeFilters.has(opt.key) ? '#fff' : opt.color }"
        @click="toggleFilter(opt.key)"
        class="filter-tag"
      >
        {{ opt.label }}
      </n-tag>
      <n-tag v-if="activeFilters.size > 0" @click="activeFilters.clear()" class="clear-filter">
        清除筛选
      </n-tag>
    </div>

    <div class="console-body" ref="logContainer">
      <div class="log-content">
        <template v-for="(log, i) in filteredLogs" :key="i">
          <div v-if="log.isUserMessage" class="log-separator user"></div>
          <div 
            class="log-line" 
            :class="[
              getTypeClass(log.type), 
              { 'user-message': log.isUserMessage, 'fact-message': log.isFactComplete }
            ]"
          >
            <span class="log-time">[{{ log.time }}]</span>
            <span class="log-type">[{{ log.type }}]</span>
            <span class="log-section">[{{ log.section }}]</span>
            <span class="log-text">{{ log.content }}</span>
          </div>
        </template>
      </div>
      <div v-if="logs.length === 0" class="empty-hint">
        等待日志流...
      </div>
    </div>
  </div>
</template>

<style scoped>
.console-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--color-glass-border);
}

.console-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-outline);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.console-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.console-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-dim);
}

.status-dot {
  width: 6px;
  height: 6px;
  background: var(--color-success);
  border-radius: 50%;
  box-shadow: 0 0 6px var(--color-success);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.header-actions {
  display: flex;
  gap: 8px;
}

.search-input {
  width: 200px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-outline);
  flex-shrink: 0;
}

.filter-label {
  font-size: 12px;
  color: var(--color-text-dim);
}

.filter-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.filter-tag:hover {
  opacity: 0.8;
}

.clear-filter {
  cursor: pointer;
  color: var(--color-error);
}

.console-body {
  flex: 1;
  overflow: auto;
  background: var(--color-background);
  position: relative;
}

.log-content {
  padding: 16px;
  font-family: 'Fira Code', 'JetBrains Mono', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.7;
}

.log-separator {
  height: 1px;
  margin: 12px 0;
  opacity: 0.5;
}

.log-separator.user {
  background: linear-gradient(90deg, transparent, var(--color-primary), transparent);
}

.log-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 2px 0;
  border-radius: 4px;
  transition: background 0.2s;
}

.log-line:hover {
  background: var(--color-surface);
}

.log-line.user-message {
  background: var(--color-primary-dim);
  padding: 8px;
  margin: 4px 0;
  border-left: 3px solid var(--color-primary);
}

.log-time {
  color: var(--color-text-dim);
}

.log-type {
  font-weight: 600;
}

.log-section {
  color: var(--color-accent);
}

.log-text {
  color: var(--color-text);
  flex: 1;
  word-break: break-all;
}

.log-error .log-type { color: var(--color-error); }
.log-warn .log-type { color: var(--color-warning); }
.log-success .log-type { color: var(--color-success); }
.log-debug .log-type { color: var(--color-text-dim); }
.log-info .log-type { color: var(--color-primary); }

.log-error .log-text { color: var(--color-error); }
.log-success .log-text { color: var(--color-success); }
.log-fact-message .log-text { color: var(--color-success); }

.empty-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--color-text-dim);
  font-size: 14px;
}

.console-body::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.console-body::-webkit-scrollbar-track {
  background: var(--color-background);
}

.console-body::-webkit-scrollbar-thumb {
  background: var(--color-outline);
  border-radius: 4px;
}

.console-body::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-dim);
}
</style>
