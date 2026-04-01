<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useThemeStore } from '@/stores/themeStore'

const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let animationFrameId: number | null = null
let resizeTimer: number | null = null

const themeStore = useThemeStore()

// Utilities
const random = (min: number, max: number) => Math.random() * (max - min) + min
const STAR_DRIFT_ANGLE = 18
const STAR_DRIFT_RAD = (STAR_DRIFT_ANGLE * Math.PI) / 180

interface Star {
  x: number
  y: number
  size: number
  baseOpacity: number
  opacityPhase: number
  opacitySpeed: number
  driftX: number
  driftY: number
  driftEnabled: boolean
}

interface Meteor {
  x: number
  y: number
  startX: number
  startY: number
  targetX: number
  targetY: number
  progress: number
  speed: number // Progress per frame
  angle: number
  size: number
  trailLength: number
  trailWidth: number
  brightness: number
  endScale: number
  trajectory: 'glide' | 'vanish'
  delayTimer: number
}

let stars: Star[] = []
let meteors: Meteor[] = []

let width = 0
let height = 0

// Geometry utilities for meteor spawning
const pickStartFromPerimeter = (w: number, h: number) => {
  const perimeterWeight = w * 2 + h * 2
  const hit = random(0, perimeterWeight)
  const cornerSafe = 0.12

  if (hit < w) {
    return { edge: 'top', startX: random(cornerSafe, 1 - cornerSafe) * w, startY: random(-0.12, 0.04) * h }
  }
  if (hit < w + h) {
    return { edge: 'right', startX: random(0.96, 1.12) * w, startY: random(cornerSafe, 1 - cornerSafe) * h }
  }
  if (hit < w * 2 + h) {
    return { edge: 'bottom', startX: random(cornerSafe, 1 - cornerSafe) * w, startY: random(0.96, 1.12) * h }
  }
  return { edge: 'left', startX: random(-0.12, 0.04) * w, startY: random(cornerSafe, 1 - cornerSafe) * h }
}

const inwardDirection = (edge: string) => {
  if (edge === 'top') return { x: 0, y: 1 }
  if (edge === 'right') return { x: -1, y: 0 }
  if (edge === 'bottom') return { x: 0, y: -1 }
  return { x: 1, y: 0 }
}

const rotate2D = (x: number, y: number, degrees: number) => {
  const rad = (degrees * Math.PI) / 180
  const cos = Math.cos(rad)
  const sin = Math.sin(rad)
  return { x: x * cos - y * sin, y: x * sin + y * cos }
}

const pickSeparatedStart = (w: number, h: number, usedStarts: Array<{ x: number; y: number }>) => {
  if (usedStarts.length === 0) return pickStartFromPerimeter(w, h)
  let best = pickStartFromPerimeter(w, h)
  let bestMinDistance = -1
  for (let i = 0; i < 14; i++) {
    const candidate = pickStartFromPerimeter(w, h)
    let minDistance = Number.POSITIVE_INFINITY
    for (const used of usedStarts) {
      const d = Math.hypot(candidate.startX - used.x, candidate.startY - used.y)
      if (d < minDistance) minDistance = d
    }
    if (minDistance > bestMinDistance) {
      best = candidate
      bestMinDistance = minDistance
    }
  }
  return best
}

// Generation
const generateStars = () => {
  stars = []
  const areaRatio = (width * height) / (1920 * 1080)
  const starCount = Math.floor(230 * Math.max(0.9, Math.min(2.6, areaRatio)))

  for (let i = 0; i < starCount; i++) {
    const bucket = Math.random()
    const isMicro = bucket < 0.72
    const isMedium = bucket >= 0.72 && bucket < 0.94
    const driftSpeedMultiplier = random(0.01, 0.05)
    const driftEnabled = Math.random() < 0.05

    stars.push({
      x: random(0, width),
      y: random(0, height),
      size: isMicro ? random(0.55, 1.2) : isMedium ? random(1.15, 2) : random(2, 3.2),
      baseOpacity: isMicro ? random(0.2, 0.6) : random(0.45, 0.95),
      opacityPhase: random(0, Math.PI * 2),
      opacitySpeed: isMicro ? random(0.005, 0.01) : random(0.01, 0.02),
      driftX: Math.cos(STAR_DRIFT_RAD) * driftSpeedMultiplier,
      driftY: Math.sin(STAR_DRIFT_RAD) * driftSpeedMultiplier,
      driftEnabled
    })
  }
}

const createMeteor = (usedStarts: Array<{ x: number; y: number }>, forceVanish = false): Meteor => {
  const centerX = width * 0.5
  const centerY = height * 0.46
  const trajectory = forceVanish ? 'vanish' : 'glide'
  const start = pickSeparatedStart(width, height, usedStarts)

  let targetX = centerX + random(-width * 0.12, width * 0.12)
  let targetY = centerY + random(-height * 0.1, height * 0.1)

  if (trajectory === 'glide') {
    const normal = inwardDirection(start.edge)
    const offset = random(-38, 38)
    const direction = rotate2D(normal.x, normal.y, offset)
    const distance = random(Math.min(width, height) * 0.24, Math.min(width, height) * 0.58)
    targetX = start.startX + direction.x * distance
    targetY = start.startY + direction.y * distance
  }

  const distanceX = targetX - start.startX
  const distanceY = targetY - start.startY
  const angle = Math.atan2(distanceY, distanceX)

  // 极大降低流星速度。之前的动画周期是 22~38 秒。
  // 在 60FPS 下，如果动画持续 30 秒，总帧数是 1800。每帧的 progress 约为 1/1800 ≈ 0.00055。
  const durationSeconds = trajectory === 'vanish' ? random(26, 38) : random(22, 34)
  const frames = durationSeconds * 60
  const speed = 1 / frames

  return {
    x: start.startX,
    y: start.startY,
    startX: start.startX,
    startY: start.startY,
    targetX,
    targetY,
    progress: 0,
    speed,
    angle,
    size: trajectory === 'vanish' ? random(1.8, 3) : random(2, 3.6),
    trailLength: trajectory === 'vanish' ? random(90, 150) : random(130, 210),
    trailWidth: random(1.1, 2.1),
    brightness: trajectory === 'vanish' ? random(0.68, 0.9) : random(0.8, 1),
    endScale: trajectory === 'vanish' ? random(0.22, 0.48) : random(0.82, 1.05),
    trajectory,
    delayTimer: random(300, 1800) // 延迟 5 到 30 秒
  }
}

const generateMeteors = () => {
  meteors = []
  const numMeteors = 2
  const usedStarts: Array<{ x: number; y: number }> = []
  const vanishIndex = Math.random() < 0.45 ? Math.floor(random(0, numMeteors)) : -1

  for (let i = 0; i < numMeteors; i++) {
    const m = createMeteor(usedStarts, i === vanishIndex)
    usedStarts.push({ x: m.startX, y: m.startY })
    meteors.push(m)
  }
}

// Rendering
const drawStar = (star: Star, isDark: boolean) => {
  if (!ctx) return

  if (star.driftEnabled) {
    star.x += star.driftX
    star.y += star.driftY
    if (star.x > width) star.x = 0
    if (star.y > height) star.y = 0
  }

  star.opacityPhase += star.opacitySpeed
  const currentOpacity = star.baseOpacity * (0.5 + 0.5 * Math.sin(star.opacityPhase))

  const rgb = isDark ? '255, 255, 255' : '0, 50, 150'
  const alpha = isDark ? currentOpacity : currentOpacity * 0.7

  ctx.beginPath()
  ctx.arc(star.x, star.y, star.size / 2, 0, Math.PI * 2)
  ctx.fillStyle = `rgba(${rgb}, ${alpha})`
  // 移除耗费 GPU 的 shadowBlur
  ctx.fill()
}

const drawMeteor = (meteor: Meteor, isDark: boolean) => {
  if (!ctx) return

  if (meteor.delayTimer > 0) {
    meteor.delayTimer--
    return
  }

  meteor.progress += meteor.speed
  if (meteor.progress >= 1) {
    Object.assign(meteor, createMeteor([]))
    return
  }

  const easeProgress = Math.pow(meteor.progress, 1.2)

  meteor.x = meteor.startX + (meteor.targetX - meteor.startX) * easeProgress
  meteor.y = meteor.startY + (meteor.targetY - meteor.startY) * easeProgress

  let opacity = 0
  let currentScale = 1

  if (easeProgress < 0.1) {
    opacity = easeProgress / 0.1 * meteor.brightness
  } else if (easeProgress > 0.8) {
    opacity = (1 - easeProgress) / 0.2 * meteor.brightness
    currentScale = meteor.trajectory === 'vanish'
      ? 1 - ((easeProgress - 0.8) / 0.2) * (1 - meteor.endScale)
      : meteor.endScale
  } else {
    opacity = meteor.trajectory === 'vanish' ? meteor.brightness * 0.7 : meteor.brightness
    currentScale = meteor.trajectory === 'vanish' ? 0.7 : 1
  }

  if (opacity <= 0) return

  ctx.save()
  ctx.translate(meteor.x, meteor.y)
  ctx.rotate(meteor.angle)
  ctx.scale(currentScale, currentScale)

  // Draw Trail
  const gradient = ctx.createLinearGradient(-meteor.trailLength, 0, 0, 0)
  if (isDark) {
    gradient.addColorStop(0, 'rgba(120, 190, 255, 0)')
    gradient.addColorStop(0.5, 'rgba(180, 225, 255, 0.25)')
    gradient.addColorStop(1, `rgba(255, 255, 255, ${opacity})`)
  } else {
    gradient.addColorStop(0, 'rgba(0, 100, 255, 0)')
    gradient.addColorStop(0.5, 'rgba(0, 150, 255, 0.15)')
    gradient.addColorStop(1, `rgba(0, 50, 200, ${opacity * 0.8})`)
  }

  ctx.beginPath()
  ctx.moveTo(0, -meteor.trailWidth / 2)
  ctx.lineTo(-meteor.trailLength, 0)
  ctx.lineTo(0, meteor.trailWidth / 2)
  ctx.closePath()
  ctx.fillStyle = gradient
  ctx.fill()

  // Draw Head with RadialGradient instead of expensive shadowBlur
  const headRadius = meteor.size * 1.5
  const headGradient = ctx.createRadialGradient(0, 0, 0, 0, 0, headRadius)
  if (isDark) {
    headGradient.addColorStop(0, `rgba(255, 255, 255, ${opacity})`)
    headGradient.addColorStop(0.4, `rgba(255, 255, 255, ${opacity * 0.8})`)
    headGradient.addColorStop(1, `rgba(117, 188, 255, 0)`)
  } else {
    headGradient.addColorStop(0, `rgba(255, 255, 255, ${opacity})`)
    headGradient.addColorStop(0.4, `rgba(0, 50, 200, ${opacity * 0.8})`)
    headGradient.addColorStop(1, `rgba(0, 100, 255, 0)`)
  }

  ctx.beginPath()
  ctx.arc(0, 0, headRadius, 0, Math.PI * 2)
  ctx.fillStyle = headGradient
  ctx.fill()

  ctx.restore()
}

const render = () => {
  if (!ctx || !canvasRef.value) return

  // Clear canvas efficiently
  ctx.clearRect(0, 0, width, height)

  const isDark = themeStore.current.isDark

  for (const star of stars) {
    drawStar(star, isDark)
  }

  // 移除 globalCompositeOperation = 'lighter'，因为它会极大地增加渲染开销
  for (const meteor of meteors) {
    drawMeteor(meteor, isDark)
  }

  animationFrameId = requestAnimationFrame(render)
}

const handleResize = () => {
  if (resizeTimer) window.clearTimeout(resizeTimer)

  resizeTimer = window.setTimeout(() => {
    if (canvasRef.value) {
      width = window.innerWidth
      height = window.innerHeight
      const dpr = window.devicePixelRatio || 1
      canvasRef.value.width = width * dpr
      canvasRef.value.height = height * dpr
      ctx?.scale(dpr, dpr)
      canvasRef.value.style.width = `${width}px`
      canvasRef.value.style.height = `${height}px`

      generateStars()
      generateMeteors()
    }
  }, 150)
}

onMounted(() => {
  if (canvasRef.value) {
    ctx = canvasRef.value.getContext('2d', { alpha: true, desynchronized: true })
    if (ctx) {
      width = window.innerWidth
      height = window.innerHeight
      const dpr = window.devicePixelRatio || 1
      canvasRef.value.width = width * dpr
      canvasRef.value.height = height * dpr
      ctx.scale(dpr, dpr)
      canvasRef.value.style.width = `${width}px`
      canvasRef.value.style.height = `${height}px`

      generateStars()
      generateMeteors()
      render()
    }
  }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) window.clearTimeout(resizeTimer)
  if (animationFrameId) cancelAnimationFrame(animationFrameId)
})
</script>

<template>
  <canvas
    ref="canvasRef"
    class="star-field-canvas"
    aria-hidden="true"
  ></canvas>
</template>

<style scoped>
.star-field-canvas {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
}

@media (prefers-reduced-motion: reduce) {
  .star-field-canvas {
    display: none !important;
  }
}
</style>
