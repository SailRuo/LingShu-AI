<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  BrainCircuit,
  Brain,
  Database,
  Cpu,
  Activity,
  Zap,
  CheckCircle,
  AlertTriangle,
  XCircle,
  Server,
  HardDrive,
  Wifi
} from 'lucide-vue-next'

interface SystemStatus {
  aiSource: string
  chatStatus: string
  embedStatus: string
  neo4j: string
  vram: string
  latency: string
}

const status = ref<SystemStatus>({
  aiSource: 'ollama',
  chatStatus: 'offline',
  embedStatus: 'offline',
  neo4j: 'offline',
  vram: '---',
  latency: '---'
})

const loading = ref(true)

const fetchStatus = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/system/status')
    if (response.ok) {
      status.value = await response.json()
      loading.value = false
    } else {
      setOffline()
    }
  } catch (error) {
    setOffline()
  }
  loading.value = false
}

function setOffline() {
  status.value.chatStatus = 'offline'
  status.value.embedStatus = 'offline'
  status.value.neo4j = 'offline'
  status.value.vram = '---'
  status.value.latency = '---'
}

let timer: any = null

onMounted(() => {
  fetchStatus()
  timer = setInterval(fetchStatus, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const aiSourceLabel = computed(() => {
  return status.value.aiSource?.toLowerCase() === 'ollama' ? 'Ollama' : 'LLM'
})

const isSystemReady = computed(() => {
  return status.value.chatStatus === 'online' &&
         status.value.embedStatus === 'online' &&
         status.value.neo4j === 'online'
})

const systemHealthScore = computed(() => {
  let score = 0
  if (status.value.chatStatus === 'online') score += 35
  if (status.value.embedStatus === 'online') score += 35
  if (status.value.neo4j === 'online') score += 30
  return score
})

const getStatusInfo = (statusKey: string) => {
  switch (statusKey) {
    case 'online':
      return { label: '在线', color: 'success', icon: CheckCircle }
    case 'offline':
      return { label: '离线', color: 'error', icon: XCircle }
    case 'model_missing':
      return { label: '模型缺失', color: 'warning', icon: AlertTriangle }
    default:
      return { label: '未知', color: 'dim', icon: AlertTriangle }
  }
}

const components = computed(() => [
  {
    key: 'chat',
    name: '对话核心',
    subName: aiSourceLabel.value,
    status: status.value.chatStatus,
    icon: BrainCircuit,
    description: 'LLM 对话引擎'
  },
  {
    key: 'embed',
    name: '向量引擎',
    subName: 'Embedding',
    status: status.value.embedStatus,
    icon: Brain,
    description: '语义向量化'
  },
  {
    key: 'neo4j',
    name: '图数据库',
    subName: 'Neo4j',
    status: status.value.neo4j,
    icon: Database,
    description: '记忆存储核心'
  },
  {
    key: 'vram',
    name: '显存状态',
    subName: status.value.vram,
    status: status.value.vram !== '---' ? 'online' : 'offline',
    icon: Cpu,
    description: 'GPU 资源监控'
  },
  {
    key: 'latency',
    name: '响应延迟',
    subName: status.value.latency,
    status: status.value.latency !== '---' ? 'online' : 'offline',
    icon: Activity,
    description: '系统响应速度'
  }
])
</script>

<template>
  <div class="security-view">
    <!-- Header -->
    <header class="security-header">
      <div class="header-content">
        <div class="title-section">
          <Activity :size="28" class="title-icon" />
          <h1 class="page-title">系统状态</h1>
        </div>
        <div class="health-summary">
          <div class="health-orb" :class="{ healthy: isSystemReady }">
            <span class="health-score">{{ systemHealthScore }}%</span>
          </div>
          <span class="health-label">{{ isSystemReady ? '系统就绪' : '部分服务异常' }}</span>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <div class="security-content">
      <!-- Star Orbit Monitor Panel -->
      <div class="orbit-monitor-panel">
        <div class="panel-header">
          <div class="panel-title-group">
            <Zap :size="18" class="panel-icon" />
            <h2 class="panel-title">星轨监控面板</h2>
          </div>
          <span class="panel-subtitle">System Neural Network Status</span>
        </div>

        <div class="orbit-grid">
          <!-- Health Orb (Center) -->
          <div class="orbit-center">
            <div class="central-orb">
              <div class="orb-core" :class="{ healthy: isSystemReady }">
                <div class="orb-ring ring-1"></div>
                <div class="orb-ring ring-2"></div>
                <div class="orb-ring ring-3"></div>
                <Activity :size="48" class="orb-icon" :class="{ healthy: isSystemReady }" />
              </div>
              <div class="orb-label">系统核心</div>
              <div class="orb-status">{{ isSystemReady ? '正常运行' : '需要检查' }}</div>
            </div>
          </div>

          <!-- Component Cards (Orbiting) -->
          <div class="orbit-components">
            <div
              v-for="component in components"
              :key="component.key"
              class="component-card"
              :class="[getStatusInfo(component.status).color]"
            >
              <div class="card-glow"></div>
              <div class="card-content">
                <div class="card-header">
                  <component :is="component.icon" :size="24" class="component-icon" />
                  <div :class="['status-indicator', getStatusInfo(component.status).color]">
                    <div class="indicator-dot"></div>
                  </div>
                </div>

                <div class="card-body">
                  <div class="component-name">{{ component.name }}</div>
                  <div class="component-sub">{{ component.subName }}</div>
                  <div class="component-desc">{{ component.description }}</div>
                </div>

                <div class="card-footer">
                  <div class="status-bar">
                    <div
                      class="status-fill"
                      :class="[getStatusInfo(component.status).color]"
                      :style="{ width: component.status === 'online' ? '100%' : '0%' }"
                    ></div>
                  </div>
                  <span class="status-text">{{ getStatusInfo(component.status).label }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- System Metrics -->
      <div class="metrics-panel">
        <div class="panel-header">
          <Server :size="18" class="panel-icon" />
          <h2 class="panel-title">系统指标</h2>
        </div>

        <div class="metrics-grid">
          <div class="metric-card">
            <HardDrive :size="20" class="metric-icon" />
            <div class="metric-content">
              <span class="metric-label">VRAM 使用</span>
              <span class="metric-value">{{ status.vram }}</span>
            </div>
          </div>

          <div class="metric-card">
            <Activity :size="20" class="metric-icon" />
            <div class="metric-content">
              <span class="metric-label">响应延迟</span>
              <span class="metric-value">{{ status.latency }}</span>
            </div>
          </div>

          <div class="metric-card">
            <Zap :size="20" class="metric-icon" />
            <div class="metric-content">
              <span class="metric-label">系统版本</span>
              <span class="metric-value">v1.0.0</span>
            </div>
          </div>        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.security-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* Header */
.security-header {
  padding: 24px 32px;
  border-bottom: 1px solid var(--color-outline);
  background: var(--color-surface);
  flex-shrink: 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.title-icon {
  color: var(--color-primary);
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.health-summary {
  display: flex;
  align-items: center;
  gap: 16px;
}

.health-orb {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-surface-elevated);
  border: 2px solid var(--color-outline);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: all 0.3s ease;
}

.health-orb.healthy {
  border-color: var(--color-success);
  box-shadow: 0 0 20px rgba(74, 222, 128, 0.3);
}

.health-score {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  font-family: 'Fira Code', monospace;
}

.health-label {
  font-size: 14px;
  color: var(--color-text-dim);
}

/* Content Area */
.security-content {
  flex: 1;
  overflow: auto;
  padding: 32px;
}

/* Orbit Monitor Panel */
.orbit-monitor-panel {
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline);
}

.panel-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-icon {
  color: var(--color-primary);
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.panel-subtitle {
  font-size: 12px;
  color: var(--color-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

/* Orbit Grid Layout */
.orbit-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 32px;
}

/* Central Orb */
.orbit-center {
  display: flex;
  justify-content: center;
  align-items: center;
}

.central-orb {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.orb-core {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: var(--color-surface-elevated);
  border: 3px solid var(--color-outline);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: all 0.5s ease;
}

.orb-core.healthy {
  border-color: var(--color-success);
  box-shadow:
    0 0 40px rgba(74, 222, 128, 0.4),
    inset 0 0 30px rgba(74, 222, 128, 0.1);
}

.orb-ring {
  position: absolute;
  border-radius: 50%;
  border: 2px solid transparent;
  animation: rotate 8s linear infinite;
}

.ring-1 {
  width: 180px;
  height: 180px;
  border-top-color: var(--color-primary);
  opacity: 0.3;
}

.ring-2 {
  width: 200px;
  height: 200px;
  border-right-color: var(--color-accent);
  opacity: 0.2;
  animation-direction: reverse;
  animation-duration: 12s;
}

.ring-3 {
  width: 220px;
  height: 220px;
  border-bottom-color: var(--color-primary);
  opacity: 0.15;
  animation-duration: 15s;
}

@keyframes rotate {
  to { transform: rotate(360deg); }
}

.orb-icon {
  color: var(--color-text-dim);
  transition: all 0.3s ease;
}

.orb-icon.healthy {
  color: var(--color-success);
  filter: drop-shadow(0 0 12px var(--color-success));
}

.orb-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-top: 24px;
}

.orb-status {
  font-size: 12px;
  color: var(--color-text-dim);
}

/* Component Cards */
.orbit-components {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 20px;
}

.component-card {
  position: relative;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
}

.component-card:hover {
  transform: translateY(-4px);
  border-color: var(--color-primary);
  box-shadow: 0 8px 24px var(--color-primary-dim);
}

.card-glow {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    135deg,
    var(--color-primary-dim) 0%,
    transparent 50%,
    transparent 100%
  );
  opacity: 0;
  transition: opacity 0.3s ease;
}

.component-card:hover .card-glow {
  opacity: 0.5;
}

.card-content {
  padding: 20px;
  position: relative;
  z-index: 1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.component-icon {
  color: var(--color-primary);
}

.status-indicator {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.status-indicator.success {
  background: rgba(74, 222, 128, 0.2);
  border: 2px solid var(--color-success);
}

.status-indicator.error {
  background: rgba(248, 113, 113, 0.2);
  border: 2px solid var(--color-error);
}

.status-indicator.warning {
  background: rgba(251, 191, 36, 0.2);
  border: 2px solid var(--color-warning);
}

.status-indicator.dim {
  background: rgba(148, 163, 184, 0.2);
  border: 2px solid var(--color-text-dim);
}

.indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-indicator.success .indicator-dot {
  background: var(--color-success);
  box-shadow: 0 0 8px var(--color-success);
  animation: pulse-green 2s ease-in-out infinite;
}

.status-indicator.error .indicator-dot {
  background: var(--color-error);
  box-shadow: 0 0 8px var(--color-error);
  animation: pulse-red 2s ease-in-out infinite;
}

.status-indicator.warning .indicator-dot {
  background: var(--color-warning);
  box-shadow: 0 0 8px var(--color-warning);
}

.status-indicator.dim .indicator-dot {
  background: var(--color-text-dim);
}

@keyframes pulse-green {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@keyframes pulse-red {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.card-body {
  margin-bottom: 16px;
}

.component-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 4px;
}

.component-sub {
  font-size: 12px;
  color: var(--color-primary);
  font-family: 'Fira Code', monospace;
  margin-bottom: 6px;
}

.component-desc {
  font-size: 11px;
  color: var(--color-text-dim);
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-bar {
  flex: 1;
  height: 6px;
  background: var(--color-outline);
  border-radius: 3px;
  overflow: hidden;
}

.status-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.status-fill.success { background: var(--color-success); }
.status-fill.error { background: var(--color-error); }
.status-fill.warning { background: var(--color-warning); }
.status-fill.dim { background: var(--color-text-dim); }

.status-text {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  min-width: 50px;
}

/* Metrics Panel */
.metrics-panel {
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border);
  border-radius: 16px;
  padding: 24px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.metric-card:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-elevated);
  transform: translateY(-2px);
}

.metric-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.metric-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.metric-label {
  font-size: 12px;
  color: var(--color-text-dim);
}

.metric-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  font-family: 'Fira Code', monospace;
}

/* Responsive Design */
@media (max-width: 1200px) {
  .orbit-grid {
    grid-template-columns: 1fr;
  }

  .orbit-center {
    order: -1;
  }
}

@media (max-width: 768px) {
  .security-header {
    padding: 20px 16px;
  }

  .header-content {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .health-summary {
    align-self: flex-end;
  }

  .security-content {
    padding: 16px;
  }

  .orbit-monitor-panel,
  .metrics-panel {
    padding: 16px;
  }

  .orbit-components {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .orb-ring {
    animation: none !important;
  }

  .component-card:hover {
    transform: none;
  }

  .metric-card:hover {
    transform: none;
  }

  .orb-icon.healthy {
    filter: none;
  }
}
</style>
