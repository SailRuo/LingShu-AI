<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let animationFrameId: number | null = null
let resizeTimer: number | null = null

// Colors for Aurora
const auroraColors = computed(() => {
  if (themeStore.current.isDark) {
    return [
      'rgba(0, 255, 150, 0.2)',
      'rgba(0, 200, 255, 0.15)',
      'rgba(150, 0, 255, 0.1)'
    ]
  } else {
    return [
      'rgba(0, 255, 180, 0.1)',
      'rgba(100, 200, 255, 0.08)',
      'rgba(200, 150, 255, 0.05)'
    ]
  }
})

let width = 0
let height = 0
let time = 0

const updateSize = () => {
  if (!canvasRef.value) return
  width = window.innerWidth
  height = window.innerHeight
  canvasRef.value.width = width
  canvasRef.value.height = height
}

const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(updateSize, 150)
}

const drawAurora = () => {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  
  time += 0.002
  const colors = auroraColors.value

  for (let i = 0; i < colors.length; i++) {
    const color = colors[i]
    ctx.beginPath()
    ctx.fillStyle = color
    
    // Create a flowing wave shape
    ctx.moveTo(0, height)
    for (let x = 0; x <= width; x += 20) {
      const noise = Math.sin(x * 0.001 + time + i) * 50 + 
                    Math.cos(x * 0.002 - time * 0.5 + i) * 30
      const y = height * (0.3 + i * 0.15) + noise
      ctx.lineTo(x, y)
    }
    
    ctx.lineTo(width, height)
    ctx.fill()
    
    // Add a glow effect using shadow
    ctx.shadowBlur = 40
    ctx.shadowColor = color
  }
  
  // Reset shadow for performance
  ctx.shadowBlur = 0
}

const animate = () => {
  drawAurora()
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
  <canvas ref="canvasRef" class="aurora-canvas"></canvas>
</template>

<style scoped>
.aurora-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  z-index: 0;
  filter: blur(40px); /* Smooth out the gradients */
}
</style>
