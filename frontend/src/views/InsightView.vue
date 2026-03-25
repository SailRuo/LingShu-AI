<script setup lang="ts">
import { onMounted, ref, onBeforeUnmount, shallowRef, h, computed, watch } from 'vue'
import { NButton, NIcon, NDropdown, useMessage, NScrollbar, NTag, NProgress } from 'naive-ui'
import { 
  RefreshCcw, Trash2, Eye, Clock, 
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
  surface: themeStore.current.cssVars['--color-surface'],
  outline: themeStore.current.cssVars['--color-outline'],
  background: themeStore.current.cssVars['--color-background'],
  isDark: themeStore.current.isDark,
}))

const sceneStyle = computed(() => ({
  '--graph-glow-primary': themeColors.value.glow,
  '--graph-glow-accent': themeColors.value.accent,
  '--graph-star-core': themeColors.value.primary,
  '--graph-star-secondary': themeColors.value.nodeFact,
  '--graph-nebula-a': themeColors.value.primary,
  '--graph-nebula-b': themeColors.value.accent,
  '--graph-nebula-c': themeColors.value.nodeUser,
}))

const config = computed(() => {
  const nodeFactColor = themeColors.value.nodeFact
  const nodeUserColor = themeColors.value.nodeUser
  
  return {
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
        size: 48,
        color: {
          background: 'transparent',
          border: nodeUserColor,
          highlight: { 
            background: 'rgba(52, 211, 153, 0.15)', 
            border: nodeUserColor 
          }
        },
        font: { 
          size: 12, 
          color: nodeUserColor, 
          face: 'Inter, system-ui',
          strokeWidth: 3,
          strokeColor: 'rgba(0, 0, 0, 0.8)'
        },
        shape: 'hexagon',
        borderWidth: 3,
        shadow: {
          enabled: true,
          color: nodeUserColor,
          size: 22,
          x: 0,
          y: 0
        },
        opacity: 0.95
      },
      'Fact': {
        caption: 'content',
        size: 'importance',
        color: {
          background: 'transparent',
          border: nodeFactColor,
          highlight: { 
            background: 'rgba(251, 191, 36, 0.12)', 
            border: themeColors.value.primary 
          }
        },
        font: { 
          size: 10, 
          color: nodeFactColor, 
          face: 'Inter, system-ui',
          strokeWidth: 2,
          strokeColor: 'rgba(0, 0, 0, 0.7)'
        },
        shape: 'diamond',
        borderWidth: 2,
        shadow: {
          enabled: true,
          color: nodeFactColor,
          size: 18,
          x: 0,
          y: 0
        },
        opacity: 0.9
      }
    },
    relationships: {
      'HAS_FACT': {
        thickness: 0.08,
        color: themeColors.value.edge,
        arrows: { 
          to: { 
            enabled: true, 
            scaleFactor: 0.4,
            type: 'arrow'
          } 
        },
        smooth: { 
          type: 'dynamic',
          roundness: 0.22
        },
        dashes: [8, 12],
        shadow: {
          enabled: true,
          color: 'rgba(52, 211, 153, 0.3)',
          size: 5,
          x: 0,
          y: 0
        },
        opacity: 0.7
      }
    },
    initialCypher: "MATCH (u:User)-[r:HAS_FACT]->(f:Fact) RETURN u,r,f",
    visConfig: {
      physics: {
        stabilization: { 
          iterations: 200,
          updateInterval: 25
        },
        barnesHut: { 
          gravitationalConstant: -5200, 
          springLength: 220,
          springConstant: 0.022,
          damping: 0.2,
          avoidOverlap: 0.4
        },
        maxVelocity: 50,
        minVelocity: 0.1
      },
      interaction: { 
        hover: true, 
        tooltipDelay: 200,
        navigationButtons: false,
        zoomView: true,
        dragView: true
      },
      nodes: {
        scaling: {
            min: 10,
            max: 42,
          label: {
            enabled: true,
            min: 8,
            max: 14
          }
        }
      },
      edges: {
        scaling: {
          min: 1,
          max: 3
        },
        smooth: {
          forceDirection: 'none'
        }
      }
    }
  }
})

function injectCustomStyles() {
  const styleId = 'neovis-custom-styles'
  const previous = document.getElementById(styleId)
  if (previous) previous.remove()

  const style = document.createElement('style')
  style.id = styleId
  style.textContent = `
    .vis-network {
      background: transparent !important;
    }
    
    .vis-node {
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }
    
    .vis-node:hover {
      filter: brightness(1.3);
    }
    
    @keyframes nodePulse {
      0%, 100% { 
        filter: drop-shadow(0 0 8px var(--glow-color));
      }
      50% { 
        filter: drop-shadow(0 0 20px var(--glow-color));
      }
    }
    
    @keyframes edgeFlow {
      0% { stroke-dashoffset: 24; }
      100% { stroke-dashoffset: 0; }
    }
    
    .vis-edge {
      animation: edgeFlow 2s linear infinite;
    }

    .vis-label {
      letter-spacing: 0.04em;
    }
  `
  document.head.appendChild(style)
}

function initViz() {
  if (viz.value) viz.value.clearNetwork()
  try {
    injectCustomStyles()
    viz.value = new Neovis(config.value as any)
    viz.value.registerOnEvent('completed', () => {
      isLoaded.value = true
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
        
        network.on('hoverNode', (_params: any) => {
          const canvas = document.getElementById('viz')?.querySelector('canvas')
          if (canvas) {
            canvas.style.cursor = 'pointer'
          }
        })
        
        network.on('blurNode', () => {
          const canvas = document.getElementById('viz')?.querySelector('canvas')
          if (canvas) {
            canvas.style.cursor = 'default'
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

function withNetwork(callback: (network: any) => void) {
  const network = viz.value?.network || viz.value?._network
  if (network) callback(network)
}

function focusGraph() {
  withNetwork((network) => {
    network.fit({
      animation: {
        duration: 700,
        easingFunction: 'easeInOutQuad'
      }
    })
  })
}

function zoomGraph(direction: 'in' | 'out') {
  withNetwork((network) => {
    const scale = network.getScale?.() ?? 1
    const factor = direction === 'in' ? 1.18 : 0.84
    network.moveTo({
      scale: Math.max(0.2, Math.min(3, scale * factor)),
      animation: {
        duration: 260,
        easingFunction: 'easeInOutQuad'
      }
    })
  })
}

watch(themeColors, () => {
  injectCustomStyles()
  if (viz.value?.network || viz.value?._network) {
    refreshGraph()
  }
}, { deep: true })

onMounted(() => setTimeout(initViz, 500))
onBeforeUnmount(() => { 
  if (viz.value) viz.value.clearNetwork()
  const customStyle = document.getElementById('neovis-custom-styles')
  if (customStyle) customStyle.remove()
})
</script>

<template>
  <div class="insight-view" :style="sceneStyle">
    <div class="insight-content">
      <div class="graph-area">
        <div class="starfield-layer starfield-back"></div>
        <div class="starfield-layer starfield-mid"></div>
        <div class="starfield-layer nebula-layer"></div>
        <div class="starfield-grid"></div>
        <div class="graph-halo graph-halo-a"></div>
        <div class="graph-halo graph-halo-b"></div>
        <div id="viz" class="viz-container" :class="{ 'is-loading': !isLoaded }"></div>
        
        <div v-if="!isLoaded" class="loading-overlay">
          <div class="loading-content">
            <div class="loading-orb">
              <div class="orb-core"></div>
              <div class="orb-ring"></div>
            </div>
            <span class="loading-text">拓扑映射中</span>
            <div class="loading-progress">
              <div class="progress-bar"></div>
            </div>
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
            <button class="tool-btn" title="定位" @click="focusGraph">
              <Target :size="16" />
            </button>
            <button class="tool-btn" title="放大" @click="zoomGraph('in')">
              <ZoomIn :size="16" />
            </button>
            <button class="tool-btn" title="缩小" @click="zoomGraph('out')">
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
  overflow: hidden;
  border-radius: 28px;
  isolation: isolate;
  background:
    radial-gradient(circle at 20% 20%, color-mix(in srgb, var(--graph-nebula-a) 12%, transparent), transparent 28%),
    radial-gradient(circle at 82% 18%, color-mix(in srgb, var(--graph-nebula-b) 14%, transparent), transparent 30%),
    radial-gradient(circle at 50% 78%, color-mix(in srgb, var(--graph-nebula-c) 12%, transparent), transparent 34%);
  border: 1px solid color-mix(in srgb, var(--color-outline) 70%, transparent);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--color-text-inverse) 3%, transparent),
    0 24px 80px color-mix(in srgb, var(--graph-glow-primary) 18%, transparent);
}

.starfield-layer,
.starfield-grid,
.graph-halo {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.starfield-back {
  opacity: 0.45;
  background-image:
    radial-gradient(circle at 12% 18%, color-mix(in srgb, var(--graph-star-core) 90%, white) 0 1px, transparent 1.5px),
    radial-gradient(circle at 72% 24%, color-mix(in srgb, var(--graph-star-secondary) 75%, white) 0 1px, transparent 1.5px),
    radial-gradient(circle at 36% 72%, color-mix(in srgb, var(--graph-star-core) 70%, white) 0 1.4px, transparent 1.8px),
    radial-gradient(circle at 82% 64%, color-mix(in srgb, var(--graph-star-secondary) 85%, white) 0 1.1px, transparent 1.8px),
    radial-gradient(circle at 24% 52%, rgba(255,255,255,0.9) 0 0.9px, transparent 1.6px);
  animation: driftSlow 28s linear infinite;
}

.starfield-mid {
  opacity: 0.3;
  background-image:
    radial-gradient(circle at 18% 84%, color-mix(in srgb, var(--graph-star-secondary) 80%, white) 0 1.2px, transparent 1.9px),
    radial-gradient(circle at 58% 44%, color-mix(in srgb, var(--graph-star-core) 70%, white) 0 1.3px, transparent 2px),
    radial-gradient(circle at 88% 34%, rgba(255,255,255,0.85) 0 1px, transparent 1.7px),
    radial-gradient(circle at 66% 82%, color-mix(in srgb, var(--graph-star-core) 55%, white) 0 0.9px, transparent 1.6px);
  transform: scale(1.08);
  animation: driftReverse 36s linear infinite;
}

.nebula-layer {
  opacity: 0.34;
  background:
    radial-gradient(circle at 14% 24%, color-mix(in srgb, var(--graph-nebula-a) 24%, transparent), transparent 26%),
    radial-gradient(circle at 78% 22%, color-mix(in srgb, var(--graph-nebula-b) 20%, transparent), transparent 28%),
    radial-gradient(circle at 62% 72%, color-mix(in srgb, var(--graph-nebula-c) 18%, transparent), transparent 32%);
  filter: blur(26px);
  animation: pulseNebula 12s ease-in-out infinite;
}

.starfield-grid {
  opacity: 0.2;
  background-image:
    linear-gradient(color-mix(in srgb, var(--color-outline) 60%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--color-outline) 60%, transparent) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at center, black 35%, transparent 92%);
}

.graph-halo {
  filter: blur(48px);
  opacity: 0.28;
}

.graph-halo-a {
  background: radial-gradient(circle at 30% 30%, var(--graph-glow-primary), transparent 42%);
}

.graph-halo-b {
  background: radial-gradient(circle at 72% 58%, var(--graph-glow-accent), transparent 38%);
}

@keyframes driftSlow {
  from { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(-1.5%, 1.5%, 0) scale(1.02); }
  to { transform: translate3d(0, 0, 0) scale(1); }
}

@keyframes driftReverse {
  from { transform: translate3d(0, 0, 0) scale(1.08); }
  50% { transform: translate3d(1.8%, -1.4%, 0) scale(1.11); }
  to { transform: translate3d(0, 0, 0) scale(1.08); }
}

@keyframes pulseNebula {
  0%, 100% { opacity: 0.24; transform: scale(1); }
  50% { opacity: 0.38; transform: scale(1.04); }
}

.viz-container {
  width: 100%;
  height: 100%;
  transition: all 0.5s ease;
  position: relative;
  z-index: 2;
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
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(4px);
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.loading-orb {
  position: relative;
  width: 60px;
  height: 60px;
}

.orb-core {
  position: absolute;
  inset: 18px;
  background: radial-gradient(circle at 30% 30%, var(--color-primary), transparent 70%);
  background-color: var(--color-primary);
  border-radius: 50%;
  box-shadow: 
    0 0 20px var(--color-primary),
    0 0 40px rgba(52, 211, 153, 0.4),
    inset 0 0 15px rgba(255, 255, 255, 0.3);
  animation: orbPulse 2s ease-in-out infinite;
}

.orb-ring {
  position: absolute;
  inset: 8px;
  border: 2px solid var(--color-primary);
  border-radius: 50%;
  opacity: 0.5;
  animation: orbRing 2s ease-in-out infinite;
  box-shadow: 0 0 10px var(--color-primary);
}

@keyframes orbPulse {
  0%, 100% { 
    transform: scale(1); 
    opacity: 1;
    box-shadow: 
      0 0 20px var(--color-primary),
      0 0 40px rgba(52, 211, 153, 0.4);
  }
  50% { 
    transform: scale(1.15); 
    opacity: 0.8;
    box-shadow: 
      0 0 30px var(--color-primary),
      0 0 60px rgba(52, 211, 153, 0.6);
  }
}

@keyframes orbRing {
  0%, 100% { 
    transform: scale(1); 
    opacity: 0.5;
  }
  50% { 
    transform: scale(1.2); 
    opacity: 0.2;
  }
}

.loading-text {
  font-size: 12px;
  letter-spacing: 0.25em;
  color: var(--color-text-dim);
  text-transform: uppercase;
  font-weight: 500;
}

.loading-progress {
  width: 120px;
  height: 2px;
  background: var(--color-surface);
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, transparent, var(--color-primary), transparent);
  animation: progressFlow 1.5s ease-in-out infinite;
}

@keyframes progressFlow {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
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
  box-shadow:
    0 16px 40px rgba(0, 0, 0, 0.18),
    inset 0 1px 0 color-mix(in srgb, var(--color-text-inverse) 8%, transparent);
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
  box-shadow: -20px 0 60px color-mix(in srgb, var(--graph-glow-primary) 16%, transparent);
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
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--color-surface) 92%, transparent), color-mix(in srgb, var(--color-surface-elevated) 88%, transparent));
  border: 1px solid var(--color-outline);
  border-radius: 12px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 16px 40px color-mix(in srgb, var(--graph-glow-primary) 10%, transparent);
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
  background: color-mix(in srgb, var(--color-surface) 90%, transparent);
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
  background: color-mix(in srgb, var(--color-surface) 90%, transparent);
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
  border-radius: 28px;
}

:deep(.n-scrollbar-rail) {
  background: transparent !important;
}

:deep(.n-progress) {
  --n-fill-color: var(--color-primary);
  --n-rail-color: var(--color-surface);
}
</style>
