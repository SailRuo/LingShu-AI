<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let animationFrameId: number | null = null
let resizeTimer: number | null = null

const waveColorRgb = computed(() => {
  return themeStore.current.isDark ? '30, 58, 138' : '186, 230, 253'
})

const foamColorRgb = computed(() => {
  return themeStore.current.isDark ? '147, 197, 253' : '255, 255, 255'
})

interface RollingWave {
  progress: number // 0 to 1
  speed: number
  seed: number
}

let width = 0
let height = 0
let waves: RollingWave[] = []
let frame = 0

const initWaves = () => {
  waves = [
    { progress: 0.1, speed: 0.004, seed: Math.random() },
    { progress: 0.4, speed: 0.004, seed: Math.random() },
    { progress: 0.7, speed: 0.004, seed: Math.random() }
  ]
}

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

const random = (min: number, max: number) => Math.random() * (max - min) + min

const drawWave = (wave: RollingWave) => {
  if (!ctx) return
  
  const rgb = waveColorRgb.value
  const foamRgb = foamColorRgb.value
  const horizon = height * 0.6
  
  // Perspective mapping: progress 0 is at horizon, 1 is at bottom
  // Use a power function to make it "rush" faster as it approaches
  const p = Math.pow(wave.progress, 2)
  const currentY = horizon + p * (height - horizon)
  
  // Scale and opacity based on proximity
  const scale = 0.1 + p * 2.5
  const opacity = Math.sin(Math.PI * wave.progress) * 0.5
  
  // Draw the wave curve
  ctx.beginPath()
  
  const segments = 40
  const segmentWidth = width / segments
  const centerX = width / 2
  
  for (let i = 0; i <= segments; i++) {
    const x = i * segmentWidth
    // Waves curve slightly downward at the edges to create a "surrounding" feel
    const distFromCenter = Math.abs(x - centerX) / centerX
    const curvature = distFromCenter * distFromCenter * 40 * p
    
    // Dynamic ripple
    const ripple = Math.sin(i * 0.5 + frame * 0.03 + wave.seed * 20) * 10 * scale
    
    const y = currentY + curvature + ripple
    
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  
  // Close the shape to the bottom
  ctx.lineTo(width, height)
  ctx.lineTo(0, height)
  ctx.closePath()
  
  // Fill with gradient
  const gradient = ctx.createLinearGradient(0, currentY - 50 * p, 0, currentY + 150 * p)
  gradient.addColorStop(0, `rgba(${rgb}, ${opacity})`)
  gradient.addColorStop(0.4, `rgba(${rgb}, ${opacity * 0.5})`)
  gradient.addColorStop(1, `rgba(${rgb}, 0)`)
  ctx.fillStyle = gradient
  ctx.fill()

  // Foam Edge
  ctx.beginPath()
  ctx.lineWidth = Math.max(1, 4 * p)
  ctx.strokeStyle = `rgba(${foamRgb}, ${opacity * 1.2})`
  ctx.lineCap = 'round'
  
  for (let i = 0; i <= segments; i++) {
    const x = i * segmentWidth
    const distFromCenter = Math.abs(x - centerX) / centerX
    const curvature = distFromCenter * distFromCenter * 40 * p
    const ripple = Math.sin(i * 0.5 + frame * 0.03 + wave.seed * 20) * 10 * scale
    const y = currentY + curvature + ripple
    
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.stroke()
  
  // Spray particles (only when wave is closer)
  if (wave.progress > 0.5) {
    const sprayOpacity = (wave.progress - 0.5) * 2 * opacity
    ctx.fillStyle = `rgba(${foamRgb}, ${sprayOpacity})`
    for (let j = 0; j < 8; j++) {
      const sx = ((wave.seed * width) + (j * (width / 8)) + frame * 0.5) % width
      const distFromCenter = Math.abs(sx - centerX) / centerX
      const curvature = distFromCenter * distFromCenter * 40 * p
      const sy = currentY + curvature + Math.sin(sx * 0.02) * 5
      
      ctx.beginPath()
      ctx.arc(sx, sy - random(0, 20) * p, random(1, 3) * p, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

const updateAndDraw = () => {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  
  frame++
  
  // Update waves
  waves.forEach(wave => {
    wave.progress += wave.speed
    if (wave.progress > 1) {
      wave.progress = 0
      wave.seed = Math.random()
    }
  })
  
  // Sort and draw
  const sortedWaves = [...waves].sort((a, b) => a.progress - b.progress)
  sortedWaves.forEach(drawWave)
}

const animate = () => {
  updateAndDraw()
  animationFrameId = requestAnimationFrame(animate)
}

onMounted(() => {
  if (canvasRef.value) {
    ctx = canvasRef.value.getContext('2d')
    updateSize()
    initWaves()
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
  <canvas ref="canvasRef" class="wave-canvas"></canvas>
</template>

<style scoped>
.wave-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  z-index: 0;
}
</style>
