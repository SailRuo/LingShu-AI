<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NButton, NDropdown, NIcon, NInput, NProgress, NScrollbar, NSlider, NTag, useMessage } from 'naive-ui'
import { Clock3, Eye, Play, RefreshCcw, Search, Sparkles, Target, Trash2, ZoomIn, ZoomOut } from 'lucide-vue-next'


import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js'

type NodeType = 'User' | 'Topic' | 'Fact'
type ActivityFilter = 'all' | 'active' | 'stable' | 'cool'
type TimeFilter = 'all' | '24h' | '7d' | '30d'
type TypeFilter = 'all' | NodeType
type NodeStatus = 'core' | 'active' | 'stable' | 'cool'

interface GraphNode { id: string; label: string; shortLabel?: string; type: NodeType; subType?: string; importance?: number; confidence?: number; activityScore?: number; cluster?: string; orbitLevel?: number; createdAt?: string | null; lastActivatedAt?: string | null; status?: NodeStatus | string; factCount?: number; version?: number; supersedesFactId?: number | null; contradictsFactId?: number | null }
interface GraphLink { source: string; target: string; type: string; weight?: number }
interface GraphPayload { nodes: GraphNode[]; links: GraphLink[]; stats: { density: number; nodes: number; edges: number; topics: number; activeFacts: number; latency: number } }

interface PositionedNode3D extends GraphNode {
  x: number; y: number; z: number;
  size: number;
  color: string;
  showLabel: boolean;
  faded: boolean;
  hiddenByReplay: boolean;
  isRecent: boolean;
  isHit: boolean;
  isFocus: boolean;
  isBorn: boolean;
}

const message = useMessage()

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
const viewport = ref({ width: 1200, height: 760 })

// Three.js REFS
const galaxyStageRef = ref<HTMLElement | null>(null)
const resizeObserver = ref<ResizeObserver | null>(null)
let renderer: THREE.WebGLRenderer
let cssRenderer: CSS2DRenderer
let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let controls: OrbitControls
let animationFrameId: number
const meshGroup = new THREE.Group()
const linkGroup = new THREE.Group()

let nodeMeshes = new Map<string, THREE.Group>()
let linkMeshes = new Map<string, THREE.Line>()
const raycaster = new THREE.Raycaster()
const mouse = new THREE.Vector2()

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

const resolvedColors = ref({
  primary: '#38bdf8',
  accent: '#818cf8',
  nodeUser: '#facc15',
  nodeFact: '#34d399',
  edge: '#94a3b8',
})

const sceneStyle = computed(() => ({ 
  '--graph-primary': resolvedColors.value.primary, 
  '--graph-accent': resolvedColors.value.accent, 
  '--graph-core': resolvedColors.value.nodeUser, 
  '--graph-fact': resolvedColors.value.nodeFact, 
  '--graph-edge': resolvedColors.value.edge, 
  '--graph-glow': resolvedColors.value.primary, 
  '--graph-outline': 'rgba(255,255,255,0.1)' 
}))

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

const searchKeyword = computed(() => searchQuery.value.trim().toLowerCase())

const positionedNodes3D = computed<PositionedNode3D[]>(() => {
  const topics = filteredNodes.value.filter((n) => n.type === 'Topic')
  const facts = filteredNodes.value.filter((n) => n.type === 'Fact')
  const users = filteredNodes.value.filter((n) => n.type === 'User')
  const pos = new Map<string, PositionedNode3D>()
  
  users.forEach((n) => pos.set(n.id, { 
    ...n, x: 0, y: 0, z: 0, size: 24, color: resolvedColors.value.nodeUser, 
    showLabel: true, faded: false, hiddenByReplay: false, 
    isRecent: false, isHit: false, isFocus: selectedNode.value?.id === n.id, isBorn: false 
  }))
  
  topics.forEach((n, i) => {
    const angle = (i / Math.max(1, topics.length)) * Math.PI * 2
    const radius = 120
    const faded = !!activeCluster.value && n.cluster !== activeCluster.value
    const activatedAt = n.lastActivatedAt ? new Date(n.lastActivatedAt).getTime() : 0
    const hiddenByReplay = replayEnabled.value && activatedAt > replayCutoff.value
    const isRecent = activatedAt >= Date.now() - 86400000
    const isHit = !!searchKeyword.value && (n.label.toLowerCase().includes(searchKeyword.value) || (n.shortLabel || '').toLowerCase().includes(searchKeyword.value))
    const isFocus = selectedNode.value?.id === n.id || (!!selectedNode.value?.cluster && selectedNode.value.cluster === n.cluster)
    
    pos.set(n.id, { 
      ...n, 
      x: Math.cos(angle) * radius, 
      y: (Math.random() - 0.5) * 20,
      z: Math.sin(angle) * radius, 
      size: 10 + (n.importance || 0.5) * 12, 
      color: resolvedColors.value.primary, 
      showLabel: true, faded, hiddenByReplay, isRecent, isHit, isFocus, isBorn: birthFlashIds.value.has(n.id) 
    })
  })
  
  const groups = new Map<string, GraphNode[]>()
  facts.forEach((f) => { const k = f.cluster || 'memory'; if (!groups.has(k)) groups.set(k, []); groups.get(k)!.push(f) })
  
  Array.from(groups.entries()).forEach(([cluster, items], gi) => {
    const topic = topics.find((n) => n.cluster === cluster)
    const anchor = topic ? pos.get(topic.id) : { x:0, y:0, z:0 }
    
    items.sort((a, b) => (b.importance || 0) - (a.importance || 0)).forEach((n, i) => {
      const level = n.orbitLevel || 2
      const radius = (level === 1 ? 30 : level === 2 ? 60 : 90) + (i % 5) * 4
      const angle = (i / items.length) * Math.PI * 2 + gi
      const zAngle = (Math.random() - 0.5) * Math.PI * 0.4
      
      const faded = !!activeCluster.value && cluster !== activeCluster.value
      const activatedAt = n.lastActivatedAt ? new Date(n.lastActivatedAt).getTime() : 0
      const hiddenByReplay = replayEnabled.value && activatedAt > replayCutoff.value
      const isRecent = activatedAt >= Date.now() - 86400000
      const isHit = !!searchKeyword.value && (n.label.toLowerCase().includes(searchKeyword.value) || (n.shortLabel || '').toLowerCase().includes(searchKeyword.value))
      const isFocus = selectedNode.value?.id === n.id || (!!selectedNode.value?.cluster && selectedNode.value.cluster === n.cluster)
      
      pos.set(n.id, { 
        ...n, 
        x: anchor!.x + Math.cos(angle) * Math.cos(zAngle) * radius, 
        y: anchor!.y + Math.sin(zAngle) * radius * 0.6, 
        z: anchor!.z + Math.sin(angle) * Math.cos(zAngle) * radius, 
        size: 3 + (n.importance || 0.5) * 6, 
        color: n.status === 'active' ? resolvedColors.value.nodeFact : resolvedColors.value.accent, 
        showLabel: (n.importance || 0) >= 0.76 || isRecent || isFocus, 
        faded, hiddenByReplay, isRecent, isHit, isFocus, isBorn: birthFlashIds.value.has(n.id) 
      })
    })
  })
  
  return Array.from(pos.values()).filter(n => !n.hiddenByReplay)
})

const displayStats = computed(() => ({ nodes: positionedNodes3D.value.length, topics: positionedNodes3D.value.filter((n) => n.type === 'Topic').length, active: positionedNodes3D.value.filter((n) => n.type === 'Fact' && (n.activityScore || 0) >= 0.75).length, recent: positionedNodes3D.value.filter((n) => n.isRecent).length, born: positionedNodes3D.value.filter((n) => n.isBorn).length }))
const replaySummary = computed(() => new Date(replayEnabled.value ? replayCutoff.value : timelineBounds.value.last).toLocaleString('zh-CN'))

// Generate Glow Texture
const circleTexture = (() => {
  const canvas = document.createElement('canvas')
  canvas.width = 128
  canvas.height = 128
  const ctx = canvas.getContext('2d')!
  const gradient = ctx.createRadialGradient(64, 64, 0, 64, 64, 64)
  gradient.addColorStop(0, 'rgba(255,255,255,1)')
  gradient.addColorStop(0.2, 'rgba(255,255,255,0.8)')
  gradient.addColorStop(0.5, 'rgba(255,255,255,0.2)')
  gradient.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, 128, 128)
  return new THREE.CanvasTexture(canvas)
})()

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
      if (birthFlashTimer.value !== null) window.clearTimeout(birthFlashTimer.value)
      birthFlashTimer.value = window.setTimeout(() => { birthFlashIds.value = new Set(); birthFlashTimer.value = null }, 4200)
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
  
  // Pivot camera to node
  if (controls && camera) {
    const p3d = positionedNodes3D.value.find(n => n.id === node.id)
    if (p3d) {
      const targetVec = new THREE.Vector3(p3d.x, p3d.y, p3d.z)
      const currentDist = camera.position.distanceTo(controls.target)
      const newCamPos = targetVec.clone().add(new THREE.Vector3(0, currentDist * 0.3, currentDist * 0.7))
      camera.position.lerp(newCamPos, 0.5)
      controls.target.lerp(targetVec, 0.5)
    }
  }
}

function zoomGraph(direction: 'in' | 'out') { 
  if (!camera || !controls) return
  const factor = direction === 'in' ? 0.7 : 1.3
  const target = controls.target.clone()
  camera.position.lerp(target, 1 - factor)
  controls.update()
}

function focusGraph() { 
  activeCluster.value = null
  if (camera && controls) {
    camera.position.set(0, 200, 300)
    controls.target.set(0, 0, 0)
    controls.update()
  }
}

// Three.js Core
function initThree() {
  if (!galaxyStageRef.value) return
  const width = galaxyStageRef.value.clientWidth
  const height = galaxyStageRef.value.clientHeight

  scene = new THREE.Scene()

  camera = new THREE.PerspectiveCamera(60, width / height, 1, 3000)
  camera.position.set(0, 200, 300)
  camera.lookAt(0, 0, 0)

  renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.setClearColor(0x000000, 0)
  galaxyStageRef.value.appendChild(renderer.domElement)

  cssRenderer = new CSS2DRenderer()
  cssRenderer.setSize(width, height)
  cssRenderer.domElement.style.position = 'absolute'
  cssRenderer.domElement.style.top = '0px'
  cssRenderer.domElement.style.pointerEvents = 'none'
  galaxyStageRef.value.appendChild(cssRenderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05
  controls.maxDistance = 1500
  controls.minDistance = 10

  scene.add(meshGroup)
  scene.add(linkGroup)

  renderer.domElement.addEventListener('pointermove', onPointerMove)
  renderer.domElement.addEventListener('click', onClick)
  renderer.domElement.addEventListener('contextmenu', onContextMenu)

  animate()
}

function onPointerMove(event: PointerEvent) {
  if (!galaxyStageRef.value) return
  const rect = galaxyStageRef.value.getBoundingClientRect()
  mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  mouse.y = - ((event.clientY - rect.top) / rect.height) * 2 + 1
}

function onClick() {
  raycaster.setFromCamera(mouse, camera)
  const intersects = raycaster.intersectObjects(meshGroup.children, true)
  if (intersects.length > 0) {
    let object = intersects[0].object
    let nodeId = object.userData.nodeId
    while (!nodeId && object.parent) {
      object = object.parent
      nodeId = object.userData.nodeId
    }
    const node = positionedNodes3D.value.find(n => n.id === nodeId)
    if (node) openNodeDetail(node)
  }
}

function onContextMenu(event: MouseEvent) {
  event.preventDefault()
  raycaster.setFromCamera(mouse, camera)
  const intersects = raycaster.intersectObjects(meshGroup.children, true)
  if (intersects.length > 0) {
    let object = intersects[0].object
    let nodeId = object.userData.nodeId
    while (!nodeId && object.parent) {
      object = object.parent
      nodeId = object.userData.nodeId
    }
    const node = positionedNodes3D.value.find(n => n.id === nodeId)
    if (node && node.type === 'Fact') {
      rightClickedNode.value = node
      dropdownX.value = event.clientX
      dropdownY.value = event.clientY
      showDropdown.value = true
    }
  }
}

function animate() {
  animationFrameId = requestAnimationFrame(animate)
  controls.update()
  
  const time = Date.now() * 0.005
  for (const id of birthFlashIds.value) {
    const group = nodeMeshes.get(id)
    if (group) {
      const sprite = group.children.find(c => c.type === 'Sprite') as THREE.Sprite
      if (sprite) {
        const node = positionedNodes3D.value.find(n => n.id === id)
        if (node) {
          const pulse = 1.0 + Math.sin(time) * 0.5
          sprite.scale.set(node.size * pulse, node.size * pulse, 1)
        }
      }
    }
  }

  // Auto rotate slowly if no user interaction
  if (!activeCluster.value && !selectedNode.value) {
    scene.rotation.y += 0.0005
  } else {
    // smoothly reset rotation
    scene.rotation.y = THREE.MathUtils.lerp(scene.rotation.y, 0, 0.005)
  }

  renderer.render(scene, camera)
  cssRenderer.render(scene, camera)
}

function onWindowResize() {
  if (!galaxyStageRef.value) return
  const width = galaxyStageRef.value.clientWidth
  const height = galaxyStageRef.value.clientHeight
  viewport.value = { width, height }
  if (camera) {
    camera.aspect = width / height
    camera.updateProjectionMatrix()
  }
  if (renderer) renderer.setSize(width, height)
  if (cssRenderer) cssRenderer.setSize(width, height)
}

// Scene synchronization
watch(positionedNodes3D, (nodes) => {
  if (!scene) return
  const currentIds = new Set(nodes.map(n => n.id))
  
  for (const [id, group] of nodeMeshes.entries()) {
    if (!currentIds.has(id)) {
      const cssObject = group.children.find(c => c instanceof CSS2DObject) as CSS2DObject
      if (cssObject && cssObject.element) {
        cssObject.element.remove()
      }
      meshGroup.remove(group)
      nodeMeshes.delete(id)
    }
  }
  
  nodes.forEach(node => {
    let group = nodeMeshes.get(node.id)
    if (!group) {
      group = new THREE.Group()
      group.userData.nodeId = node.id
      
      const spriteMat = new THREE.SpriteMaterial({ map: circleTexture, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false })
      const sprite = new THREE.Sprite(spriteMat)
      sprite.userData.nodeId = node.id
      group.add(sprite)
      
      const labelDiv = document.createElement('div')
      labelDiv.className = 'node-label'
      labelDiv.style.pointerEvents = 'auto'
      labelDiv.onclick = () => openNodeDetail(node)
      const cssObject = new CSS2DObject(labelDiv)
      cssObject.position.set(0, -10, 0)
      group.add(cssObject)
      
      meshGroup.add(group)
      nodeMeshes.set(node.id, group)
    }
    
    group.position.set(node.x, node.y, node.z)
    
    const sprite = group.children[0] as THREE.Sprite
    if (!node.isBorn) sprite.scale.set(node.size, node.size, 1)
    
    const mat = sprite.material as THREE.SpriteMaterial
    mat.color.set(node.color)
    mat.opacity = node.faded ? 0.15 : (node.isHit || node.isFocus ? 1.0 : 0.8)
    
    if (node.isFocus || node.isHit) sprite.scale.set(node.size * 1.5, node.size * 1.5, 1)
    
    const cssObject = group.children[1] as CSS2DObject
    const labelDiv = cssObject.element
    labelDiv.textContent = node.shortLabel || node.label
    labelDiv.style.visibility = node.showLabel ? 'visible' : 'hidden'
    labelDiv.style.opacity = node.faded ? '0.2' : '1'
    if (node.isFocus) {
      labelDiv.style.color = 'var(--color-text)'
      labelDiv.style.textShadow = `0 0 10px ${node.color}`
    } else {
      labelDiv.style.color = 'var(--color-text-dim)'
      labelDiv.style.textShadow = 'none'
    }
  })
}, { deep: true })

const defaultLineMat = new THREE.LineBasicMaterial({ color: 0x94a3b8, transparent: true, opacity: 0.25 })
const energizedLineMat = new THREE.LineBasicMaterial({ color: 0x38bdf8, transparent: true, opacity: 0.8 })
const supersedesMat = new THREE.LineBasicMaterial({ color: 0xfbbf24, transparent: true, opacity: 0.6 })
const contradictsMat = new THREE.LineBasicMaterial({ color: 0xfb7185, transparent: true, opacity: 0.6 })

const computed3DLinks = computed(() => {
  return filteredLinks.value.map(l => {
    const s = positionedNodes3D.value.find(n => n.id === l.source)
    const t = positionedNodes3D.value.find(n => n.id === l.target)
    if (!s || !t) return null
    return { id: `${l.source}-${l.target}-${l.type}`, source: s, target: t, type: l.type, weight: l.weight || 0.35, faded: s.faded || t.faded, energized: s.isHit || t.isHit || s.isFocus || t.isFocus }
  }).filter(Boolean) as any[]
})

watch(computed3DLinks, (links) => {
  if (!scene) return
  
  const currentIds = new Set(links.map(l => l.id))
  for (const [id, line] of linkMeshes.entries()) {
    if (!currentIds.has(id)) {
      linkGroup.remove(line)
      line.geometry.dispose()
      linkMeshes.delete(id)
    }
  }
  
  links.forEach(l => {
    let line = linkMeshes.get(l.id)
    if (!line) {
      const geo = new THREE.BufferGeometry()
      geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(6), 3))
      
      let mat = defaultLineMat
      if (l.type === 'SUPERSEDES') mat = supersedesMat
      else if (l.type === 'CONTRADICTS') mat = contradictsMat
      
      line = new THREE.Line(geo, mat)
      linkGroup.add(line)
      linkMeshes.set(l.id, line)
    }
    
    const posAttribute = line.geometry.getAttribute('position') as THREE.BufferAttribute
    posAttribute.setXYZ(0, l.source.x, l.source.y, l.source.z)
    posAttribute.setXYZ(1, l.target.x, l.target.y, l.target.z)
    posAttribute.needsUpdate = true
    
    let targetMat = defaultLineMat
    if (l.energized) targetMat = energizedLineMat
    else if (l.type === 'SUPERSEDES') targetMat = supersedesMat
    else if (l.type === 'CONTRADICTS') targetMat = contradictsMat
    line.material = targetMat
    
    ;(line.material as THREE.LineBasicMaterial).opacity = l.faded ? 0.05 : (l.energized ? 0.9 : 0.4)
  })
}, { deep: true })

function updateColors() {
  const rs = getComputedStyle(document.body)
  const prim = rs.getPropertyValue('--color-primary').trim()
  const acc = rs.getPropertyValue('--color-accent').trim()
  const nu = rs.getPropertyValue('--color-node-user').trim()
  const nf = rs.getPropertyValue('--color-node-fact').trim()
  const edge = rs.getPropertyValue('--color-edge').trim()
  
  resolvedColors.value = {
    primary: prim || '#0f766e',
    accent: acc || '#ea580c',
    nodeUser: nu || '#0f766e',
    nodeFact: nf || '#ea580c',
    edge: edge || 'rgba(15, 118, 110, 0.3)'
  }
}

let themeObserver: MutationObserver | null = null

onMounted(async () => { 
  updateColors()
  themeObserver = new MutationObserver(() => updateColors())
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['style', 'class', 'data-theme'] })
  themeObserver.observe(document.body, { attributes: true, attributeFilter: ['style', 'class'] })
  
  initThree()
  resizeObserver.value = new ResizeObserver(onWindowResize)
  if (galaxyStageRef.value) resizeObserver.value.observe(galaxyStageRef.value)
  await fetchGraph() 
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrameId)
  resizeObserver.value?.disconnect()
  themeObserver?.disconnect()
  
  for (const group of nodeMeshes.values()) {
    const cssObject = group.children.find(c => c instanceof CSS2DObject) as CSS2DObject
    if (cssObject && cssObject.element) {
      cssObject.element.remove()
    }
  }
  
  renderer?.dispose()
  if (birthFlashTimer.value !== null) window.clearTimeout(birthFlashTimer.value)
  stopReplayPlayback()
})

function stopReplayPlayback() {
  if (replayTimer.value !== null) {
    window.clearInterval(replayTimer.value)
    replayTimer.value = null
  }
  replayPlaying.value = false
}

function toggleReplay() {
  replayEnabled.value = !replayEnabled.value
  if (!replayEnabled.value) { replayProgress.value = 100; stopReplayPlayback() }
}

function toggleReplayPlayback() {
  if (!replayEnabled.value) replayEnabled.value = true
  if (replayPlaying.value) { stopReplayPlayback(); return }
  if (replayProgress.value >= 100) replayProgress.value = 0
  replayPlaying.value = true
  replayTimer.value = window.setInterval(() => {
    if (replayProgress.value >= 100) { replayProgress.value = 100; stopReplayPlayback(); return }
    replayProgress.value = Math.min(100, replayProgress.value + 2)
  }, 180)
}

function clearFilters() { activeCluster.value = null; activityFilter.value = 'all'; timeFilter.value = 'all'; typeFilter.value = 'all'; replayEnabled.value = false; replayProgress.value = 100; stopReplayPlayback(); searchQuery.value = ''; focusGraph() }
function formatDensity(value: number) { return `${(value * 100).toFixed(1)}%` }
function formatTime(value?: string | null) { if (!value) return '未知时间'; const t = new Date(value).getTime(); if (Number.isNaN(t)) return '未知时间'; const d = Math.floor((Date.now() - t) / 1000); if (d < 60) return '刚刚'; if (d < 3600) return `${Math.floor(d / 60)} 分钟前`; if (d < 86400) return `${Math.floor(d / 3600)} 小时前`; if (d < 86400 * 7) return `${Math.floor(d / 86400)} 天前`; return new Date(t).toLocaleDateString('zh-CN') }
function formatDateTime(value?: string | null) { if (!value) return '未知'; const d = new Date(value); return Number.isNaN(d.getTime()) ? '未知' : d.toLocaleString('zh-CN') }
</script>

<template>
  <div class="insight-view" :style="sceneStyle">
    <div class="insight-content">
      <div class="graph-area">
        <div class="stars stars-a"></div>
        <div class="stars stars-b"></div>
        <div class="nebula"></div>

        <!-- TOP HUD -->
        <div class="top-hud">
          <div class="hud-panel glass stats-bar">
            <span>密度 {{ formatDensity(graph.stats.density) }}</span>
            <span class="divider"></span>
            <span>节点 {{ graph.stats.nodes }}</span>
            <span class="divider"></span>
            <span>活跃 {{ graph.stats.activeFacts }}</span>
            <span class="divider"></span>
            <span>延迟 {{ graph.stats.latency }}ms</span>
          </div>

          <div class="hud-panel glass action-bar">
            <n-input v-model:value="searchQuery" clearable size="small" placeholder="搜索记忆或主题" class="search-input" :style="{ '--n-border': 'none', '--n-border-hover': 'none', '--n-border-focus': 'none' , '--n-color': 'transparent', '--n-color-focus': 'transparent' }">
              <template #prefix><Search :size="14" /></template>
            </n-input>
            <div class="divider-vertical"></div>
            <button class="tool-btn" @click="focusGraph" title="重置中心"><Target :size="16" /></button>
            <button class="tool-btn" @click="zoomGraph('in')" title="放大"><ZoomIn :size="16" /></button>
            <button class="tool-btn" @click="zoomGraph('out')" title="缩小"><ZoomOut :size="16" /></button>
            <div class="divider-vertical"></div>
            <button class="tool-btn primary" @click="fetchGraph" title="刷新图谱"><RefreshCcw :size="16" :class="{ spinning: isLoading }" /></button>
          </div>
        </div>

        <!-- LEFT SIDE HUD -->
        <div class="left-hud">
          <div class="hud-panel glass filter-box">
            <div class="filter-row">
              <span class="filter-label">星域</span>
              <div class="pill-group">
                <button class="pill" :class="{ active: activeCluster === null }" @click="activeCluster = null">全部</button>
                <button v-for="topic in topicClusters" :key="topic.key" class="pill" :class="{ active: activeCluster === topic.key }" @click="activeCluster = activeCluster === topic.key ? null : topic.key">
                  {{ topic.label }} · {{ topic.count }}
                </button>
              </div>
            </div>
            <div class="filter-row">
              <span class="filter-label">活跃</span>
              <div class="pill-group">
                <button class="pill" :class="{ active: activityFilter === 'all' }" @click="activityFilter = 'all'">全部</button>
                <button class="pill" :class="{ active: activityFilter === 'active' }" @click="activityFilter = 'active'">高活跃</button>
                <button class="pill" :class="{ active: activityFilter === 'stable' }" @click="activityFilter = 'stable'">稳定</button>
                <button class="pill" :class="{ active: activityFilter === 'cool' }" @click="activityFilter = 'cool'">冷区</button>
              </div>
            </div>
            <div class="filter-row">
              <span class="filter-label">时间</span>
              <div class="pill-group">
                <button class="pill" :class="{ active: timeFilter === 'all' }" @click="timeFilter = 'all'">全部</button>
                <button class="pill" :class="{ active: timeFilter === '24h' }" @click="timeFilter = '24h'">24H</button>
                <button class="pill" :class="{ active: timeFilter === '7d' }" @click="timeFilter = '7d'">7D</button>
                <button class="pill" :class="{ active: timeFilter === '30d' }" @click="timeFilter = '30d'">30D</button>
                <button class="pill clear" @click="clearFilters">清空</button>
              </div>
            </div>
            <div class="filter-row">
              <span class="filter-label">类型</span>
              <div class="pill-group">
                <button class="pill" :class="{ active: typeFilter === 'all' }" @click="typeFilter = 'all'">全部</button>
                <button class="pill" :class="{ active: typeFilter === 'User' }" @click="typeFilter = 'User'">用户</button>
                <button class="pill" :class="{ active: typeFilter === 'Topic' }" @click="typeFilter = 'Topic'">主题</button>
                <button class="pill" :class="{ active: typeFilter === 'Fact' }" @click="typeFilter = 'Fact'">事实</button>
              </div>
            </div>
          </div>

          <div class="hud-panel glass replay-box">
            <div class="replay-header">
              <div class="replay-title"><Sparkles :size="14" /> 时间回放</div>
              <div class="replay-actions">
                <span>{{ replaySummary }}</span>
                <button class="pill" :class="{ active: replayPlaying }" @click="toggleReplayPlayback">{{ replayPlaying ? '暂停' : '执行' }}</button>
              </div>
            </div>
            <div class="replay-slider-wrap">
              <button class="tool-btn play-btn" :class="{ active: replayEnabled }" @click="toggleReplay" title="启用回放"><Play :size="14" /></button>
              <n-slider v-model:value="replayProgress" :disabled="!replayEnabled" :step="1" class="slider-fill" />
            </div>
          </div>

          <div class="hud-panel glass info-box">
            <div class="summary-list">
              <div class="summary-item"><span>视野</span><strong>{{ displayStats.nodes }}</strong></div>
              <div class="summary-item"><span>主题</span><strong>{{ displayStats.topics }}</strong></div>
              <div class="summary-item"><span>高活跃</span><strong>{{ displayStats.active }}</strong></div>
              <div class="summary-item"><span>新记录</span><strong>{{ displayStats.recent }}</strong></div>
            </div>
            <div class="divider-horizontal"></div>
            <div class="legend-list">
              <span><i class="legend-dot related"></i>关联链</span>
              <span><i class="legend-dot supersedes"></i>版本替代</span>
              <span><i class="legend-dot contradicts"></i>冲突关系</span>
            </div>
          </div>
        </div>

        <!-- THREE.JS SCENE CONTAINER -->
        <div class="galaxy-stage" ref="galaxyStageRef"></div>

        <div v-if="!isLoaded || isLoading" class="loading-overlay">
          <div class="loading-text">正在重构3D宇宙场景</div>
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
.insight-view, .insight-content { height: 100%; box-sizing: border-box; }
.insight-content { display: flex; gap: 16px; font-family: 'Inter', system-ui, sans-serif; }
.graph-area { 
  position: relative; flex: 1; min-width: 0; overflow: hidden; border-radius: 20px; 
  border: 1px solid var(--color-glass-border); 
  background: var(--color-background); 
  box-shadow: 0 12px 48px var(--color-primary-dim); 
}
.stars, .nebula, .galaxy-stage, .loading-overlay { position: absolute; inset: 0; pointer-events: none; }
.galaxy-stage { pointer-events: auto; }

/* Keep stars adaptive by using text color mixed with transparency */
.stars-a { opacity: 0.25; background-image: radial-gradient(circle at 14% 18%, color-mix(in srgb, var(--color-text) 80%, transparent) 0 1px, transparent 1.6px), radial-gradient(circle at 72% 24%, color-mix(in srgb,var(--graph-primary) 70%,white) 0 1.2px, transparent 1.8px), radial-gradient(circle at 36% 78%, color-mix(in srgb,var(--graph-core) 70%,white) 0 1px, transparent 1.8px); }
.stars-b { opacity: 0.15; background-image: radial-gradient(circle at 22% 84%, color-mix(in srgb, var(--color-text) 60%, transparent) 0 1px, transparent 1.6px), radial-gradient(circle at 84% 64%, color-mix(in srgb,var(--graph-fact) 72%,white) 0 1px, transparent 1.8px); }
.nebula { opacity: 0.22; filter: blur(36px); background: radial-gradient(circle at 18% 22%, color-mix(in srgb,var(--graph-primary) 22%,transparent), transparent 30%), radial-gradient(circle at 78% 28%, color-mix(in srgb,var(--graph-fact) 18%,transparent), transparent 30%), radial-gradient(circle at 52% 70%, color-mix(in srgb,var(--graph-core) 16%,transparent), transparent 34%); }

/* Responsive Glass using theme vars */
.glass { 
  border: 1px solid var(--color-glass-border); 
  background: var(--color-glass-bg); 
  backdrop-filter: blur(20px); 
  -webkit-backdrop-filter: blur(20px); 
}
.hud-panel { pointer-events: auto; border-radius: 16px; }

/* Top HUD for stats & actions */
.top-hud {
  position: absolute; top: 16px; left: 16px; right: 16px; z-index: 10;
  display: flex; justify-content: space-between; align-items: flex-start;
  pointer-events: none;
}
.stats-bar { 
  display: flex; gap: 12px; align-items: center; padding: 10px 18px; 
  color: var(--color-text); font-size: 13px; font-weight: 500;
  border-radius: 100px; /* pill shape */
}
.divider { width: 1px; height: 12px; background: var(--color-outline); }
.divider-vertical { width: 1px; height: 18px; background: var(--color-outline); margin: 0 4px; }
.divider-horizontal { height: 1px; width: 100%; background: var(--color-outline); margin: 12px 0; }

.action-bar {
  display: flex; gap: 6px; align-items: center; padding: 6px 8px;
  border-radius: 100px;
}
.search-input { width: 220px; font-family: 'Inter', system-ui, sans-serif; }

/* Left HUD for tools */
.left-hud {
  position: absolute; top: 80px; left: 16px; z-index: 10;
  width: 310px; display: flex; flex-direction: column; gap: 14px;
  pointer-events: none; max-height: calc(100% - 100px); overflow-y: auto;
}
.left-hud::-webkit-scrollbar { display: none; }

.filter-box { padding: 16px; display: flex; flex-direction: column; gap: 14px; }
.filter-row { display: flex; flex-direction: column; gap: 8px; }
.filter-label { font-size: 12px; color: var(--color-text-dim); font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; }
.pill-group { display: flex; gap: 6px; flex-wrap: wrap; }

.pill { 
  padding: 6px 12px; border-radius: 999px; font-size: 12px; font-weight: 500;
  color: var(--color-text); background: var(--color-outline);
  border: 1px solid transparent; cursor: pointer; transition: all 0.2s; 
}
.pill:hover { background: color-mix(in srgb, var(--color-outline) 80%, var(--color-primary-dim)); }
.pill.active { color: var(--color-text-inverse); background: var(--color-primary); box-shadow: 0 0 12px var(--color-primary-dim); }
.pill.clear { margin-left: auto; background: transparent; color: var(--color-text-dim); text-decoration: underline; padding: 6px; }
.pill.clear:hover { background: transparent; color: var(--color-text); }

.tool-btn { 
  width: 34px; height: 34px; border-radius: 50%; color: var(--color-text); 
  background: transparent; border: 0; cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; justify-content: center;
}
.tool-btn:hover { background: var(--color-outline); }
.tool-btn.primary { color: var(--color-primary); background: var(--color-primary-dim); }
.tool-btn.primary:hover { background: color-mix(in srgb, var(--color-primary-dim) 50%, var(--color-outline)); }
.tool-btn.active { background: var(--color-primary); color: var(--color-text-inverse); }

/* Replay box */
.replay-box { padding: 14px 16px; display: flex; flex-direction: column; gap: 12px; }
.replay-header { display: flex; justify-content: space-between; align-items: center; color: var(--color-text); font-size: 13px; font-weight: 500; }
.replay-title { display: flex; align-items: center; gap: 6px; color: var(--color-primary); font-weight: 600; }
.replay-actions { display: flex; align-items: center; gap: 10px; color: var(--color-text-dim); font-size: 12px; }
.replay-actions .pill { padding: 4px 10px; }
.replay-slider-wrap { display: flex; align-items: center; gap: 12px; }
.play-btn { flex-shrink: 0; }
.slider-fill { flex: 1; }

/* Info box (Summary + Legend) */
.info-box { padding: 16px; }
.summary-list { display: flex; flex-wrap: wrap; gap: 12px 18px; }
.summary-item { display: flex; flex-direction: column; gap: 4px; }
.summary-item span { font-size: 11px; color: var(--color-text-dim); text-transform: uppercase; font-weight: 600; }
.summary-item strong { font-size: 16px; color: var(--color-text); font-weight: 600; }

.legend-list { display: flex; flex-wrap: wrap; gap: 16px; font-size: 12px; color: var(--color-text-dim); font-weight: 500; }
.legend-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
.legend-dot.related { background: color-mix(in srgb,var(--graph-edge) 80%,white); }
.legend-dot.supersedes { background: var(--color-warning); }
.legend-dot.contradicts { background: var(--color-error); }

/* Detail panel */
.detail-panel { 
  width: 320px; border-radius: 20px; overflow: hidden; 
  border: 1px solid var(--color-glass-border); 
  background: var(--color-surface-elevated); 
  box-shadow: 0 12px 48px rgba(0,0,0,0.1); 
  display: flex; flex-direction: column;
}
.panel-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--color-outline); }
.panel-label { display: block; font-size: 11px; letter-spacing: 0.16em; text-transform: uppercase; color: var(--color-text-dim); }
.panel-title { margin: 6px 0 0; color: var(--color-text); font-weight: 600; }
.close-btn { width: 32px; height: 32px; border-radius: 50%; color: var(--color-text-dim); background: transparent; display: flex; align-items: center; justify-content: center; border: 0; cursor: pointer; transition: all 0.2s; }
.close-btn:hover { background: var(--color-outline); color: var(--color-text); }
.panel-body { height: calc(100% - 74px); }
.panel-content { padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.tag-row { display: flex; gap: 8px; flex-wrap: wrap; }
.content-card, .mini-card, .time-card { padding: 14px; border-radius: 16px; border: 1px solid var(--color-outline); background: var(--color-surface); }
.node-title { margin: 0; color: var(--color-text); font-size: 16px; line-height: 1.55; font-weight: 600; }
.node-meta { margin: 6px 0 0; color: var(--color-text-dim); font-size: 13px; font-weight: 500; }
.metric-row { display: flex; justify-content: space-between; color: var(--color-text); font-size: 13px; font-weight: 500; }
.mini-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.mini-card span { display: block; color: var(--color-text-dim); font-size: 12px; font-weight: 600; text-transform: uppercase; }
.mini-card strong { display: block; margin-top: 6px; color: var(--color-text); font-size: 16px; }
.time-card { display: flex; gap: 12px; align-items: center; color: var(--color-text); }
.time-main { font-size: 14px; font-weight: 600; }
.time-sub { font-size: 12px; color: var(--color-text-dim); margin-top: 2px; }
.panel-actions { display: flex; gap: 10px; align-items: center; }

.spinning { animation: spin 1.2s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.loading-overlay { z-index: 5; display: flex; align-items: center; justify-content: center; background: rgba(5,8,14,0.44); pointer-events: auto; }
.loading-text { color: #fff; letter-spacing: 0.16em; }

.panel-enter-active, .panel-leave-active { transition: transform 0.24s ease, opacity 0.24s ease; }
.panel-enter-from, .panel-leave-to { opacity: 0; transform: translateX(12px); }

/* Global styles for CSS2D labels injected into DOM */
:global(.node-label) {
  font-size: 11px;
  color: var(--color-text-dim);
  text-shadow: 0 1px 4px var(--color-background);
  white-space: nowrap;
  pointer-events: auto;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}
:global(.node-label:hover) {
  color: var(--color-text) !important;
  transform: scale(1.1);
}

@media (max-width: 1100px) {
  .insight-content { flex-direction: column; }
  .detail-panel { width: 100%; min-height: 320px; }
  .left-hud { 
    position: static; 
    width: 100%; 
    max-height: none; 
    display: grid; 
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); 
    margin-bottom: 16px; 
    overflow-y: visible;
  }
}
</style>
