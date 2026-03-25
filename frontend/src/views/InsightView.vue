<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NDropdown, NIcon, NInput, NProgress, NScrollbar, NSlider, NTag, useMessage } from 'naive-ui'
import { Clock3, Eye, Play, RefreshCcw, Search, Sparkles, Target, Trash2, ZoomIn, ZoomOut } from 'lucide-vue-next'
import { useThemeStore } from '@/stores/themeStore'

type NodeType = 'User' | 'Topic' | 'Fact'
type ActivityFilter = 'all' | 'active' | 'stable' | 'cool'
type TimeFilter = 'all' | '24h' | '7d' | '30d'
type TypeFilter = 'all' | NodeType
type NodeStatus = 'core' | 'active' | 'stable' | 'cool'

interface GraphNode { id: string; label: string; shortLabel?: string; type: NodeType; subType?: string; importance?: number; confidence?: number; activityScore?: number; cluster?: string; orbitLevel?: number; createdAt?: string | null; lastActivatedAt?: string | null; status?: NodeStatus | string; factCount?: number; version?: number; supersedesFactId?: number | null; contradictsFactId?: number | null }
interface GraphLink { source: string; target: string; type: string; weight?: number }
interface GraphPayload { nodes: GraphNode[]; links: GraphLink[]; stats: { density: number; nodes: number; edges: number; topics: number; activeFacts: number; latency: number } }
interface PositionedNode extends GraphNode { x: number; y: number; size: number; color: string; showLabel: boolean; faded: boolean; hiddenByReplay: boolean; isRecent: boolean; isHit: boolean; isFocus: boolean; isBorn: boolean }
interface RenderedLink { source: PositionedNode; target: PositionedNode; type: string; width: number; faded: boolean; energized: boolean }

const message = useMessage()
const themeStore = useThemeStore()
const graph = ref<GraphPayload>({ nodes: [], links: [], stats: { density: 0, nodes: 0, edges: 0, topics: 0, activeFacts: 0, latency: 0 } })
const selectedNode = ref<GraphNode | null>(null)
const rightClickedNode = ref<GraphNode | null>(null)
const isLoading = ref(false)
const isLoaded = ref(false)
const searchQuery = ref('')
const activeCluster = ref<string | null>(null)
const activityFilter = ref<ActivityFilter>('all')
const timeFilter = ref<TimeFilter>('all')
const typeFilter = ref<TypeFilter>('all')
const replayEnabled = ref(false)
const replayProgress = ref(100)
const replayPlaying = ref(false)
const scale = ref(1)
const viewport = ref({ width: 1200, height: 760 })
const stageRef = ref<HTMLElement | null>(null)
const resizeObserver = ref<ResizeObserver | null>(null)
const replayTimer = ref<number | null>(null)
const birthFlashIds = ref<Set<string>>(new Set())
const birthFlashTimer = ref<number | null>(null)
const showDropdown = ref(false)
const dropdownX = ref(0)
const dropdownY = ref(0)

const dropdownOptions = [
  { label: '查看关联详情', key: 'inspect', icon: () => h(NIcon, null, { default: () => h(Eye) }) },
  { label: '移除此节点', key: 'delete', icon: () => h(NIcon, { color: 'var(--color-error)' }, { default: () => h(Trash2) }) },
]

const themeColors = computed(() => ({
  primary: themeStore.current.cssVars['--color-primary'],
  accent: themeStore.current.cssVars['--color-accent'],
  nodeUser: themeStore.current.cssVars['--color-node-user'],
  nodeFact: themeStore.current.cssVars['--color-node-fact'],
  edge: themeStore.current.cssVars['--color-edge'],
  glow: themeStore.current.cssVars['--color-glow'],
  outline: themeStore.current.cssVars['--color-outline'],
}))

const sceneStyle = computed(() => ({ '--graph-primary': themeColors.value.primary, '--graph-accent': themeColors.value.accent, '--graph-core': themeColors.value.nodeUser, '--graph-fact': themeColors.value.nodeFact, '--graph-edge': themeColors.value.edge, '--graph-glow': themeColors.value.glow, '--graph-outline': themeColors.value.outline }))
const topicClusters = computed(() => graph.value.nodes.filter((n) => n.type === 'Topic').map((n) => ({ key: n.cluster || n.id, label: n.label, count: n.factCount || 0 })))
const timelineNodes = computed(() => graph.value.nodes.filter((n) => n.type !== 'User' && n.lastActivatedAt).sort((a, b) => new Date(a.lastActivatedAt || 0).getTime() - new Date(b.lastActivatedAt || 0).getTime()))
const timelineBounds = computed(() => {
  const first = timelineNodes.value[0]?.lastActivatedAt ? new Date(timelineNodes.value[0].lastActivatedAt!).getTime() : Date.now()
  const lastNode = timelineNodes.value[timelineNodes.value.length - 1]
  const last = lastNode?.lastActivatedAt ? new Date(lastNode.lastActivatedAt).getTime() : first
  return { first, last: Math.max(first, last) }
})
const replayCutoff = computed(() => {
  if (!replayEnabled.value) return timelineBounds.value.last
  const span = Math.max(1, timelineBounds.value.last - timelineBounds.value.first)
  return timelineBounds.value.first + (span * replayProgress.value) / 100
})

function withinTime(node: GraphNode) {
  if (timeFilter.value === 'all') return true
  const ts = node.lastActivatedAt ? new Date(node.lastActivatedAt).getTime() : 0
  if (!ts) return false
  const age = Date.now() - ts
  if (timeFilter.value === '24h') return age <= 86400000
  if (timeFilter.value === '7d') return age <= 604800000
  return age <= 2592000000
}

const filteredNodes = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return graph.value.nodes.filter((node) => {
    const clusterOk = !activeCluster.value || node.cluster === activeCluster.value || node.type === 'User'
    const keywordOk = !keyword || node.label.toLowerCase().includes(keyword) || (node.shortLabel || '').toLowerCase().includes(keyword) || (node.cluster || '').toLowerCase().includes(keyword)
    const typeOk = typeFilter.value === 'all' || node.type === typeFilter.value
    const score = node.activityScore || 0
    const activityOk = activityFilter.value === 'all' || (activityFilter.value === 'active' && score >= 0.75) || (activityFilter.value === 'stable' && score >= 0.38 && score < 0.75) || (activityFilter.value === 'cool' && score < 0.38)
    return clusterOk && keywordOk && typeOk && activityOk && withinTime(node)
  })
})

const visibleNodeIds = computed(() => new Set(filteredNodes.value.map((n) => n.id)))
const filteredLinks = computed(() => graph.value.links.filter((l) => visibleNodeIds.value.has(l.source) && visibleNodeIds.value.has(l.target)))
const displayStats = computed(() => ({ nodes: positionedNodes.value.length, topics: positionedNodes.value.filter((n) => n.type === 'Topic').length, active: positionedNodes.value.filter((n) => n.type === 'Fact' && (n.activityScore || 0) >= 0.75).length, recent: positionedNodes.value.filter((n) => n.isRecent).length, born: positionedNodes.value.filter((n) => n.isBorn).length }))
const replaySummary = computed(() => new Date(replayEnabled.value ? replayCutoff.value : timelineBounds.value.last).toLocaleString('zh-CN'))
const searchKeyword = computed(() => searchQuery.value.trim().toLowerCase())

const positionedNodes = computed<PositionedNode[]>(() => {
  const width = Math.max(760, viewport.value.width)
  const height = Math.max(560, viewport.value.height)
  const cx = width / 2
  const cy = height / 2 + 42
  const topics = filteredNodes.value.filter((n) => n.type === 'Topic')
  const facts = filteredNodes.value.filter((n) => n.type === 'Fact')
  const users = filteredNodes.value.filter((n) => n.type === 'User')
  const pos = new Map<string, PositionedNode>()
  users.forEach((n) => pos.set(n.id, { ...n, x: cx, y: cy, size: 88, color: themeColors.value.nodeUser, showLabel: true, faded: false, hiddenByReplay: false, isRecent: false, isHit: false, isFocus: selectedNode.value?.id === n.id, isBorn: false }))
  topics.forEach((n, i) => {
    const angle = -Math.PI / 2 + (i / Math.max(1, topics.length)) * Math.PI * 2
    const faded = !!activeCluster.value && n.cluster !== activeCluster.value
    const activatedAt = n.lastActivatedAt ? new Date(n.lastActivatedAt).getTime() : 0
    const hiddenByReplay = replayEnabled.value && activatedAt > replayCutoff.value
    const isRecent = activatedAt >= Date.now() - 86400000
    const isHit = !!searchKeyword.value && (n.label.toLowerCase().includes(searchKeyword.value) || (n.shortLabel || '').toLowerCase().includes(searchKeyword.value))
    const isFocus = selectedNode.value?.id === n.id || (!!selectedNode.value?.cluster && selectedNode.value.cluster === n.cluster)
    pos.set(n.id, { ...n, x: cx + Math.cos(angle) * Math.min(width, height) * 0.22, y: cy + Math.sin(angle) * Math.min(width, height) * 0.15, size: 30 + (n.importance || 0.5) * 26, color: themeColors.value.primary, showLabel: true, faded, hiddenByReplay, isRecent, isHit, isFocus, isBorn: birthFlashIds.value.has(n.id) })
  })
  const groups = new Map<string, GraphNode[]>()
  facts.forEach((f) => { const k = f.cluster || 'memory'; if (!groups.has(k)) groups.set(k, []); groups.get(k)!.push(f) })
  Array.from(groups.entries()).forEach(([cluster, items], gi) => {
    const topic = topics.find((n) => n.cluster === cluster)
    const anchor = topic ? pos.get(topic.id) : undefined
    const base = anchor ? Math.atan2(anchor.y - cy, anchor.x - cx) : -Math.PI / 2 + gi
    items.sort((a, b) => (b.importance || 0) - (a.importance || 0)).forEach((n, i) => {
      const level = n.orbitLevel || 2
      const radius = (level === 1 ? 108 : level === 2 ? 174 : 242) + (i % 5) * 14
      const spread = items.length > 1 ? (i / (items.length - 1) - 0.5) * 1.36 : 0
      const faded = !!activeCluster.value && cluster !== activeCluster.value
      const activatedAt = n.lastActivatedAt ? new Date(n.lastActivatedAt).getTime() : 0
      const hiddenByReplay = replayEnabled.value && activatedAt > replayCutoff.value
      const isRecent = activatedAt >= Date.now() - 86400000
      const isHit = !!searchKeyword.value && (n.label.toLowerCase().includes(searchKeyword.value) || (n.shortLabel || '').toLowerCase().includes(searchKeyword.value))
      const isFocus = selectedNode.value?.id === n.id || (!!selectedNode.value?.cluster && selectedNode.value.cluster === n.cluster)
      pos.set(n.id, { ...n, x: (anchor?.x || cx) + Math.cos(base + spread) * radius, y: (anchor?.y || cy) + Math.sin(base + spread) * radius * 0.66, size: 12 + (n.importance || 0.5) * 17, color: n.status === 'active' ? themeColors.value.nodeFact : themeColors.value.accent, showLabel: scale.value >= 0.94 || (n.importance || 0) >= 0.76 || !faded || isRecent, faded, hiddenByReplay, isRecent, isHit, isFocus, isBorn: birthFlashIds.value.has(n.id) })
    })
  })
  return filteredNodes.value.map((n) => pos.get(n.id)!).filter((n): n is PositionedNode => Boolean(n) && !n.hiddenByReplay)
})

const nodeMap = computed(() => new Map(positionedNodes.value.map((n) => [n.id, n])))
const renderedLinks = computed<RenderedLink[]>(() => filteredLinks.value.map((l) => {
  const source = nodeMap.value.get(l.source)
  const target = nodeMap.value.get(l.target)
  if (!source || !target) return null
  const energized = source.isHit || target.isHit || source.isFocus || target.isFocus
  return { source, target, type: l.type, width: 1 + (l.weight || 0.35) * 2, faded: source.faded || target.faded, energized }
}).filter((l): l is RenderedLink => Boolean(l)))

async function fetchGraph() {
  isLoading.value = true
  showDropdown.value = false
  try {
    const previousIds = new Set(graph.value.nodes.map((n) => n.id))
    const response = await fetch('/api/memory/graph')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload = await response.json() as GraphPayload
    graph.value = payload
    const appearedIds = payload.nodes
      .filter((node) => node.type === 'Fact' && !previousIds.has(node.id))
      .map((node) => node.id)
    if (appearedIds.length) {
      birthFlashIds.value = new Set(appearedIds)
      if (birthFlashTimer.value !== null) {
        window.clearTimeout(birthFlashTimer.value)
      }
      birthFlashTimer.value = window.setTimeout(() => {
        birthFlashIds.value = new Set()
        birthFlashTimer.value = null
      }, 4200)
    }
    isLoaded.value = true
    if (selectedNode.value) selectedNode.value = payload.nodes.find((n) => n.id === selectedNode.value?.id) || null
  } catch (error) {
    console.error(error)
    message.error('记忆图谱同步失败')
  } finally {
    isLoading.value = false
  }
}

async function handleDeleteFact() {
  if (!rightClickedNode.value || rightClickedNode.value.type !== 'Fact') return
  try {
    const response = await fetch(`/api/memory/fact/${rightClickedNode.value.id.replace('fact_', '')}`, { method: 'DELETE' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    message.success('记忆节点已移除')
    if (selectedNode.value?.id === rightClickedNode.value.id) selectedNode.value = null
    await fetchGraph()
  } catch (error) {
    console.error(error)
    message.error('节点移除失败')
  } finally {
    showDropdown.value = false
    rightClickedNode.value = null
  }
}

function handleSelectDropdown(key: string) {
  if (key === 'inspect' && rightClickedNode.value) { selectedNode.value = rightClickedNode.value; showDropdown.value = false; return }
  if (key === 'delete') handleDeleteFact()
}

function openNodeDetail(node: GraphNode) {
  selectedNode.value = node
  if (node.type === 'Topic') activeCluster.value = activeCluster.value === node.cluster ? null : node.cluster || null
}

function stopReplayPlayback() {
  if (replayTimer.value !== null) {
    window.clearInterval(replayTimer.value)
    replayTimer.value = null
  }
  replayPlaying.value = false
}

function toggleReplay() {
  replayEnabled.value = !replayEnabled.value
  if (!replayEnabled.value) {
    replayProgress.value = 100
    stopReplayPlayback()
  }
}

function toggleReplayPlayback() {
  if (!replayEnabled.value) replayEnabled.value = true
  if (replayPlaying.value) {
    stopReplayPlayback()
    return
  }
  if (replayProgress.value >= 100) replayProgress.value = 0
  replayPlaying.value = true
  replayTimer.value = window.setInterval(() => {
    if (replayProgress.value >= 100) {
      replayProgress.value = 100
      stopReplayPlayback()
      return
    }
    replayProgress.value = Math.min(100, replayProgress.value + 2)
  }, 180)
}

function clearFilters() { activeCluster.value = null; activityFilter.value = 'all'; timeFilter.value = 'all'; typeFilter.value = 'all'; replayEnabled.value = false; replayProgress.value = 100; stopReplayPlayback(); searchQuery.value = ''; scale.value = 1 }
function formatDensity(value: number) { return `${(value * 100).toFixed(1)}%` }
function formatTime(value?: string | null) { if (!value) return '未知时间'; const t = new Date(value).getTime(); if (Number.isNaN(t)) return '未知时间'; const d = Math.floor((Date.now() - t) / 1000); if (d < 60) return '刚刚'; if (d < 3600) return `${Math.floor(d / 60)} 分钟前`; if (d < 86400) return `${Math.floor(d / 3600)} 小时前`; if (d < 86400 * 7) return `${Math.floor(d / 86400)} 天前`; return new Date(t).toLocaleDateString('zh-CN') }
function formatDateTime(value?: string | null) { if (!value) return '未知'; const d = new Date(value); return Number.isNaN(d.getTime()) ? '未知' : d.toLocaleString('zh-CN') }
function zoomGraph(direction: 'in' | 'out') { const next = direction === 'in' ? scale.value * 1.12 : scale.value * 0.88; scale.value = Math.max(0.72, Math.min(1.8, Number(next.toFixed(2)))) }
function focusGraph() { scale.value = 1; activeCluster.value = null }
function registerResize() { if (!stageRef.value) return; resizeObserver.value = new ResizeObserver((entries) => { const rect = entries[0]?.contentRect; if (rect) viewport.value = { width: rect.width, height: rect.height } }); resizeObserver.value.observe(stageRef.value) }

onMounted(async () => { registerResize(); await fetchGraph() })
onBeforeUnmount(() => {
  resizeObserver.value?.disconnect()
  stopReplayPlayback()
  if (birthFlashTimer.value !== null) {
    window.clearTimeout(birthFlashTimer.value)
    birthFlashTimer.value = null
  }
})
</script>

<template>
  <div class="insight-view" :style="sceneStyle">
    <div class="insight-content">
      <div ref="stageRef" class="graph-area">
        <div class="stars stars-a"></div>
        <div class="stars stars-b"></div>
        <div class="nebula"></div>

        <div class="toolbar glass">
          <div class="toolbar-stats">
            <span>密度 {{ formatDensity(graph.stats.density) }}</span>
            <span>节点 {{ graph.stats.nodes }}</span>
            <span>活跃 {{ graph.stats.activeFacts }}</span>
            <span>延迟 {{ graph.stats.latency }}ms</span>
          </div>
          <div class="toolbar-actions">
            <n-input v-model:value="searchQuery" clearable size="small" placeholder="搜索记忆或主题" class="search-input">
              <template #prefix><Search :size="14" /></template>
            </n-input>
            <button class="tool-btn" @click="focusGraph"><Target :size="16" /></button>
            <button class="tool-btn" @click="zoomGraph('in')"><ZoomIn :size="16" /></button>
            <button class="tool-btn" @click="zoomGraph('out')"><ZoomOut :size="16" /></button>
            <button class="tool-btn" :class="{ active: replayEnabled }" @click="toggleReplay"><Play :size="16" /></button>
            <button class="tool-btn primary" @click="fetchGraph"><RefreshCcw :size="16" :class="{ spinning: isLoading }" /></button>
          </div>
        </div>

        <div class="filter-box glass">
          <div class="filter-row">
            <span class="filter-label">星域</span>
            <button class="pill" :class="{ active: activeCluster === null }" @click="activeCluster = null">全部</button>
            <button v-for="topic in topicClusters" :key="topic.key" class="pill" :class="{ active: activeCluster === topic.key }" @click="activeCluster = activeCluster === topic.key ? null : topic.key">
              {{ topic.label }} · {{ topic.count }}
            </button>
          </div>
          <div class="filter-row">
            <span class="filter-label">活跃</span>
            <button class="pill" :class="{ active: activityFilter === 'all' }" @click="activityFilter = 'all'">全部</button>
            <button class="pill" :class="{ active: activityFilter === 'active' }" @click="activityFilter = 'active'">高活跃</button>
            <button class="pill" :class="{ active: activityFilter === 'stable' }" @click="activityFilter = 'stable'">稳定</button>
            <button class="pill" :class="{ active: activityFilter === 'cool' }" @click="activityFilter = 'cool'">冷区</button>
          </div>
          <div class="filter-row">
            <span class="filter-label">时间</span>
            <button class="pill" :class="{ active: timeFilter === 'all' }" @click="timeFilter = 'all'">全部</button>
            <button class="pill" :class="{ active: timeFilter === '24h' }" @click="timeFilter = '24h'">24小时</button>
            <button class="pill" :class="{ active: timeFilter === '7d' }" @click="timeFilter = '7d'">7天</button>
            <button class="pill" :class="{ active: timeFilter === '30d' }" @click="timeFilter = '30d'">30天</button>
            <button class="pill clear" @click="clearFilters">清空</button>
          </div>
          <div class="filter-row">
            <span class="filter-label">类型</span>
            <button class="pill" :class="{ active: typeFilter === 'all' }" @click="typeFilter = 'all'">全部</button>
            <button class="pill" :class="{ active: typeFilter === 'User' }" @click="typeFilter = 'User'">用户</button>
            <button class="pill" :class="{ active: typeFilter === 'Topic' }" @click="typeFilter = 'Topic'">主题</button>
            <button class="pill" :class="{ active: typeFilter === 'Fact' }" @click="typeFilter = 'Fact'">事实</button>
          </div>
        </div>

        <div class="summary-bar glass">
          <span>当前视野 {{ displayStats.nodes }}</span>
          <span>主题 {{ displayStats.topics }}</span>
          <span>高活跃 {{ displayStats.active }}</span>
          <span>新记忆 {{ displayStats.recent }}</span>
          <span>新写入 {{ displayStats.born }}</span>
        </div>

        <div class="legend-bar glass">
          <span><i class="legend-dot related"></i>关联链</span>
          <span><i class="legend-dot supersedes"></i>版本替代</span>
          <span><i class="legend-dot contradicts"></i>冲突关系</span>
        </div>

        <div class="replay-box glass">
          <div class="replay-header">
            <div class="replay-title"><Sparkles :size="14" /> 时间回放</div>
            <div class="replay-actions">
              <span>{{ replaySummary }}</span>
              <button class="pill" :class="{ active: replayPlaying }" @click="toggleReplayPlayback">{{ replayPlaying ? '暂停' : '播放' }}</button>
            </div>
          </div>
          <n-slider v-model:value="replayProgress" :disabled="!replayEnabled" :step="1" />
        </div>

        <div class="galaxy-stage" :style="{ transform: `scale(${scale})` }">
          <div class="orbit orbit-a"></div>
          <div class="orbit orbit-b"></div>
          <div class="orbit orbit-c"></div>
          <svg class="graph-links" :viewBox="`0 0 ${viewport.width} ${viewport.height}`">
            <line v-for="link in renderedLinks" :key="`${link.source.id}-${link.target.id}-${link.type}`" :x1="link.source.x" :y1="link.source.y" :x2="link.target.x" :y2="link.target.y" class="graph-link" :class="[link.type.toLowerCase(), { faded: link.faded, energized: link.energized }]" :style="{ strokeWidth: `${link.width}px` }" />
          </svg>
          <div v-for="node in positionedNodes" :key="node.id" class="node" :class="[node.type.toLowerCase(), node.status || 'stable', { selected: selectedNode?.id === node.id, faded: node.faded, recent: node.isRecent, hit: node.isHit, focus: node.isFocus, born: node.isBorn }]" :style="{ left: `${node.x}px`, top: `${node.y}px`, '--node-size': `${node.size}px`, '--node-color': node.color }" @click="openNodeDetail(node)" @contextmenu.prevent="node.type === 'Fact' && (rightClickedNode = node, dropdownX = $event.clientX, dropdownY = $event.clientY, showDropdown = true)">
            <span class="core"></span>
            <span v-if="node.isBorn" class="birth-ring"></span>
            <span v-if="node.showLabel" class="label">{{ node.shortLabel || node.label }}</span>
          </div>
        </div>

        <div v-if="!isLoaded || isLoading" class="loading-overlay">
          <div class="loading-text">正在重构记忆星系</div>
        </div>
      </div>

      <transition name="panel">
        <aside v-if="selectedNode" class="detail-panel">
          <div class="panel-header">
            <div><span class="panel-label">节点解析</span><h3 class="panel-title">记忆图谱</h3></div>
            <button class="close-btn" @click="selectedNode = null"><RefreshCcw :size="15" style="transform: rotate(90deg)" /></button>
          </div>
          <n-scrollbar class="panel-body">
            <div class="panel-content">
              <div class="tag-row">
                <n-tag :bordered="false" size="small" :type="selectedNode.type === 'User' ? 'success' : selectedNode.type === 'Topic' ? 'info' : 'warning'">{{ selectedNode.type === 'User' ? '核心用户' : selectedNode.type === 'Topic' ? '主题轨道' : '记忆碎片' }}</n-tag>
                <n-tag v-if="selectedNode.status" :bordered="false" size="small">{{ selectedNode.status }}</n-tag>
              </div>
              <div class="content-card">
                <p class="node-title">{{ selectedNode.label }}</p>
                <p class="node-meta">{{ selectedNode.subType || '未分类记忆' }}<span v-if="selectedNode.cluster"> · {{ selectedNode.cluster }}</span></p>
              </div>
              <div class="metric-row"><span>活跃度</span><span>{{ ((selectedNode.activityScore || 0) * 100).toFixed(0) }}%</span></div>
              <n-progress type="line" :percentage="(selectedNode.activityScore || 0) * 100" :show-indicator="false" :height="5" />
              <div class="metric-row"><span>置信度</span><span>{{ ((selectedNode.confidence || 0) * 100).toFixed(0) }}%</span></div>
              <n-progress type="line" :percentage="(selectedNode.confidence || 0) * 100" :show-indicator="false" :height="5" />
              <div class="mini-grid">
                <div class="mini-card"><span>重要性</span><strong>{{ ((selectedNode.importance || 0) * 100).toFixed(0) }}%</strong></div>
                <div class="mini-card"><span>轨道层级</span><strong>{{ selectedNode.orbitLevel ?? 0 }}</strong></div>
              </div>
              <div v-if="selectedNode.type === 'Fact'" class="mini-grid">
                <div class="mini-card"><span>版本号</span><strong>{{ selectedNode.version ?? 1 }}</strong></div>
                <div class="mini-card"><span>关系状态</span><strong>{{ selectedNode.supersedesFactId ? '替代旧记忆' : selectedNode.contradictsFactId ? '存在冲突' : '稳定事实' }}</strong></div>
              </div>
              <div class="time-card"><Clock3 :size="18" /><div><div class="time-main">{{ formatTime(selectedNode.lastActivatedAt || selectedNode.createdAt) }}</div><div class="time-sub">{{ formatDateTime(selectedNode.lastActivatedAt || selectedNode.createdAt) }}</div></div></div>
              <div class="panel-actions">
                <n-button block secondary @click="selectedNode.cluster && (activeCluster = selectedNode.cluster)">聚焦当前星域</n-button>
                <n-button v-if="selectedNode.type === 'Fact'" type="error" secondary circle @click="rightClickedNode = selectedNode; handleDeleteFact()"><template #icon><Trash2 :size="16" /></template></n-button>
              </div>
            </div>
          </n-scrollbar>
        </aside>
      </transition>
    </div>

    <n-dropdown placement="bottom-start" trigger="manual" :x="dropdownX" :y="dropdownY" :options="dropdownOptions" :show="showDropdown" :on-clickoutside="() => (showDropdown = false)" @select="handleSelectDropdown" />
  </div>
</template>

<style scoped>
.insight-view,.insight-content{height:100%}.insight-content{display:flex;gap:20px}.graph-area{position:relative;flex:1;min-width:0;overflow:hidden;border-radius:28px;border:1px solid color-mix(in srgb,var(--graph-outline) 70%,transparent);background:linear-gradient(180deg,rgba(7,10,18,.98),rgba(6,8,14,.96));box-shadow:0 28px 80px color-mix(in srgb,var(--graph-glow) 12%,transparent)}.stars,.nebula,.galaxy-stage,.loading-overlay{position:absolute;inset:0}.stars-a{opacity:.38;background-image:radial-gradient(circle at 14% 18%,rgba(255,255,255,.9) 0 1px,transparent 1.6px),radial-gradient(circle at 72% 24%,color-mix(in srgb,var(--graph-primary) 70%,white) 0 1.2px,transparent 1.8px),radial-gradient(circle at 36% 78%,color-mix(in srgb,var(--graph-core) 70%,white) 0 1px,transparent 1.8px)}.stars-b{opacity:.24;background-image:radial-gradient(circle at 22% 84%,rgba(255,255,255,.72) 0 1px,transparent 1.6px),radial-gradient(circle at 84% 64%,color-mix(in srgb,var(--graph-fact) 72%,white) 0 1px,transparent 1.8px)}.nebula{opacity:.34;filter:blur(26px);background:radial-gradient(circle at 18% 22%,color-mix(in srgb,var(--graph-primary) 22%,transparent),transparent 30%),radial-gradient(circle at 78% 28%,color-mix(in srgb,var(--graph-fact) 18%,transparent),transparent 30%),radial-gradient(circle at 52% 70%,color-mix(in srgb,var(--graph-core) 16%,transparent),transparent 34%)}.glass{border:1px solid rgba(255,255,255,.08);background:rgba(8,12,20,.58);backdrop-filter:blur(16px)}.toolbar,.filter-box,.summary-bar,.legend-bar{position:absolute;left:18px;right:18px;z-index:4;border-radius:18px}.toolbar{top:18px;display:flex;gap:12px;align-items:center;justify-content:space-between;padding:10px 14px}.toolbar-stats,.toolbar-actions{display:flex;gap:14px;align-items:center;color:rgba(255,255,255,.82)}.search-input{width:220px}.tool-btn,.pill,.close-btn{border:0}.tool-btn{width:38px;height:38px;border-radius:12px;color:rgba(255,255,255,.82);background:rgba(255,255,255,.05)}.tool-btn.primary{background:color-mix(in srgb,var(--graph-primary) 24%,rgba(255,255,255,.06))}.filter-box{top:84px;padding:12px;display:flex;flex-direction:column;gap:10px}.filter-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.filter-label{min-width:44px;font-size:12px;color:rgba(255,255,255,.52)}.pill{padding:7px 12px;border-radius:999px;color:rgba(255,255,255,.76);background:rgba(255,255,255,.05)}.pill.active{color:#fff;background:color-mix(in srgb,var(--graph-primary) 24%,rgba(255,255,255,.08))}.pill.clear{margin-left:auto}.summary-bar{top:246px;display:flex;gap:24px;padding:10px 14px;color:rgba(255,255,255,.82)}.legend-bar{top:338px;display:flex;gap:22px;padding:10px 14px;color:rgba(255,255,255,.78)}.legend-dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:8px}.legend-dot.related{background:color-mix(in srgb,var(--graph-edge) 80%,white)}.legend-dot.supersedes{background:#fbbf24}.legend-dot.contradicts{background:#fb7185}.galaxy-stage{transform-origin:center center;transition:transform .24s ease}.orbit{position:absolute;left:50%;top:58%;border-radius:50%;border:1px solid rgba(255,255,255,.08);transform:translate(-50%,-50%)}.orbit-a{width:26%;height:18%}.orbit-b{width:48%;height:32%}.orbit-c{width:70%;height:48%}.graph-links{width:100%;height:100%}.graph-link{stroke:color-mix(in srgb,var(--graph-edge) 70%,white);stroke-linecap:round;stroke-dasharray:10 12;opacity:.48}.graph-link.related_to{stroke:color-mix(in srgb,var(--graph-edge) 78%,white)}.graph-link.supersedes{stroke:#fbbf24;stroke-dasharray:4 8;opacity:.9}.graph-link.contradicts{stroke:#fb7185;stroke-dasharray:2 10;opacity:.95}.graph-link.faded{opacity:.14}.graph-link.energized{opacity:.96;stroke:color-mix(in srgb,var(--graph-primary) 60%,white);filter:drop-shadow(0 0 8px color-mix(in srgb,var(--graph-primary) 35%,transparent));animation:linkFlow 1.6s linear infinite}.graph-link.supersedes.energized{stroke:#fde68a}.graph-link.contradicts.energized{stroke:#fda4af}.node{position:absolute;transform:translate(-50%,-50%);cursor:pointer;transition:opacity .22s ease,transform .22s ease}.node.faded{opacity:.28}.core{display:block;width:var(--node-size);height:var(--node-size);border-radius:50%;background:radial-gradient(circle at 38% 38%,rgba(255,255,255,.86),transparent 22%),radial-gradient(circle,color-mix(in srgb,var(--node-color) 78%,white),color-mix(in srgb,var(--node-color) 58%,transparent) 62%,transparent 100%);box-shadow:0 0 0 1px color-mix(in srgb,var(--node-color) 34%,white),0 0 26px color-mix(in srgb,var(--node-color) 40%,transparent)}.birth-ring{position:absolute;left:50%;top:50%;width:calc(var(--node-size) + 10px);height:calc(var(--node-size) + 10px);border-radius:50%;border:1px solid color-mix(in srgb,var(--node-color) 58%,white);transform:translate(-50%,-50%);animation:birthPulse 1.2s ease-out infinite}.node.topic .core{border-radius:18px}.node.user .core{box-shadow:0 0 0 1px color-mix(in srgb,var(--node-color) 40%,white),0 0 42px color-mix(in srgb,var(--node-color) 50%,transparent),0 0 90px color-mix(in srgb,var(--node-color) 20%,transparent)}.node.selected{transform:translate(-50%,-50%) scale(1.04)}.node.selected .core,.node.focus .core{box-shadow:0 0 0 2px rgba(255,255,255,.26),0 0 34px color-mix(in srgb,var(--node-color) 46%,transparent)}.node.hit .core{box-shadow:0 0 0 2px color-mix(in srgb,var(--graph-primary) 38%,white),0 0 30px color-mix(in srgb,var(--graph-primary) 42%,transparent)}.node.born .core{box-shadow:0 0 0 2px color-mix(in srgb,var(--node-color) 44%,white),0 0 40px color-mix(in srgb,var(--node-color) 50%,transparent),0 0 76px color-mix(in srgb,var(--node-color) 24%,transparent);animation:bornFlash 1.2s ease-in-out infinite}.label{display:block;margin-top:10px;font-size:12px;color:rgba(255,255,255,.86);text-align:center;text-shadow:0 1px 14px rgba(0,0,0,.8);white-space:nowrap}.loading-overlay{z-index:5;display:flex;align-items:center;justify-content:center;background:rgba(5,8,14,.44)}.loading-text{color:#fff;letter-spacing:.16em}.detail-panel{width:340px;border-radius:28px;overflow:hidden;border:1px solid color-mix(in srgb,var(--graph-outline) 70%,transparent);background:linear-gradient(180deg,rgba(10,14,26,.96),rgba(7,10,18,.98));box-shadow:0 28px 72px rgba(0,0,0,.28)}.panel-header{display:flex;justify-content:space-between;align-items:center;padding:18px;border-bottom:1px solid rgba(255,255,255,.06)}.panel-label{display:block;font-size:11px;letter-spacing:.16em;text-transform:uppercase;color:rgba(255,255,255,.46)}.panel-title{margin:6px 0 0;color:#fff}.close-btn{width:34px;height:34px;border-radius:12px;color:rgba(255,255,255,.76);background:rgba(255,255,255,.05)}.panel-body{height:calc(100% - 76px)}.panel-content{padding:18px;display:flex;flex-direction:column;gap:16px}.tag-row{display:flex;gap:8px;flex-wrap:wrap}.content-card,.mini-card,.time-card{padding:14px;border-radius:18px;border:1px solid rgba(255,255,255,.06);background:rgba(255,255,255,.03)}.node-title{margin:0;color:#fff;font-size:16px;line-height:1.55}.node-meta{margin:8px 0 0;color:rgba(255,255,255,.56);font-size:13px}.metric-row{display:flex;justify-content:space-between;color:rgba(255,255,255,.74)}.mini-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.mini-card span{display:block;color:rgba(255,255,255,.48);font-size:12px}.mini-card strong{display:block;margin-top:8px;color:#fff;font-size:16px}.time-card{display:flex;gap:12px;align-items:center;color:#fff}.time-main{font-weight:700}.time-sub{margin-top:4px;color:rgba(255,255,255,.54);font-size:13px}.panel-actions{display:flex;gap:10px;align-items:center}.panel-enter-active,.panel-leave-active{transition:transform .24s ease,opacity .24s ease}.panel-enter-from,.panel-leave-to{opacity:0;transform:translateX(12px)}.spinning{animation:spin 1.2s linear infinite}@keyframes spin{from{transform:rotate(0)}to{transform:rotate(360deg)}}@keyframes linkFlow{from{stroke-dashoffset:28}to{stroke-dashoffset:0}}@keyframes birthPulse{0%{opacity:.9;transform:translate(-50%,-50%) scale(.9)}100%{opacity:0;transform:translate(-50%,-50%) scale(1.55)}}@keyframes bornFlash{0%,100%{transform:scale(1)}50%{transform:scale(1.08)}}@media (max-width:1100px){.insight-content{flex-direction:column}.detail-panel{width:100%;min-height:320px}.summary-bar{top:286px}.legend-bar{top:378px}}
.tool-btn.active{background:color-mix(in srgb,var(--graph-primary) 28%,rgba(255,255,255,.08));color:#fff}.replay-box{position:absolute;left:18px;right:18px;top:302px;z-index:4;border-radius:18px;padding:12px 14px}.replay-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;color:rgba(255,255,255,.8);font-size:13px}.replay-title{display:flex;align-items:center;gap:8px;color:#fff}.replay-actions{display:flex;align-items:center;gap:10px}.node.recent .core{box-shadow:0 0 0 1px color-mix(in srgb,var(--node-color) 34%,white),0 0 26px color-mix(in srgb,var(--node-color) 40%,transparent),0 0 48px color-mix(in srgb,var(--node-color) 36%,transparent)}@media (max-width:1100px){.replay-box{top:342px}}
</style>
