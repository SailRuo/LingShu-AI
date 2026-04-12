<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let animationFrameId: number | null = null
let resizeTimer: number | null = null

const mistColor = computed(() => {
  return themeStore.current.isDark ? '255, 255, 255' : '100, 120, 140'
})

interface Cloud {
  x: number
  y: number
  w: number
  h: number
  vx: number
  opacity: number
}

let width = 0
let height = 0
let clouds: Cloud[] = []

const random = (min: number, max: number) => Math.random() * (max - min) + min

const createClouds = () => {
  const count = width < 768 ? 10 : 20
  clouds = []
  for (let i = 0; i < count; i++) {
    clouds.push({
      x: random(-width, width),
      y: random(0, height),
      w: random(200, 600),
      h: random(100, 300),
      vx: random(0.1, 0.5),
      opacity: random(0.02, 0.08)
    })
  }
}

const updateSize = () => {
  if (!canvasRef.value) return
  width = window.innerWidth
  height = window.innerHeight
  canvasRef.value.width = width
  canvasRef.value.height = height
  createClouds()
}

const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(updateSize, 150)
}

const draw = () => {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  
  const rgb = mistColor.value

  clouds.forEach(c => {
    c.x += c.vx
    if (c.x > width + c.w) c.x = -c.w

    ctx!.beginPath()
    const gradient = ctx!.createRadialGradient(
      c.x + c.w / 2, c.y + c.h / 2, 0,
      c.x + c.w / 2, c.y + c.h / 2, c.w / 2
    )
    gradient.addColorStop(0, `rgba(${rgb}, ${c.opacity})`)
    gradient.addColorStop(1, `rgba(${rgb}, 0)`)
    
    ctx!.fillStyle = gradient
    // Draw an ellipse
    ctx!.save()
    ctx!.translate(c.x + c.w / 2, c.y + c.h / 2)
    ctx!.scale(1, c.h / c.w)
    ctx!.arc(0, 0, c.w / 2, 0, Math.PI * 2)
    ctx!.restore()
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
  <canvas ref="canvasRef" class="mist-canvas"></canvas>
</template>

<style scoped>
.mist-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  z-index: 0;
  filter: blur(30px);
}
</style>
