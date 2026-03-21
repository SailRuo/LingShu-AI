<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { 
  NIcon, NScrollbar, 
  useMessage, NProgress
} from 'naive-ui'
import { 
  Activity, Zap, 
  Hexagon, Share2, Cpu as CpuIcon,
  Box, Scissors, Filter, Combine, 
  RefreshCw, User, Maximize, Bell, Settings, Shield, FileText
} from 'lucide-vue-next'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
const message = useMessage()

const selectNode = (node: any) => {
  selectedNode.value = node
  message.info(`已选中节点: ${node.label}`)
}

// --- Mock Data ---
const vramUsage = ref(8.4)
const totalVram = 16
const vramPercent = computed(() => (vramUsage.value / totalVram) * 100)
const systemTime = ref(new Date())

const logs = ref([
  { time: '12:44:02.102', content: '正在获取节点 0x82f4 的关联边...', type: 'info' },
  { time: '12:44:02.114', content: '内存缓存中发现 12 个匹配项。', type: 'info' },
  { time: '12:44:02.115', content: '正在解析 Temporal_Buffer 关联。', type: 'info' },
  { time: '12:44:02.128', content: '在 0x22a1 处检测 to 冲突。', type: 'error' },
])

const nodes = ref([
  { id: '0x82F4', label: 'Root_Nexus_Core', type: 'core', x: 400, y: 350, size: 40 },
  { id: '0x12A3', label: 'Synapse_A', type: 'fact', x: 400, y: 200, size: 12 },
  { id: '0x34B2', label: 'Synapse_B', type: 'fact', x: 280, y: 300, size: 12 },
  { id: '0x56C1', label: 'Synapse_C', type: 'fact', x: 520, y: 300, size: 12 },
])

const selectedNode = ref(nodes.value[0])

// --- Simulation ---
let timer: any
onMounted(() => {
  timer = setInterval(() => {
    systemTime.value = new Date()
  }, 1000)
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<template>
  <div class="ls-console h-screen w-screen flex flex-col overflow-hidden select-none bg-[#0a0510] text-gray-300"
       :class="[themeStore.current.key]">
    
    <!-- GRID BACKGROUND -->
    <div class="fixed inset-0 pointer-events-none z-0 bg-dot-grid opacity-20"></div>

    <!-- TOP NAV -->
    <header class="h-16 flex items-center justify-between px-6 border-b border-white/5 bg-black/40 backdrop-blur-md z-50">
      <div class="flex items-center gap-12">
        <!-- Logo -->
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 bg-primary/20 flex items-center justify-center rounded-lg border border-primary/50 shadow-[0_0_15px_rgba(168,85,247,0.3)]">
            <n-icon :size="20" class="text-primary"><Hexagon /></n-icon>
          </div>
          <div class="flex flex-col">
            <span class="text-lg font-black tracking-widest text-white">灵枢</span>
            <span class="text-[8px] font-mono text-primary/60 tracking-[0.3em] -mt-1">LINGSHU.AI</span>
          </div>
        </div>

        <!-- Center Tabs -->
        <nav class="flex items-center gap-8 ml-8">
          <div class="nav-tab active">
            <span>记忆图谱</span>
            <div class="tab-indicator"></div>
          </div>
          <div class="nav-tab">
            <span>基础设施</span>
          </div>
        </nav>
      </div>

      <div class="flex items-center gap-6">
        <!-- Search -->
        <div class="relative flex items-center">
            <input 
              type="text" 
              placeholder="搜索..." 
              class="bg-white/5 border border-white/10 rounded-full h-9 w-64 pl-4 pr-10 text-xs focus:outline-none focus:border-primary/50 transition-all"
            />
            <div class="absolute right-3 flex items-center gap-1 opacity-40">
                <span class="border border-white/20 rounded px-1 text-[8px]">⌘</span>
                <span class="border border-white/20 rounded px-1 text-[8px]">K</span>
            </div>
        </div>

        <div class="flex items-center gap-4 border-l border-white/10 pl-6">
            <n-icon :size="20" class="cursor-pointer hover:text-primary transition-colors"><Bell /></n-icon>
            <div class="flex items-center gap-3 cursor-pointer group">
                <div class="flex flex-col items-end">
                    <span class="text-xs font-bold text-white group-hover:text-primary transition-colors">Dr. Neural</span>
                    <span class="text-[9px] opacity-40">管理员</span>
                </div>
                <div class="w-9 h-9 rounded-full bg-gradient-to-br from-primary to-purple-800 border-2 border-white/10 flex items-center justify-center overflow-hidden">
                    <n-icon :size="20" class="text-white"><User /></n-icon>
                </div>
            </div>
        </div>
      </div>
    </header>

    <div class="flex-1 flex overflow-hidden relative z-10">
      
      <!-- LEFT SIDEBAR -->
      <aside class="w-64 bg-black/30 border-r border-white/5 flex flex-col py-6 px-4 gap-8">
        <!-- Core Capabilities -->
        <div class="space-y-4">
            <div class="text-[10px] font-bold text-white/40 tracking-widest px-2 uppercase">核心能力</div>
            <nav class="space-y-1">
                <div class="sider-link">
                    <n-icon><Activity /></n-icon>
                    <span>意念共鸣</span>
                </div>
                <div class="sider-link active">
                    <n-icon><Zap /></n-icon>
                    <span>记忆图谱</span>
                </div>
                <div class="sider-link">
                    <n-icon><Share2 /></n-icon>
                    <span>关联矩阵</span>
                </div>
                <div class="sider-link">
                    <n-icon><Box /></n-icon>
                    <span>全维口袋</span>
                </div>
            </nav>
        </div>

        <!-- Infrastructure -->
        <div class="space-y-4">
            <div class="text-[10px] font-bold text-white/40 tracking-widest px-2 uppercase">基础设施</div>
            <nav class="space-y-1">
                <div class="sider-link">
                    <n-icon><Settings /></n-icon>
                    <span>系统设置</span>
                </div>
                <div class="sider-link">
                    <n-icon><Shield /></n-icon>
                    <span>安全保障</span>
                </div>
                <div class="sider-link">
                    <n-icon><FileText /></n-icon>
                    <span>系统日志</span>
                </div>
            </nav>
        </div>

        <!-- Pruning Tools -->
        <div class="mt-auto space-y-4">
            <div class="flex items-center gap-2 px-2">
                <div class="w-1.5 h-1.5 rounded-full bg-primary animate-pulse"></div>
                <div class="text-[10px] font-bold text-white/40 tracking-widest uppercase">修剪工具</div>
            </div>
            <div class="grid grid-cols-2 gap-2">
                <div class="tool-card group">
                    <n-icon :size="18"><Combine /></n-icon>
                    <span>合并</span>
                </div>
                <div class="tool-card group">
                    <n-icon :size="18" class="text-primary"><Scissors /></n-icon>
                    <span class="text-primary">抹除</span>
                </div>
                <div class="tool-card group">
                    <n-icon :size="18"><Filter /></n-icon>
                    <span>过滤</span>
                </div>
                <div class="tool-card group">
                    <n-icon :size="18" class="text-primary"><Scissors /></n-icon>
                    <span class="text-primary">修剪</span>
                </div>
            </div>
        </div>
      </aside>

      <!-- MAIN AREA -->
      <main class="flex-1 flex flex-col relative bg-[#0a0510]">
        <!-- Top Stats -->
        <div class="absolute top-6 left-8 right-8 flex justify-between items-start pointer-events-none z-20">
            <div class="flex items-center gap-4 bg-black/60 backdrop-blur border border-white/5 py-1 px-3 rounded text-[10px] pointer-events-auto">
                <span class="opacity-40 font-mono italic">STREAM_STATUS:</span>
                <span class="text-primary font-bold font-mono tracking-tighter">REAL_TIME_TOPOLOGY_SCANNER::ACTIVE</span>
            </div>
            <div class="flex items-center gap-6 pointer-events-auto">
                <div class="flex items-center gap-3 text-[10px] font-mono">
                    <span class="opacity-40">FRAG DENSITY:</span>
                    <span class="text-white">42</span>
                </div>
                <n-icon :size="16" class="opacity-40 cursor-pointer hover:text-white transition-colors"><RefreshCw /></n-icon>
                <button class="bg-primary/20 hover:bg-primary/30 border border-primary text-primary px-4 py-1.5 text-[10px] font-black tracking-widest transition-all rounded shadow-[0_0_15px_rgba(168,85,247,0.2)] uppercase">
                    Initialize_Sync
                </button>
            </div>
        </div>

        <!-- Topology Canvas -->
        <div class="flex-1 relative overflow-hidden">
            <svg class="w-full h-full absolute inset-0">
                <defs>
                    <radialGradient id="node-glow" cx="50%" cy="50%" r="50%">
                        <stop offset="0%" stop-color="rgba(168,85,247,0.3)" />
                        <stop offset="100%" stop-color="transparent" />
                    </radialGradient>
                </defs>

                <!-- Mock Edges -->
                <line v-for="node in nodes.slice(1)" :key="node.id"
                      :x1="nodes[0].x" :y1="nodes[0].y"
                      :x2="node.x" :y2="node.y"
                      stroke="rgba(168,85,247,0.1)" stroke-width="1" stroke-dasharray="4 4" />

                <!-- Nodes -->
                <g v-for="node in nodes" :key="node.id" class="cursor-pointer group" @click="selectNode(node)">
                    <circle :cx="node.x" :cy="node.y" :r="node.size + 10" fill="url(#node-glow)" 
                            class="opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                    <circle :cx="node.x" :cy="node.y" :r="node.size" 
                            class="fill-black/40 stroke-primary/30 group-hover:stroke-primary transition-all duration-300" 
                            :stroke-width="node.type === 'core' ? 2 : 1" />
                    <circle v-if="node.type === 'core'" :cx="node.x" :cy="node.y" :r="node.size * 0.4" 
                            class="fill-primary/20 stroke-primary animate-pulse" stroke-width="1" />
                    
                    <text :x="node.x" :y="node.y + node.size + 20" text-anchor="middle" 
                          class="text-[9px] fill-white opacity-40 font-mono uppercase tracking-widest group-hover:opacity-100 transition-opacity">
                        {{ node.label }}
                    </text>
                    <text :x="node.x" :y="node.y + node.size + 30" text-anchor="middle" 
                          class="text-[7px] fill-primary/60 font-mono uppercase tracking-[0.2em] group-hover:opacity-100 transition-opacity">
                        {{ node.id }}
                    </text>
                </g>
            </svg>
        </div>

        <!-- Bottom Controls -->
        <div class="absolute bottom-8 left-1/2 -translate-x-1/2 flex gap-4 z-20">
            <button class="bg-black/60 backdrop-blur border border-white/10 text-white/60 hover:text-white px-6 py-3 rounded-lg flex items-center gap-3 transition-all">
                <n-icon :size="18"><Maximize /></n-icon>
                <span class="text-xs font-bold uppercase tracking-widest">垂直视角</span>
            </button>
            <button class="bg-black/60 backdrop-blur border border-white/10 text-white/60 hover:text-white px-6 py-3 rounded-lg flex items-center gap-3 transition-all">
                <n-icon :size="18"><RefreshCw /></n-icon>
                <span class="text-xs font-bold uppercase tracking-widest">同步图谱</span>
            </button>
        </div>
      </main>

      <!-- RIGHT INSPECTOR -->
      <aside class="w-96 bg-black/40 border-l border-white/10 flex flex-col p-8 gap-10">
        <!-- Panel Header -->
        <div class="flex justify-between items-center">
            <div class="flex items-center gap-3">
                <div class="w-2 h-2 rounded-full bg-primary shadow-[0_0_8px_rgba(168,85,247,0.6)]"></div>
                <span class="font-mono text-[11px] font-bold tracking-[0.2em] text-white">NEURON_INSPECTOR</span>
            </div>
            <span class="font-mono text-[10px] opacity-20 tracking-widest">V.4.2.0</span>
        </div>

        <!-- Node Status Card -->
        <div class="bg-gradient-to-br from-white/[0.03] to-transparent border border-white/5 p-6 rounded-xl relative overflow-hidden group">
            <div class="absolute top-4 right-4 text-primary opacity-20 group-hover:opacity-40 transition-opacity">
                <n-icon :size="32"><CpuIcon /></n-icon>
            </div>

            <div class="space-y-1 mb-4">
                <div class="text-[8px] font-mono text-primary uppercase tracking-[0.2em]">节点ID: {{ selectedNode.id }}</div>
                <div class="text-2xl font-black text-white italic tracking-tight">{{ selectedNode.label }}</div>
            </div>

            <div class="pt-6 border-t border-white/5 space-y-4">
                <div class="flex justify-between items-center">
                    <span class="text-[9px] font-mono opacity-40 uppercase tracking-widest">Synaptic_Stability</span>
                    <div class="w-32 h-1 bg-white/5 rounded-full overflow-hidden">
                        <div class="h-full bg-primary w-[92%]"></div>
                    </div>
                </div>
                
                <div class="grid grid-cols-1 gap-3">
                    <div class="flex justify-between items-center text-xs font-mono">
                        <span class="opacity-40 text-[9px]">GLOBAL_SYNC</span>
                        <span class="text-white text-[10px]">0.9942σ</span>
                    </div>
                    <div class="flex justify-between items-center text-xs font-mono">
                        <span class="opacity-40 text-[9px]">FRAG_DECAY</span>
                        <span class="text-white text-[10px]">0.002%</span>
                    </div>
                    <div class="flex justify-between items-center text-xs font-mono">
                        <span class="opacity-40 text-[9px]">X_SYNC::TRUE</span>
                        <span class="text-primary text-[9px] font-bold">BUF_READY:100%</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Activity Feed -->
        <div class="flex-1 flex flex-col gap-4 overflow-hidden">
            <div class="flex items-center gap-3">
                <n-icon :size="16" class="opacity-40"><Share2 /></n-icon>
                <span class="text-[10px] font-bold text-white/40 tracking-[0.2em] uppercase">原始数据源</span>
            </div>
            <n-scrollbar class="flex-1 pr-4">
                <div class="space-y-3">
                    <div v-for="(log, i) in logs" :key="i" 
                         class="bg-black/20 border-l-2 border-primary/20 p-3 rounded-r flex flex-col gap-1 group hover:bg-primary/5 transition-colors"
                         :class="{ '!border-red-500/50 !bg-red-500/5': log.type === 'error' }">
                        <div class="flex justify-between items-center">
                            <span class="text-[9px] font-mono opacity-40">[{{ log.time }}]</span>
                            <div class="w-1 h-1 rounded-full bg-primary/40"></div>
                        </div>
                        <div class="text-[10px] leading-relaxed" 
                             :class="log.type === 'error' ? 'text-red-400' : 'text-white/70'">
                            {{ log.content }}
                        </div>
                    </div>
                </div>
            </n-scrollbar>
        </div>

        <!-- Progress Footer -->
        <div class="pt-6 border-t border-white/5 space-y-3">
            <div class="flex justify-between items-end">
                <span class="text-[9px] font-mono opacity-40 uppercase tracking-widest">显存占用</span>
                <span class="text-[10px] font-mono text-white">{{ vramUsage }} GB / {{ totalVram }} GB</span>
            </div>
            <n-progress type="line" :percentage="vramPercent" :show-indicator="false" 
                        color="#a855f7" rail-color="rgba(255,255,255,0.05)" :height="6" />
        </div>
      </aside>
    </div>

    <!-- STATUS FOOTER -->
    <footer class="h-10 bg-black/60 border-t border-white/5 flex items-center justify-between px-6 px-10 relative z-50">
      <div class="flex items-center gap-8">
        <div class="flex items-center gap-3 group">
            <div class="w-1.5 h-1.5 rounded-full bg-[#10b981] shadow-[0_0_8px_#10b981]"></div>
            <span class="text-[9px] font-mono opacity-60">系统运行中</span>
        </div>
        <div class="flex items-center gap-6 border-l border-white/10 pl-8">
            <div class="flex items-center gap-2">
                <span class="text-[9px] font-mono opacity-40 uppercase">Ollama</span>
                <span class="text-[9px] font-mono text-green-500">在线</span>
            </div>
            <div class="flex items-center gap-2">
                <span class="text-[9px] font-mono opacity-40 uppercase">Neo4j</span>
                <span class="text-[9px] font-mono text-green-500">在线</span>
            </div>
        </div>
        <div class="flex items-center gap-2 border-l border-white/10 pl-8">
            <span class="text-[9px] font-mono opacity-40 uppercase tracking-tighter">VRAM</span>
            <span class="text-[9px] font-mono font-bold">{{ vramUsage }} / {{ totalVram }} GB</span>
        </div>
        <div class="flex items-center gap-2 border-l border-white/10 pl-8">
            <span class="text-[9px] font-mono opacity-40 uppercase tracking-tighter">延迟</span>
            <span class="text-[9px] font-mono font-bold">1.2ms</span>
        </div>
      </div>

      <div class="flex items-center gap-10">
        <div class="flex items-center gap-3">
            <n-icon :size="12" class="text-primary animate-pulse"><Zap /></n-icon>
            <span class="text-[9px] font-mono opacity-40 uppercase">神经同步</span>
            <span class="text-[9px] font-mono text-primary font-bold">0.994σ</span>
        </div>
        <div class="flex items-center gap-6 opacity-30 text-[8px] font-mono">
            <span>V1.0.0</span>
            <span>隐私条款</span>
            <span>NEXUS-PROTOCOL</span>
        </div>
      </div>
    </footer>

  </div>
</template>

<style scoped>
.text-primary { color: #a855f7; }
.bg-primary { background-color: #a855f7; }
.border-primary { border-color: #a855f7; }

/* Background */
.bg-dot-grid {
  background-image: radial-gradient(rgba(168, 85, 247, 0.2) 1px, transparent 1px);
  background-size: 40px 40px;
}

/* Sidebar Links */
.sider-link {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 8px;
    color: rgba(255, 255, 255, 0.4);
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    font-size: 13px;
}
.sider-link:hover {
    color: rgba(255, 255, 255, 0.9);
    background: rgba(255, 255, 255, 0.05);
}
.sider-link.active {
    color: #a855f7;
    background: rgba(168, 85, 247, 0.1);
    box-shadow: inset 0 0 10px rgba(168, 85, 247, 0.1);
    font-weight: bold;
}

/* Tool Cards */
.tool-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid rgba(255, 255, 255, 0.05);
    padding: 12px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.3s;
}
.tool-card:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(168, 85, 247, 0.3);
    transform: translateY(-2px);
}
.tool-card span {
    font-size: 10px;
    font-weight: bold;
    opacity: 0.6;
}

/* Nav Tabs */
.nav-tab {
    position: relative;
    padding: 0 4px;
    cursor: pointer;
    transition: all 0.3s;
}
.nav-tab span {
    font-size: 14px;
    font-weight: 500;
    color: rgba(255, 255, 255, 0.4);
}
.nav-tab.active span {
    color: #fff;
    text-shadow: 0 0 10px rgba(168, 85, 247, 0.5);
}
.tab-indicator {
    position: absolute;
    bottom: -20px;
    left: 0;
    width: 60%;
    height: 3px;
    background: #a855f7;
    box-shadow: 0 0 15px #a855f7;
    filter: blur(1px);
}

/* Animations */
@keyframes pulse {
    0%, 100% { opacity: 0.5; transform: scale(0.95); }
    50% { opacity: 1; transform: scale(1.05); }
}
.animate-pulse {
    animation: pulse 2s infinite cubic-bezier(0.4, 0, 0.6, 1);
}
</style>
