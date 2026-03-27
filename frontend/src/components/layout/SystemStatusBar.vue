<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { Cpu, Database, Activity, Zap, BrainCircuit, Brain } from 'lucide-vue-next'

const status = ref({
  aiSource: 'ollama',
  chatStatus: 'offline',
  embedStatus: 'offline',
  neo4j: 'offline',
  vram: '---',
  latency: '---'
})

const fetchStatus = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/system/status')
    if (response.ok) {
      status.value = await response.json()
    } else {
      status.value.chatStatus = 'offline'
      status.value.embedStatus = 'offline'
      status.value.neo4j = 'offline'
    }
  } catch (error) {
    status.value.chatStatus = 'offline'
    status.value.embedStatus = 'offline'
    status.value.neo4j = 'offline'
  }
}

const aiSourceLabel = computed(() => {
  return status.value.aiSource?.toLowerCase() === 'ollama' ? 'Ollama' : 'LLM'
})

const isSystemReady = computed(() => {
  return status.value.chatStatus === 'online' && 
         status.value.embedStatus === 'online' && 
         status.value.neo4j === 'online'
})

const systemSummary = computed(() => {
  if (isSystemReady.value) return '系统就绪'
  if (status.value.embedStatus === 'model_missing') return '向量模型缺失'
  if (status.value.chatStatus === 'model_missing') return '聊天模型缺失'
  return '检查连接'
})

let timer: any = null

onMounted(() => {
  fetchStatus()
  timer = setInterval(fetchStatus, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="system-status-bar">
    <div class="status-content">
      <!-- Left: System Status -->
      <div class="status-left">
        <div class="status-indicator">
          <div :class="['pulse-dot', isSystemReady ? 'online' : 'error']"></div>
          <span class="status-text">{{ systemSummary }}</span>
        </div>
        
        <div class="metric-group">
          <div class="metric-item" :title="`Chat: ${status.chatStatus}`">
            <BrainCircuit :size="14" class="metric-icon" />
            <span class="metric-label">{{ aiSourceLabel }}</span>
            <div :class="['status-light', status.chatStatus === 'online' ? 'online' : 'error']"></div>
          </div>
          <div class="metric-item" :title="`Embed: ${status.embedStatus}`">
            <Brain :size="14" class="metric-icon" />
            <span class="metric-label">Embed</span>
            <div :class="['status-light', status.embedStatus === 'online' ? 'online' : 'error']"></div>
          </div>
          <div class="metric-item" :title="`Neo4j: ${status.neo4j}`">
            <Database :size="14" class="metric-icon" />
            <span class="metric-label">Neo4j</span>
            <div :class="['status-light', status.neo4j === 'online' ? 'online' : 'error']"></div>
          </div>
          <div class="metric-item">
            <Cpu :size="14" class="metric-icon" />
            <span class="metric-label">VRAM</span>
            <span class="metric-value">{{ status.vram }}</span>
          </div>
          <div class="metric-item">
            <Activity :size="14" class="metric-icon" />
            <span class="metric-label">延迟</span>
            <span class="metric-value">{{ status.latency }}</span>
          </div>
        </div>
      </div>

      <!-- Right: Sync & Version -->
      <div class="status-right">
        <div class="sync-indicator">
          <Zap :size="12" />
          <span>神经同步</span>
          <span class="sync-value">0.994σ</span>
        </div>
        <div class="version-tag">v1.0.0</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.system-status-bar {
  height: 100%;
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border-top: 1px solid var(--color-glass-border);
  width: 100%;
}

.status-content {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  max-width: 100vw;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.pulse-dot.online {
  background: #10b981; /* Green */
  animation: pulse-green 2s ease-in-out infinite;
}

.pulse-dot.error {
  background: #ef4444; /* Red */
  animation: pulse-red 2s ease-in-out infinite;
}

@keyframes pulse-green {
  0%, 100% { 
    opacity: 1;
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4);
  }
  50% { 
    opacity: 0.8;
    box-shadow: 0 0 0 6px rgba(16, 185, 129, 0);
  }
}

@keyframes pulse-red {
  0%, 100% { 
    opacity: 1;
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
  }
  50% { 
    opacity: 0.8;
    box-shadow: 0 0 0 6px rgba(239, 68, 68, 0);
  }
}

.status-light {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-left: 4px;
}

.status-light.online {
  background: #10b981;
  box-shadow: 0 0 4px #10b981;
}

.status-light.error {
  background: #ef4444;
  box-shadow: 0 0 4px #ef4444;
}

.status-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text);
}

.metric-group {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-left: 24px;
  border-left: 1px solid var(--color-outline);
}

.metric-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
}

.metric-icon {
  color: var(--color-text-dim);
}

.metric-label {
  font-size: 11px;
  color: var(--color-text-dim);
}

.metric-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
  font-family: 'Fira Code', monospace;
}

.status-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sync-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--color-surface);
  border-radius: 6px;
  font-size: 11px;
  color: var(--color-text-dim);
  height: 24px;
}

.sync-value {
  color: var(--color-primary);
  font-weight: 600;
  font-family: 'Fira Code', monospace;
}

.version-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  background: var(--color-surface);
  border-radius: 4px;
  font-size: 10px;
  font-family: 'Fira Code', monospace;
  color: var(--color-text-dim);
  height: 24px;
}
</style>
