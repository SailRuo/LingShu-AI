<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let animationFrameId: number | null = null
let resizeTimer: number | null = null

const fireflyColor = computed(() => {
  return themeStore.current.isDark ? '255, 230, 100' : '200, 150, 50'
})

interface Firefly {
  x: number
  y: number
  s: number // size
  ang: number // angle
  v: number // velocity
  opacity: number
  opacityDir: number
}

let width = 0
let height = 0
let fireflies: Firefly[] = []

const random = (min: number, max: number) => Math.random() * (max - min) + min

const createFireflies = () => {
  const count = width < 768 ? 30 : 60
  fireflies = []
  for (let i = 0; i < count; i++) {
    fireflies.push({
      x: random(0, width),
      y: random(0, height),
      s: random(1, 3),
      ang: random(0, Math.PI * 2),
      v: random(0.2, 0.6),
      opacity: random(0.1, 0.8),
      opacityDir: random(0, 1) > 0.5 ? 0.01 : -0.01
    })
  }
}

const updateSize = () => {
  if (!canvasRef.value) return
  width = window.innerWidth
  height = window.innerHeight
  canvasRef.value.width = width
  canvasRef.value.height = height
  createFireflies()
}

const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(updateSize, 150)
}

const draw = () => {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  
  const rgb = fireflyColor.value

  fireflies.forEach(f => {
    // Update position
    f.x += Math.cos(f.ang) * f.v
    f.y += Math.sin(f.ang) * f.v
    f.ang += random(-0.1, 0.1)

    // Update opacity (flicker)
    f.opacity += f.opacityDir
    if (f.opacity >= 0.8 || f.opacity <= 0.1) f.opacityDir *= -1

    // Bounds check
    if (f.x < 0) f.x = width
    if (f.x > width) f.x = 0
    if (f.y < 0) f.y = height
    if (f.y > height) f.y = 0

    // Draw
    ctx!.beginPath()
    const gradient = ctx!.createRadialGradient(f.x, f.y, 0, f.x, f.y, f.s * 4)
    gradient.addColorStop(0, `rgba(${rgb}, ${f.opacity})`)
    gradient.addColorStop(1, `rgba(${rgb}, 0)`)
    
    ctx!.fillStyle = gradient
    ctx!.arc(f.x, f.y, f.s * 4, 0, Math.PI * 2)
    ctx!.fill()
  })
}

const animate = () => {
  draw()
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
  <canvas ref="canvasRef" class="firefly-canvas"></canvas>
</template>

<style scoped>
.firefly-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  z-index: 0;
}
</style>
