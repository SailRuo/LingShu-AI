<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let animationFrameId: number | null = null
let resizeTimer: number | null = null

// 根据主题决定雨滴基础颜色 (RGB格式)
const dropColorRgb = computed(() => themeStore.current.isDark ? '255, 255, 255' : '71, 85, 105')

// Utilities
const random = (min: number, max: number) => Math.random() * (max - min) + min

interface Drop {
  x: number
  y: number
  z: number // 0 (near) to 1 (far)
  speed: number
  length: number
  opacity: number
  groundY: number
}

interface Particle {
  x: number
  y: number
  z: number
  vx: number
  vy: number
  life: number
  opacity: number
  size: number
}

interface Ripple {
  x: number
  y: number
  z: number
  radius: number
  maxRadius: number
  speed: number
  opacity: number
}

let drops: Drop[] = []
let particles: Particle[] = []
let ripples: Ripple[] = []

let width = 0
let height = 0

const createDrops = () => {
  const density = width < 768 ? 80 : 150
  const horizon = height * 0.65 
  drops = []
  for (let i = 0; i < density; i++) {
    const z = random(0.1, 1) 
    const groundY = horizon + (1 - z) * (height - horizon) + random(-10, 10)
    
    drops.push({
      x: random(0, width),
      y: random(-height, groundY),
      z,
      speed: (15 + random(0, 12)) * (1.3 - z), 
      length: (20 + random(0, 20)) * (1.3 - z),
      opacity: random(0.1, 0.5) * (1.2 - z),
      groundY
    })
  }
}

const createSplash = (x: number, y: number, z: number) => {
  const count = Math.floor(random(3, 6))
  const scale = 1.3 - z
  
  // Create particles (溅开)
  for (let i = 0; i < count; i++) {
    particles.push({
      x,
      y,
      z,
      vx: random(-1.5, 1.5) * scale,
      vy: random(-2.5, -0.5) * scale, // 降低溅起高度
      life: 1.0,
      opacity: 0.7 * scale,
      size: random(0.6, 1.2) * scale // 减小水珠尺寸
    })
  }

  // Also create a small ripple (涟漪)
  if (ripples.length < 200) {
    ripples.push({
      x,
      y,
      z,
      radius: 0,
      maxRadius: random(10, 25) * scale,
      speed: random(0.2, 0.5) * scale,
      opacity: 0.4 * scale
    })
  }
}

const updateSize = () => {
  if (!canvasRef.value) return
  width = window.innerWidth
  height = window.innerHeight
  canvasRef.value.width = width
  canvasRef.value.height = height
  createDrops()
}

const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(updateSize, 150)
}

const updateAndDraw = () => {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)

  const rgb = dropColorRgb.value
  const horizon = height * 0.65

  // 1. 地面积水反光
  const groundGradient = ctx.createLinearGradient(0, horizon, 0, height)
  groundGradient.addColorStop(0, `rgba(${rgb}, 0)`)
  groundGradient.addColorStop(1, `rgba(${rgb}, 0.05)`)
  ctx.fillStyle = groundGradient
  ctx.fillRect(0, horizon, width, height - horizon)

  // 2. 雨滴
  for (let i = 0; i < drops.length; i++) {
    const drop = drops[i]
    ctx.beginPath()
    const gradient = ctx.createLinearGradient(drop.x, drop.y, drop.x, drop.y + drop.length)
    gradient.addColorStop(0, `rgba(${rgb}, 0)`)
    gradient.addColorStop(1, `rgba(${rgb}, ${drop.opacity})`)
    ctx.strokeStyle = gradient
    ctx.lineWidth = Math.max(0.6, 1.5 * (1.1 - drop.z))
    ctx.moveTo(drop.x, drop.y)
    ctx.lineTo(drop.x, drop.y + drop.length)
    ctx.stroke()

    drop.y += drop.speed

    if (drop.y > drop.groundY) {
      createSplash(drop.x, drop.groundY, drop.z)
      drop.y = random(-200, -50)
      drop.x = random(0, width)
      drop.groundY = horizon + (1 - drop.z) * (height - horizon) + random(-10, 10)
    }
  }

  // 3. 溅起的水花 (Particles)
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i]
    ctx.fillStyle = `rgba(${rgb}, ${p.opacity * p.life})`
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
    ctx.fill()

    p.x += p.vx
    p.y += p.vy
    p.vy += 0.25 // 增加重力感，让水珠更快落地
    p.life -= 0.05 // 缩短生命周期，让效果更干脆

    if (p.life <= 0) {
      particles.splice(i, 1)
    }
  }

  // 4. 涟漪 (Ripples)
  for (let i = ripples.length - 1; i >= 0; i--) {
    const r = ripples[i]
    ctx.beginPath()
    ctx.ellipse(r.x, r.y, r.radius, r.radius * 0.2, 0, 0, Math.PI * 2)
    ctx.strokeStyle = `rgba(${rgb}, ${r.opacity})`
    ctx.lineWidth = Math.max(0.3, 0.6 * (1 - r.z))
    ctx.stroke()

    r.radius += r.speed
    r.opacity -= 0.008

    if (r.opacity <= 0) {
      ripples.splice(i, 1)
    }
  }
}

const animate = () => {
  updateAndDraw()
  animationFrameId = requestAnimationFrame(animate)
}

onMounted(() => {
  if (canvasRef.value) {
    ctx = canvasRef.value.getContext('2d')
    updateSize()
    window.addEventListener('resize', handleResize)
    animate()
  }
})

onBeforeUnmount(() => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId)
  if (resizeTimer) clearTimeout(resizeTimer)
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <canvas ref="canvasRef" class="rain-canvas"></canvas>
</template>

<style scoped>
.rain-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  z-index: 0;
}
</style>
