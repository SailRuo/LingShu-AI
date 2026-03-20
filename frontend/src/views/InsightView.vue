<script setup lang="ts">
import { onMounted, ref, onBeforeUnmount, shallowRef, h, computed } from 'vue'
import { NButton, NIcon, NDropdown, useMessage, NScrollbar } from 'naive-ui'
import { RefreshCcw, Activity, Trash2, Eye, Box, Clock, Layers, Cpu } from 'lucide-vue-next'
// @ts-ignore
import Neovis from 'neovis.js'
import { useThemeStore } from '@/stores/themeStore'

const viz = shallowRef<any>(null)
const isLoaded = ref(false)
const selectedNode = ref<any>(null)
const message = useMessage()
const themeStore = useThemeStore()

function formatTime(ts: number): string {
  if (!ts || isNaN(ts)) return '未知时间'
  const diff = Math.floor((Date.now() - ts) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function toISODateString(val: unknown): string {
  if (!val) return new Date().toISOString()
  if (typeof val === 'string') return val
  if (typeof val === 'number') return new Date(val).toISOString()
  if (typeof val === 'object' && val !== null) {
    const obj = val as Record<string, unknown>
    if (obj.toString && typeof obj.toString === 'function') {
      const str = obj.toString()
      if (str !== '[object Object]') return str
    }
  }
  return new Date().toISOString()
}

const showDropdown = ref(false)
const dropdownX = ref(0)
const dropdownY = ref(0)
const rightClickedNode = ref<any>(null)

const dropdownOptions = [
  {
    label: '查看神经元连接',
    key: 'inspect',
    icon: () => h(NIcon, null, { default: () => h(Eye) })
  },
  {
    label: '移除此记忆',
    key: 'delete',
    icon: () => h(NIcon, { color: '#ef4444' }, { default: () => h(Trash2) })
  }
]

const themeColors = computed(() => ({
  primary: themeStore.current.cssVars['--color-primary'],
  primaryDim: themeStore.current.cssVars['--color-primary-dim'],
  accent: themeStore.current.cssVars['--color-accent'],
  error: themeStore.current.cssVars['--color-error'],
  text: themeStore.current.cssVars['--color-text'],
  textDim: themeStore.current.cssVars['--color-text-dim'],
  outline: themeStore.current.cssVars['--color-outline'],
  nodeUser: themeStore.current.cssVars['--color-node-user'] || themeStore.current.cssVars['--color-primary'],
  nodeFact: themeStore.current.cssVars['--color-node-fact'] || themeStore.current.cssVars['--color-accent'],
  edge: themeStore.current.cssVars['--color-edge'] || themeStore.current.cssVars['--color-primary'],
  glow: themeStore.current.cssVars['--color-glow'] || themeStore.current.cssVars['--color-primary'],
  glassBg: themeStore.current.cssVars['--color-glass-bg'],
  glassBorder: themeStore.current.cssVars['--color-glass-border'],
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
      size: 50,
      color: {
        background: 'rgba(0,0,0,0)',
        border: themeColors.value.nodeUser,
        highlight: { background: 'rgba(0,0,0,0)', border: themeColors.value.nodeUser }
      },
      font: { size: 14, color: themeColors.value.nodeUser, face: 'Fira Code' },
      shape: 'dot',
      borderWidth: 3,
      shadow: { enabled: true, color: themeColors.value.glow, size: 20, x: 0, y: 0 }
    },
    'Fact': {
      caption: 'content',
      size: 'importance',
      color: {
        background: 'rgba(0,0,0,0)',
        border: themeColors.value.nodeFact,
        highlight: { background: 'rgba(0,0,0,0)', border: themeColors.value.nodeUser }
      },
      font: { size: 10, color: themeColors.value.nodeFact, face: 'Fira Code' },
      shape: 'dot',
      borderWidth: 2,
      shadow: { enabled: true, color: themeColors.value.glow, size: 12, x: 0, y: 0 }
    }
  },
  relationships: {
    'HAS_FACT': {
      thickness: 1.5,
      caption: false,
      color: themeColors.value.edge,
      arrows: { to: { enabled: true, scaleFactor: 0.4 } },
      dashes: false
    }
  },
  initialCypher: "MATCH (u:User)-[r:HAS_FACT]->(f:Fact) RETURN u,r,f",
  visConfig: {
    physics: {
      forceAtlas2Based: { 
        gravitationalConstant: -30, 
        centralGravity: 0.01, 
        springLength: 180, 
        springConstant: 0.2,
        avoidOverlap: 1
      },
      solver: 'forceAtlas2Based',
      timestep: 0.35,
      stabilization: { iterations: 200 }
    },
    edges: { 
      smooth: { type: 'curvedCW', roundness: 0.3 },
      shadow: {
        enabled: true,
        color: themeColors.value.glow,
        size: 5,
        x: 0,
        y: 0
      }
    },
    nodes: { 
      borderWidth: 2,
      hover: {
        borderWidth: 4,
        size: 1.2
      }
    }
  }
}))

function initViz() {
  if (viz.value) viz.value.clearNetwork()
  try {
    viz.value = new Neovis(config.value as any)
    
    viz.value.registerOnEvent('completed', () => {
      isLoaded.value = true
      if (viz.value.network) {
        viz.value.network.on('oncontext', (params: any) => {
          params.event.preventDefault()
          const nodeId = viz.value.network.getNodeAt(params.pointer.DOM)
          if (nodeId) {
            const node = viz.value.nodes.get(nodeId)
            if (node && node.raw.labels.includes('Fact')) {
              rightClickedNode.value = { ...node, id: nodeId }
              dropdownX.value = params.event.clientX
              dropdownY.value = params.event.clientY
              showDropdown.value = true
            }
          }
        })
      }
    })
    
    viz.value.registerOnEvent('clickNode', (event: any) => {
      const node = event.node
      selectedNode.value = {
        id: node.id,
        label: node.raw.properties.name || node.raw.properties.content,
        type: node.raw.labels[0],
        importance: node.raw.properties.importance || 0.5,
        observedAt: toISODateString(node.raw.properties.observedAt)
      }
      showDropdown.value = false
    })

    viz.value.render()
  } catch (err) {
    console.error('Neovis init error:', err)
  }
}

async function handleDeleteFact() {
  if (!rightClickedNode.value) return
  try {
    const factId = rightClickedNode.value.id
    await fetch(`/api/memory/fact/${factId}`, { method: 'DELETE' })
    message.success('记忆片段已成功修剪')
    refreshGraph()
  } catch (err) {
    message.error('同步错误：修剪失败')
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
      type: rightClickedNode.value.raw.labels[0],
      importance: rightClickedNode.value.raw.properties.importance || 0.5,
      observedAt: toISODateString(rightClickedNode.value.raw.properties.observedAt)
    }
    showDropdown.value = false
  }
}

function refreshGraph() {
  if (viz.value) viz.value.reload()
  else initViz()
}

onMounted(() => setTimeout(initViz, 500))
onBeforeUnmount(() => { if (viz.value) viz.value.clearNetwork() })
</script>

<template>
  <div class="ls-view insight-view h-full flex flex-col p-6 overflow-hidden gap-4" :class="{ 'theme-zen': !themeColors.isDark, 'theme-dark': themeColors.isDark }">
    
    <header class="h-14 flex items-center justify-between px-5 glass-panel rounded-xl z-30">
      <div class="flex items-center gap-5">
        <div class="w-1 h-7 bg-primary glow-box-primary"></div>
        <div class="flex flex-col">
          <h2 class="text-lg font-black tracking-[0.15em] text-primary uppercase">
            深度洞察 <span class="font-hud text-[10px] ml-3 opacity-40">图谱探索器::拓扑同步</span>
          </h2>
          <div class="flex items-center gap-2 mt-0.5">
             <div class="w-1.5 h-1.5 rounded-full bg-success animate-pulse"></div>
             <span class="text-[8px] font-hud tracking-[0.15em] text-primary/60 uppercase">
               流状态: 实时拓扑扫描器::活跃
             </span>
          </div>
        </div>
      </div>
      
      <div class="flex items-center gap-4">
        <div class="stat-badge flex items-center px-4 py-1 rounded-full">
           <n-icon :size="12" class="text-primary mr-2"><Activity /></n-icon>
           <span class="text-[9px] font-hud text-primary/80 uppercase tracking-wider">片段密度: <span class="text-primary font-black">42</span></span>
        </div>
        <div class="stat-badge flex items-center px-4 py-1 rounded-full">
           <n-icon :size="12" class="text-primary mr-2"><Box /></n-icon>
           <span class="text-[9px] font-hud text-primary/80 uppercase tracking-wider">节点: <span class="text-primary font-black">14,892</span></span>
        </div>
        <div class="stat-badge flex items-center px-4 py-1 rounded-full">
           <n-icon :size="12" class="text-primary mr-2"><Layers /></n-icon>
           <span class="text-[9px] font-hud text-primary/80 uppercase tracking-wider">连线: <span class="text-primary font-black">56,310</span></span>
        </div>
        <div class="flex gap-2">
          <n-button quaternary circle @click="refreshGraph" class="hover:bg-primary/10 transition-colors">
            <template #icon><n-icon :size="18"><RefreshCcw /></n-icon></template>
          </n-button>
          <n-button type="primary" secondary class="hud-btn-main font-hud glow-box-primary" @click="refreshGraph">
             初始化同步
          </n-button>
        </div>
      </div>
    </header>

    <div class="flex-1 flex gap-4 overflow-hidden min-h-0 relative">
      
      <div class="flex-1 glass-panel rounded-2xl border relative overflow-hidden bg-dot-grid group">
        <div id="viz" class="w-full h-full relative z-0 transition-opacity duration-1000" :class="{ 'opacity-20': !isLoaded }"></div>
        
        <div class="absolute top-5 left-6 pointer-events-none z-10 flex flex-col gap-2">
          <div class="flex items-center gap-3">
             <div class="w-2 h-2 rounded-full border-2 border-primary/40 animate-ping"></div>
             <span class="text-[9px] font-hud text-primary tracking-[0.2em] font-black underline decoration-primary/20">实时神经流_0xFA2</span>
          </div>
          <div class="flex flex-col opacity-20">
             <span class="text-[7px] font-hud uppercase">X坐标: 402.1</span>
             <span class="text-[7px] font-hud uppercase">Y坐标: 108.4</span>
          </div>
        </div>

        <div class="absolute bottom-5 left-6 right-6 flex justify-between items-center pointer-events-none z-10">
          <div class="flex items-center gap-4">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-primary animate-pulse"></div>
              <span class="text-[8px] font-hud text-primary/80 uppercase tracking-wider">活跃: 89</span>
            </div>
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full bg-accent animate-pulse"></div>
              <span class="text-[8px] font-hud text-accent/80 uppercase tracking-wider">系统延迟: 42毫秒</span>
            </div>
          </div>
          <div class="flex items-center gap-4">
            <div class="text-[8px] font-hud text-primary/60 uppercase tracking-wider">缩放: 100%</div>
            <div class="text-[8px] font-hud text-primary/60 uppercase tracking-wider">平移: 居中</div>
          </div>
        </div>

        <div v-if="!isLoaded" class="absolute inset-0 z-20 flex flex-col items-center justify-center loading-overlay">
          <div class="relative w-20 h-20 flex items-center justify-center">
             <n-icon :size="56" class="text-primary opacity-20 animate-spin-slow"><RefreshCcw /></n-icon>
             <div class="absolute inset-0 border-2 border-primary/10 rounded-full animate-ping"></div>
          </div>
          <p class="mt-6 font-hud text-[10px] tracking-[0.3em] text-primary animate-pulse">正在同步 Neo4j 拓扑结构...</p>
        </div>
        
        <n-dropdown
          placement="bottom-start" trigger="manual"
          :x="dropdownX" :y="dropdownY" :options="dropdownOptions"
          :show="showDropdown" :on-clickoutside="() => (showDropdown = false)"
          @select="handleSelectDropdown"
        />
      </div>

      <aside class="w-80 glass-panel rounded-2xl border flex flex-col overflow-hidden shadow-2xl">
        <div class="h-16 flex items-center px-7 border-b panel-header">
          <span class="font-hud text-[13px] tracking-[0.2em] text-primary font-black uppercase">神经元检测器</span>
          <div class="ml-auto w-3 h-3 rounded-full bg-primary/30 border border-primary/50"></div>
        </div>
        
        <n-scrollbar class="flex-1">
          <div class="p-7 space-y-8">
            <template v-if="selectedNode">
              <div class="hud-box glass-panel">
                <div class="hud-label-terminal font-hud">神经元ID: {{ selectedNode.id }}</div>
                <div class="mt-7 flex flex-wrap gap-3">
                  <div class="hud-chip uppercase font-black" :class="selectedNode.type">{{ selectedNode.type }}</div>
                  <div class="hud-chip opacity-60">同步稳定</div>
                  <div class="hud-chip opacity-60">内存L2</div>
                </div>
              </div>

              <div class="space-y-4">
                <div class="flex items-center gap-3 px-1">
                   <n-icon :size="18" class="text-primary glow-text-primary"><Cpu /></n-icon>
                   <span class="text-[13px] font-hud text-primary tracking-wider font-bold">解码内容</span>
                </div>
                <div class="payload-box p-6 rounded-lg relative overflow-hidden group">
                  <div class="absolute top-0 right-0 p-3 opacity-5 transition-opacity group-hover:opacity-20 flex flex-col items-end">
                     <Layers :size="36" />
                     <span class="text-[12px] font-hud">块_01</span>
                  </div>
                  <pre class="font-hud text-[13px] leading-7 text-primary/90 whitespace-pre-wrap selection:bg-primary/30">{{ selectedNode.label }}</pre>
                </div>
              </div>

              <div class="space-y-5">
                <div class="flex justify-between items-end px-1">
                  <span class="text-[13px] font-hud text-text-dim uppercase tracking-wider">记忆权重</span>
                  <span class="text-[16px] font-hud text-primary font-black underline decoration-primary/40 decoration-wavy">{{ (selectedNode.importance * 100).toFixed(1) }}%</span>
                </div>
                <div class="relative h-8 flex items-center border p-1.5 weight-bar rounded">
                   <div class="absolute inset-x-0 h-px top-0 bg-primary/20"></div>
                   <div class="h-full bg-primary/40 relative shadow-glow rounded-sm" :style="{ width: `${selectedNode.importance * 100}%` }">
                      <div class="absolute inset-0 animate-shimmer"></div>
                   </div>
                   <div class="absolute inset-x-0 bottom-0 flex justify-between px-2 pt-1 opacity-10">
                      <div v-for="i in 20" :key="i" class="w-[1px] h-2 bg-white"></div>
                   </div>
                </div>
              </div>

              <div class="flex flex-col gap-3 py-4 border-t items-end text-right">
                 <div class="flex items-center gap-3">
                   <span class="text-[14px] font-black text-primary tracking-[0.15em]">{{ formatTime(new Date(selectedNode.observedAt).getTime()) }}</span>
                   <n-icon size="16" class="text-primary"><Clock /></n-icon>
                 </div>
                 <div class="font-hud text-[12px] opacity-30 tracking-tight uppercase">
                   追踪UUID: {{ String(selectedNode.observedAt).split('T')[0] }}::系统同步
                 </div>
              </div>
            </template>
            
            <div v-else class="flex flex-col items-center justify-center py-24 gap-5 opacity-30">
               <n-icon :size="52" class="animate-pulse"><Box /></n-icon>
               <span class="font-hud text-[13px] tracking-[0.3em] uppercase text-center">待机: 选择神经元</span>
            </div>

            <div class="mt-auto pt-8 border-t space-y-5">
               <div class="text-[13px] font-hud text-text-dim uppercase tracking-[0.2em]">突触稳定性</div>
               <div class="border p-6 font-hud text-[13px] tracking-tight space-y-4 relative overflow-hidden stats-box rounded-lg">
                  <div class="absolute -top-12 -right-12 w-28 h-28 border border-primary/5 rounded-full"></div>
                  <div class="flex justify-between items-center">
                    <span class="opacity-50 uppercase">全局同步</span>
                    <span class="text-primary font-black text-[15px]">0.9942σ</span>
                  </div>
                  <div class="flex justify-between items-center">
                    <span class="opacity-50 uppercase">片段衰减</span>
                    <span class="text-warning font-black text-[15px]">0.002%</span>
                  </div>
                  <div class="flex justify-between items-center text-primary/50 text-[12px] pt-3 border-t border-white/5 mt-4">
                    <span>X同步::真</span>
                    <span>缓冲就绪::100%</span>
                  </div>
               </div>
            </div>
          </div>
        </n-scrollbar>
      </aside>
    </div>

    <div class="h-20 glass-panel rounded-xl border flex items-center justify-between px-5 gap-5">
      <div class="flex-1 border-r pr-5 divider-line">
        <div class="text-[8px] font-hud text-text-dim uppercase tracking-[0.2em] mb-1">大模型令牌使用</div>
        <div class="flex items-end justify-between">
          <div class="text-xl font-black text-primary">5.1k</div>
          <div class="text-[7px] font-hud text-primary/60 uppercase tracking-wider">平均: 4.8k | 最大: 7.2k</div>
        </div>
        <div class="mt-1.5 h-1.5 rounded-full overflow-hidden progress-bar">
          <div class="h-full bg-primary/60 rounded-full" style="width: 65%"></div>
        </div>
      </div>
      <div class="flex-1 border-r pr-5 divider-line">
        <div class="text-[8px] font-hud text-text-dim uppercase tracking-[0.2em] mb-1">系统负载</div>
        <div class="flex items-end justify-between">
          <div class="text-xl font-black text-primary">64%</div>
          <div class="text-[7px] font-hud text-primary/60 uppercase tracking-wider">CPU: 42% | GPU: 78%</div>
        </div>
        <div class="mt-1.5 h-1.5 rounded-full overflow-hidden progress-bar">
          <div class="h-full bg-primary/60 rounded-full" style="width: 64%"></div>
        </div>
      </div>
      <div class="flex-1">
        <div class="text-[8px] font-hud text-text-dim uppercase tracking-[0.2em] mb-1">内存使用</div>
        <div class="flex items-end justify-between">
          <div class="text-xl font-black text-primary">18.2GB</div>
          <div class="text-[7px] font-hud text-primary/60 uppercase tracking-wider">总计: 32GB</div>
        </div>
        <div class="mt-1.5 h-1.5 rounded-full overflow-hidden progress-bar">
          <div class="h-full bg-primary/60 rounded-full" style="width: 57%"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.insight-view {
  background-color: transparent;
  color: var(--color-text);
}

.glass-panel {
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border);
}

.theme-dark .glass-panel {
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.theme-zen .glass-panel {
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.stat-badge {
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
}

.theme-dark .stat-badge {
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.05);
}

.theme-zen .stat-badge {
  background: rgba(255, 255, 255, 0.5);
  border-color: rgba(0, 0, 0, 0.03);
}

.panel-header {
  background: var(--color-surface);
  border-color: var(--color-outline);
}

.theme-dark .panel-header {
  background: rgba(0, 0, 0, 0.3);
}

.theme-zen .panel-header {
  background: rgba(255, 255, 255, 0.4);
}

.loading-overlay {
  background: var(--color-glass-bg);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
}

.bg-dot-grid {
  background-image: radial-gradient(var(--color-outline) 1px, transparent 1px);
  background-size: 24px 24px;
}

.theme-zen .bg-dot-grid {
  background-image: radial-gradient(rgba(0, 0, 0, 0.03) 1px, transparent 1px);
}

.shadow-glow { 
  box-shadow: 0 0 15px var(--color-glow); 
}

.theme-zen .shadow-glow {
  box-shadow: 0 0 8px var(--color-glow);
}

.hud-btn-main {
  font-family: 'Fira Code', monospace;
  font-size: 10px;
  letter-spacing: 0.08em;
  padding: 0 16px;
}

.hud-box {
  border: 1px solid var(--color-outline);
  background: var(--color-surface);
  padding: 22px;
  position: relative;
}

.hud-label-terminal {
  background: var(--color-background);
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  padding: 3px 10px;
  font-family: 'Fira Code', monospace;
  font-size: 12px;
  position: absolute;
  top: -12px;
  left: 12px;
}

.theme-zen .hud-label-terminal {
  background: #fff;
}

.hud-chip {
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
  padding: 4px 10px;
  font-family: 'Fira Code', monospace;
  font-size: 12px;
  color: var(--color-text-dim);
}

.hud-chip.User { color: var(--color-node-user); border-color: var(--color-node-user); }
.hud-chip.Fact { color: var(--color-node-fact); border-color: var(--color-node-fact); }

.payload-box {
  background: var(--color-surface);
  border: 1px solid var(--color-outline);
}

.theme-dark .payload-box {
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.05);
}

.theme-zen .payload-box {
  background: rgba(255, 255, 255, 0.6);
  border-color: rgba(0, 0, 0, 0.03);
}

.weight-bar {
  background: var(--color-surface);
  border-color: var(--color-outline);
}

.theme-dark .weight-bar {
  background: rgba(0, 0, 0, 0.2);
  border-color: rgba(255, 255, 255, 0.1);
}

.theme-zen .weight-bar {
  background: rgba(255, 255, 255, 0.4);
  border-color: rgba(0, 0, 0, 0.05);
}

.stats-box {
  background: var(--color-surface);
  border-color: var(--color-outline);
}

.theme-dark .stats-box {
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.05);
}

.theme-zen .stats-box {
  background: rgba(255, 255, 255, 0.5);
  border-color: rgba(0, 0, 0, 0.03);
}

.divider-line {
  border-color: var(--color-outline);
}

.progress-bar {
  background: var(--color-surface);
}

.theme-dark .progress-bar {
  background: rgba(0, 0, 0, 0.4);
}

.theme-zen .progress-bar {
  background: rgba(0, 0, 0, 0.05);
}

@keyframes shimmer { 0% { transform: translateX(-100%); } 100% { transform: translateX(100%); } }
.animate-shimmer { animation: shimmer 2s infinite linear; }
.animate-spin-slow { animation: spin 8s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

:deep(.viz-main) { width: 100%; height: 100%; }
:deep(.vis-network) { outline: none; background: transparent !important; }

:deep(.n-scrollbar-rail) { background-color: transparent !important; }
:deep(.n-scrollbar-rail--vertical) { width: 4px !important; }
</style>
