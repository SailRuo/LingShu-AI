<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { 
  NIcon, NScrollbar, 
  NTag, NCard, NEmpty, NInput, NButton,
  useMessage
} from 'naive-ui'
import { 
  FileText, Search, Trash2, Download, 
  Activity, Zap, Cpu, Database
} from 'lucide-vue-next'

const logs = ref<any[]>([])
let logEventSource: EventSource | null = null
const maxLogs = 200
const searchQuery = ref('')
const message = useMessage()

// Filters
const filterSection = ref<string | null>(null)

const connectLogs = () => {
  if (logEventSource) logEventSource.close()
  
  // Using relative path to benefit from Vite proxy
  logEventSource = new EventSource('/api/logs/stream')
  
  logEventSource.onmessage = (event) => {
    try {
      const logData = JSON.parse(event.data)
      logs.value.unshift(logData)
      if (logs.value.length > maxLogs) {
        logs.value.pop()
      }
    } catch (e) {
      console.error('Failed to parse log data:', e)
    }
  }

  logEventSource.onerror = (err) => {
    console.error('Log SSE connection error:', err)
    logEventSource?.close()
    setTimeout(connectLogs, 5000)
  }
}

const clearLogs = () => {
  logs.value = []
  message.info('本地日志已清空')
}

const filteredLogs = computed(() => {
  let result = logs.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(l => 
      l.content.toLowerCase().includes(q) || 
      (l.section && l.section.toLowerCase().includes(q))
    )
  }
  if (filterSection.value) {
    result = result.filter(l => l.section === filterSection.value)
  }
  return result
})

const getLogType = (type: string) => {
  switch (type?.toLowerCase()) {
    case 'error': return 'error'
    case 'warn': return 'warning'
    case 'debug': return 'info'
    case 'trace': return 'default'
    default: return 'info'
  }
}

const getSectionIcon = (section: string) => {
  switch (section?.toUpperCase()) {
    case 'CHAT': return Activity
    case 'LLM': return Cpu
    case 'MEMORY': return Zap
    case 'FACT': return Database
    default: return FileText
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
  <div class="system-log-view">
    <!-- Header -->
    <header class="view-header">
      <div class="header-title">
        <div class="title-icon">
          <FileText :size="20" />
        </div>
        <div class="title-text">
          <h2>系统日志</h2>
          <span class="subtitle">实时同步后端 LLM 调用链与认知脉冲</span>
        </div>
      </div>
      
      <div class="header-actions">
        <n-input 
          v-model:value="searchQuery" 
          placeholder="搜索日志内容..." 
          clearable 
          class="search-input"
        >
          <template #prefix>
            <n-icon><Search /></n-icon>
          </template>
        </n-input>
        
        <n-button secondary @click="clearLogs">
          <template #icon><n-icon><Trash2 /></n-icon></template>
          清空
        </n-button>
        
        <n-button type="primary">
          <template #icon><n-icon><Download /></n-icon></template>
          导出
        </n-button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="view-main">
      <div class="log-container">
        <div v-if="filteredLogs.length === 0" class="empty-state">
          <n-empty description="暂无日志数据，等待系统脉冲..." />
        </div>
        
        <n-scrollbar v-else class="log-scroll">
          <div class="log-list">
            <div 
              v-for="(log, i) in filteredLogs" 
              :key="i" 
              class="log-item-card"
              :class="log.type?.toLowerCase()"
            >
              <div class="log-meta">
                <span class="log-time">{{ log.time }}</span>
                <div class="log-tags">
                  <n-tag 
                    v-if="log.section" 
                    :bordered="false" 
                    size="small" 
                    class="section-tag"
                  >
                    <template #icon>
                      <n-icon :component="getSectionIcon(log.section)" />
                    </template>
                    {{ log.section }}
                  </n-tag>
                  <n-tag 
                    :type="getLogType(log.type)" 
                    :bordered="false" 
                    size="small" 
                    class="type-tag"
                  >
                    {{ log.type }}
                  </n-tag>
                </div>
              </div>
              <div class="log-content">
                {{ log.content }}
              </div>
            </div>
          </div>
        </n-scrollbar>
      </div>

      <!-- Sidebar / Filters -->
      <aside class="log-sidebar">
        <n-card :bordered="false" class="filter-card">
          <div class="sidebar-section">
            <h3 class="sidebar-label">统计概览</h3>
            <div class="stats-grid">
              <div class="stat-box">
                <span class="stat-val">{{ logs.length }}</span>
                <span class="stat-key">总日志</span>
              </div>
              <div class="stat-box">
                <span class="stat-val text-error">{{ logs.filter(l => l.type === 'ERROR').length }}</span>
                <span class="stat-key">异常</span>
              </div>
            </div>
          </div>

          <div class="sidebar-divider"></div>

          <div class="sidebar-section">
            <h3 class="sidebar-label">模块过滤</h3>
            <div class="filter-groups">
              <div 
                class="filter-item" 
                :class="{ active: filterSection === null }"
                @click="filterSection = null"
              >
                全部模块
              </div>
              <div 
                v-for="s in ['CHAT', 'LLM', 'MEMORY', 'FACT']" 
                :key="s"
                class="filter-item"
                :class="{ active: filterSection === s }"
                @click="filterSection = s"
              >
                {{ s }}
              </div>
            </div>
          </div>
        </n-card>
        
        <div class="system-status-card">
          <div class="status-header">
             <div class="status-indicator online"></div>
             <span>SSE 连接状态: 已就绪</span>
          </div>
          <div class="status-details">
            后端服务器: http://localhost:8080/api/logs/stream
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.system-log-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 24px;
  background: transparent;
  color: var(--color-text);
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-shrink: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 16px;
}

.title-icon {
  width: 44px;
  height: 44px;
  background: var(--color-primary-dim);
  border: 1px solid var(--color-primary);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  box-shadow: 0 0 15px rgba(168, 85, 247, 0.2);
}

.title-text h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.subtitle {
  font-size: 12px;
  color: var(--color-text-dim);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.search-input {
  width: 280px;
}

.view-main {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 24px;
  min-height: 0;
}

.log-container {
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border);
  border-radius: 20px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.log-scroll {
  flex: 1;
}

.log-list {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-item-card {
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  border-left: 3px solid var(--color-primary);
  transition: all 0.2s;
}

.log-item-card:hover {
  background: rgba(255, 255, 255, 0.05);
  transform: translateX(4px);
}

.log-item-card.error { border-left-color: var(--color-error); }
.log-item-card.warning { border-left-color: var(--color-warning); }
.log-item-card.info { border-left-color: var(--color-primary); }

.log-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.log-time {
  font-family: 'Fira Code', monospace;
  font-size: 11px;
  color: var(--color-text-dim);
}

.log-tags {
  display: flex;
  gap: 8px;
}

.log-content {
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.85);
  word-break: break-all;
}

/* Sidebar Styling */
.log-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card :deep(.n-card__content) {
  padding: 20px !important;
}

.filter-card {
  background: var(--color-glass-bg) !important;
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: 20px;
}

.sidebar-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.stat-box {
  background: rgba(255, 255, 255, 0.03);
  padding: 12px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-val {
  font-size: 18px;
  font-weight: 700;
  font-family: 'Fira Code', monospace;
}

.stat-key {
  font-size: 10px;
  color: var(--color-text-dim);
}

.sidebar-divider {
  height: 1px;
  background: var(--color-outline);
  margin: 20px 0;
}

.filter-groups {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-item {
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--color-text-dim);
}

.filter-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: var(--color-text);
}

.filter-item.active {
  background: var(--color-primary-dim);
  color: var(--color-primary);
  font-weight: 600;
}

.system-status-card {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--color-outline);
  border-radius: 16px;
  padding: 16px;
}

.status-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 8px;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-indicator.online {
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
}

.status-details {
  font-size: 9px;
  font-family: 'Fira Code', monospace;
  color: var(--color-text-dim);
  word-break: break-all;
}

.text-error { color: var(--color-error); }
</style>
