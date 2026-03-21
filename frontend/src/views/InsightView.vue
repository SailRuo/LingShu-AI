<script setup lang="ts">
import { onMounted, ref, onBeforeUnmount, shallowRef, h, computed } from 'vue'
import { NButton, NIcon, NDropdown, useMessage, NScrollbar, NTag, NProgress } from 'naive-ui'
import { 
  RefreshCcw, Activity, Trash2, Eye, Clock, 
  Search, Target, ZoomIn, ZoomOut
} from 'lucide-vue-next'
// @ts-ignore
import Neovis from 'neovis.js'
import { useThemeStore } from '@/stores/themeStore'

const viz = shallowRef<any>(null)
const isLoaded = ref(false)
const selectedNode = ref<any>(null)
const message = useMessage()
const themeStore = useThemeStore()

const stats = ref({
  density: '42.5',
  nodes: '14,892',
  edges: '56,310',
  latency: '38'
})

function formatTime(ts: number | string): string {
  const timestamp = typeof ts === 'number' ? ts : new Date(ts).getTime()
  if (!timestamp || isNaN(timestamp)) return '未知时间'
  const diff = Math.floor((Date.now() - timestamp) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

function toISODateString(val: unknown): string {
  if (!val) return new Date().toISOString()
  if (typeof val === 'string') return val
  if (typeof val === 'number') return new Date(val).toISOString()
  return new Date().toISOString()
}

const showDropdown = ref(false)
const dropdownX = ref(0)
const dropdownY = ref(0)
const rightClickedNode = ref<any>(null)

const dropdownOptions = [
  {
    label: '查看关联详情',
    key: 'inspect',
    icon: () => h(NIcon, null, { default: () => h(Eye) })
  },
  {
    label: '移除此节点',
    key: 'delete',
    icon: () => h(NIcon, { color: 'var(--color-error)' }, { default: () => h(Trash2) })
  }
]

const themeColors = computed(() => ({
  primary: themeStore.current.cssVars['--color-primary'],
  accent: themeStore.current.cssVars['--color-accent'],
  nodeUser: themeStore.current.cssVars['--color-node-user'],
  nodeFact: themeStore.current.cssVars['--color-node-fact'],
  edge: themeStore.current.cssVars['--color-edge'],
  glow: themeStore.current.cssVars['--color-glow'],
  isDark: themeStore.current.isDark,
}))

const config = computed(() => ({
  containerId: 'viz',
  nonFlat: true,
  neo4j: {
    serverUrl: 'bolt://localhost:7687',
    serverUser: 'neo4j',
    serverPassword: 'lingshu123'
  },
  labels: {
    'User': {
      caption: 'name',
      size: 55,
      color: {
        background: 'rgba(255,255,255,0.03)',
        border: themeColors.value.nodeUser,
        highlight: { background: 'rgba(255,255,255,0.08)', border: themeColors.value.nodeUser }
      },
      font: { size: 13, color: themeColors.value.nodeUser, face: 'Inter, system-ui' },
      shape: 'dot',
      borderWidth: 2
    },
    'Fact': {
      caption: 'content',
      size: 'importance',
      color: {
        background: 'rgba(255,255,255,0.02)',
        border: themeColors.value.nodeFact,
        highlight: { background: 'rgba(255,255,255,0.05)', border: themeColors.value.primary }
      },
      font: { size: 11, color: themeColors.value.nodeFact, face: 'Inter, system-ui' },
      shape: 'dot',
      borderWidth: 1.5
    }
  },
  relationships: {
    'HAS_FACT': {
      thickness: 1,
      color: themeColors.value.edge,
      arrows: { to: { enabled: true, scaleFactor: 0.3 } },
      smooth: { type: 'continuous' }
    }
  },
  initialCypher: "MATCH (u:User)-[r:HAS_FACT]->(f:Fact) RETURN u,r,f",
  visConfig: {
    physics: {
      stabilization: { iterations: 150 },
      barnesHut: { gravitationalConstant: -3000, springLength: 200 }
    },
    interaction: { hover: true, tooltipDelay: 300 }
  }
}))

function initViz() {
  if (viz.value) viz.value.clearNetwork()
  try {
    viz.value = new Neovis(config.value as any)
    viz.value.registerOnEvent('completed', () => {
      isLoaded.value = true
      // Neovis 的 registerOnEvent 有白名单，不支持 oncontext
      // 我们直接在底层的 vis.js network 实例上注册事件
      const network = viz.value.network || viz.value._network
      if (network) {
        network.on('oncontext', (event: any) => {
          event.event.preventDefault()
          const nodeId = network.getNodeAt(event.pointer.DOM)
          if (nodeId) {
            const node = viz.value.nodes.get(nodeId)
            rightClickedNode.value = node
            dropdownX.value = event.event.clientX
            dropdownY.value = event.event.clientY
            showDropdown.value = true
          }
        })
      }
    })
    viz.value.registerOnEvent('clickNode', (event: any) => {
      const node = event.node
      selectedNode.value = {
        id: node.id,
        label: node.raw.properties.name || node.raw.properties.content,
        type: node.raw.labels[0] === 'User' ? '核心用户' : '记忆碎片',
        importance: node.raw.properties.importance || 0.5,
        observedAt: toISODateString(node.raw.properties.observedAt)
      }
    })
    viz.value.render()
  } catch (err) {
    console.error('可视化引擎启动失败:', err)
  }
}

async function handleDeleteFact() {
  if (!rightClickedNode.value) return
  try {
    // 移除 ID 前缀 (例如 "fact_123" -> "123") 以匹配后端 Long 类型
    const rawId = rightClickedNode.value.id
    const factId = typeof rawId === 'string' ? rawId.replace('fact_', '') : rawId
    
    await fetch(`/api/memory/fact/${factId}`, { method: 'DELETE' })
    message.success('节点已成功移除')
    refreshGraph()
  } catch (err) {
    message.error('同步错误：操作失败')
  } finally {
    showDropdown.value = false
    rightClickedNode.value = null
  }
}

function handleSelectDropdown(key: string) {
  if (key === 'delete') handleDeleteFact()
  else if (key === 'inspect') {
    selectedNode.value = {
      id: rightClickedNode.value.id,
      label: rightClickedNode.value.raw.properties.name || rightClickedNode.value.raw.properties.content,
      type: rightClickedNode.value.raw.labels[0] === 'User' ? '核心用户' : '记忆碎片',
      importance: rightClickedNode.value.raw.properties.importance || 0.5,
      observedAt: toISODateString(rightClickedNode.value.raw.properties.observedAt)
    }
    showDropdown.value = false
  }
}

function refreshGraph() {
  isLoaded.value = false
  if (viz.value) viz.value.reload()
  else initViz()
}

onMounted(() => setTimeout(initViz, 500))
onBeforeUnmount(() => { if (viz.value) viz.value.clearNetwork() })
</script>

<template>
  <div class="insight-view">
    <div class="insight-content">
      <div class="graph-area">
        <div id="viz" class="viz-container" :class="{ 'is-loading': !isLoaded }"></div>
        
        <div v-if="!isLoaded" class="loading-overlay">
          <div class="loading-content">
            <Activity :size="28" class="loading-icon" />
            <span class="loading-text">拓扑映射中...</span>
          </div>
        </div>

        <div class="toolbar">
          <div class="toolbar-stats">
            <div class="stat-item">
              <span class="stat-label">密度</span>
              <span class="stat-value">{{ stats.density }}σ</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-label">节点</span>
              <span class="stat-value">{{ stats.nodes }}</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-label">延迟</span>
              <span class="stat-value">{{ stats.latency }}ms</span>
            </div>
          </div>
          
          <div class="toolbar-actions">
            <button class="tool-btn" title="搜索">
              <Search :size="16" />
            </button>
            <button class="tool-btn" title="定位">
              <Target :size="16" />
            </button>
            <button class="tool-btn" title="放大">
              <ZoomIn :size="16" />
            </button>
            <button class="tool-btn" title="缩小">
              <ZoomOut :size="16" />
            </button>
            <div class="tool-divider"></div>
            <button class="tool-btn primary" @click="refreshGraph" title="同步图谱">
              <RefreshCcw :size="16" />
            </button>
          </div>
        </div>
      </div>

      <transition name="panel">
        <aside v-if="selectedNode" class="detail-panel">
          <div class="panel-header">
            <div class="panel-title">
              <span class="panel-label">节点解析</span>
              <h3 class="panel-heading">记忆图谱</h3>
            </div>
            <button class="close-btn" @click="selectedNode = null">
              <n-icon :size="18"><RefreshCcw :size="16" style="transform: rotate(90deg)" /></n-icon>
            </button>
          </div>

          <n-scrollbar class="panel-body">
            <div class="panel-content">
              <section class="info-section">
                <div class="tag-row">
                  <n-tag 
                    :bordered="false" 
                    size="small" 
                    :type="selectedNode.type === '核心用户' ? 'success' : 'warning'"
                  >
                    {{ selectedNode.type }}
                  </n-tag>
                  <span class="node-id">ID: {{ String(selectedNode.id).slice(0, 10) }}</span>
                </div>
                <div class="content-card">
                  <p class="node-label">{{ selectedNode.label }}</p>
                </div>
              </section>

              <section class="metric-section">
                <div class="metric-header">
                  <span class="metric-title">关联置信度</span>
                  <span class="metric-value">{{ (selectedNode.importance * 100).toFixed(1) }}%</span>
                </div>
                <n-progress
                  type="line"
                  :percentage="selectedNode.importance * 100"
                  :show-indicator="false"
                  :height="4"
                />
                <div class="metric-grid">
                  <div class="metric-card">
                    <span class="metric-card-label">活跃度</span>
                    <span class="metric-card-value">极高频</span>
                  </div>
                  <div class="metric-card">
                    <span class="metric-card-label">状态</span>
                    <span class="metric-card-value highlight">核心常驻</span>
                  </div>
                </div>
              </section>

              <section class="time-section">
                <span class="section-title">时间戳记</span>
                <div class="time-card">
                  <div class="time-icon">
                    <Clock :size="18" />
                  </div>
                  <div class="time-info">
                    <span class="time-relative">{{ formatTime(selectedNode.observedAt) }}</span>
                    <span class="time-absolute">{{ new Date(selectedNode.observedAt).toLocaleString() }}</span>
                  </div>
                </div>
              </section>

              <div class="panel-actions">
                <n-button size="medium" block secondary class="action-btn">
                  追溯知识链路
                </n-button>
                <n-button 
                  secondary 
                  type="error" 
                  size="medium" 
                  circle 
                  class="delete-btn"
                  @click="rightClickedNode = selectedNode; handleDeleteFact()"
                >
                  <template #icon>
                    <n-icon :size="18"><Trash2 /></n-icon>
                  </template>
                </n-button>
              </div>
            </div>
          </n-scrollbar>
        </aside>
      </transition>
    </div>

    <n-dropdown
      placement="bottom-start" 
      trigger="manual"
      :x="dropdownX" 
      :y="dropdownY" 
      :options="dropdownOptions"
      :show="showDropdown" 
      :on-clickoutside="() => (showDropdown = false)"
      @select="handleSelectDropdown"
    />
  </div>
</template>

<style scoped>
.insight-view {
  height: 100%;
  position: relative;
  background: transparent;
}

.insight-content {
  height: 100%;
  display: flex;
  position: relative;
}

.graph-area {
  flex: 1;
  position: relative;
  min-width: 0;
}

.viz-container {
  width: 100%;
  height: 100%;
  transition: all 0.5s ease;
}

.viz-container.is-loading {
  filter: blur(8px);
  opacity: 0.3;
}

.loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.loading-icon {
  color: var(--color-primary);
  animation: pulse 2s ease-in-out infinite;
}

.loading-text {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--color-text-dim);
  text-transform: uppercase;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

.toolbar {
  position: absolute;
  bottom: 24px;
  left: 24px;
  right: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border);
  border-radius: 16px;
  z-index: 10;
}

.toolbar-stats {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-label {
  font-size: 11px;
  color: var(--color-text-dim);
}

.stat-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
  font-family: 'Fira Code', monospace;
}

.stat-divider {
  width: 1px;
  height: 16px;
  background: var(--color-outline);
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tool-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: transparent;
  border: none;
  color: var(--color-text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
}

.tool-btn:hover {
  background: var(--color-surface);
  color: var(--color-primary);
}

.tool-btn.primary {
  background: var(--color-primary-dim);
  color: var(--color-primary);
}

.tool-btn.primary:hover {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.tool-divider {
  width: 1px;
  height: 20px;
  background: var(--color-outline);
  margin: 0 8px;
}

.detail-panel {
  width: 360px;
  border-left: 1px solid var(--color-glass-border);
  background: var(--color-glass-bg);
  backdrop-filter: blur(24px);
  display: flex;
  flex-direction: column;
  z-index: 30;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--color-outline);
}

.panel-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.panel-heading {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: transparent;
  border: none;
  color: var(--color-text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: var(--color-surface);
  color: var(--color-text);
}

.panel-body {
  flex: 1;
  min-height: 0;
}

.panel-content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tag-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.node-id {
  font-size: 10px;
  color: var(--color-text-dim);
  font-family: 'Fira Code', monospace;
}

.content-card {
  padding: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}

.content-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--color-primary);
  opacity: 0.5;
}

.node-label {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text);
  margin: 0;
}

.metric-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.metric-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-title {
  font-size: 11px;
  color: var(--color-text-dim);
  letter-spacing: 0.05em;
}

.metric-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  font-family: 'Fira Code', monospace;
}

.metric-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 8px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 10px;
}

.metric-card-label {
  font-size: 10px;
  color: var(--color-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.metric-card-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
}

.metric-card-value.highlight {
  color: var(--color-primary);
}

.time-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  font-size: 11px;
  color: var(--color-text-dim);
  letter-spacing: 0.05em;
}

.time-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  border-radius: 12px;
}

.time-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: var(--color-primary-dim);
  border-radius: 10px;
  color: var(--color-primary);
}

.time-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.time-relative {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.time-absolute {
  font-size: 10px;
  color: var(--color-text-dim);
  font-family: 'Fira Code', monospace;
}

.panel-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--color-outline);
}

.action-btn {
  flex: 1;
  height: 44px;
  border-radius: 10px;
  font-weight: 600;
}

.delete-btn {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
}

.panel-enter-active,
.panel-leave-active {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.panel-enter-from,
.panel-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

:deep(.vis-network) {
  outline: none;
  background: transparent !important;
}

:deep(.n-scrollbar-rail) {
  background: transparent !important;
}

:deep(.n-progress) {
  --n-fill-color: var(--color-primary);
  --n-rail-color: var(--color-surface);
}
</style>
