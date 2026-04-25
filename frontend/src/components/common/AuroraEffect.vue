<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()
const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let animationFrameId: number | null = null
let resizeTimer: number | null = null

// Advanced Aurora Config
const ribbons = ref([
  { phase: 0, offset: 0, speed: 0.008, color: '', alpha: 0.25, width: 300 },
  { phase: 1.5, offset: 100, speed: 0.006, color: '', alpha: 0.2, width: 400 },
  { phase: 3.0, offset: -50, speed: 0.004, color: '', alpha: 0.15, width: 500 },
  { phase: 4.5, offset: 200, speed: 0.007, color: '', alpha: 0.2, width: 350 }
])

const updateColors = () => {
  if (themeStore.current.isDark) {
    ribbons.value[0].color = '0, 255, 180' // Cyan/Green
    ribbons.value[1].color = '0, 160, 255' // Blue
    ribbons.value[2].color = '160, 0, 255' // Purple
    ribbons.value[3].color = '0, 255, 100' // Lime
  } else {
    ribbons.value[0].color = '100, 255, 200' 
    ribbons.value[1].color = '150, 200, 255'
    ribbons.value[2].color = '200, 150, 255'
    ribbons.value[3].color = '150, 255, 180'
  }
}

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
  
  // Use blending for glow effect
  ctx.globalCompositeOperation = 'screen'
  
  time += 0.015 // Increased time step

  ribbons.value.forEach((ribbon, i) => {
    const { speed, color, alpha, offset, width: ribbonWidth } = ribbon
    const currentPhase = time * speed * 60 + i * 2
    
    // Dynamic vertical shift (breathing effect)
    const verticalShift = Math.sin(time * 0.5 + i) * 30
    
    ctx!.beginPath()
    
    const grad = ctx!.createLinearGradient(0, height * 0.1 + offset + verticalShift, 0, height * 0.9 + offset + verticalShift)
    grad.addColorStop(0, `rgba(${color}, 0)`)
    grad.addColorStop(0.2, `rgba(${color}, ${alpha})`)
    grad.addColorStop(0.5, `rgba(${color}, ${alpha * 0.4})`)
    grad.addColorStop(0.8, `rgba(${color}, 0)`)
    
    ctx!.fillStyle = grad
    
    // Draw ribbon shape
    ctx!.moveTo(0, height)
    
    // Top boundary - Increased amplitude
    for (let x = 0; x <= width; x += 30) {
      const y = height * 0.3 + offset + verticalShift + 
                Math.sin(x * 0.0008 + currentPhase) * 180 + 
                Math.cos(x * 0.0015 - currentPhase * 0.6) * 80
      ctx!.lineTo(x, y)
    }
    
    // Bottom boundary - Increased amplitude
    for (let x = width; x >= 0; x -= 30) {
      const y = height * 0.3 + offset + verticalShift + ribbonWidth + 
                Math.sin(x * 0.001 + currentPhase * 0.8) * 200 + 
                Math.cos(x * 0.0016 - currentPhase * 0.4) * 100
      ctx!.lineTo(x, y)
    }
    
    ctx!.closePath()
    ctx!.fill()
    
    // Add vertical streaks (more dynamic)
    if (i === 0) {
        ctx!.save()
        for (let j = 0; j < 12; j++) {
            const sx = (time * 80 + j * (width / 10)) % width
            const sAlpha = (Math.sin(time + j) + 1) * 0.03 // Flickering streaks
            ctx!.fillStyle = `rgba(${color}, ${sAlpha})`
            ctx!.fillRect(sx, height * 0.1, 3, height * 0.7)
        }
        ctx!.restore()
    }
  })
}

const animate = () => {
  drawAurora()
  animationFrameId = requestAnimationFrame(animate)
}

watch(() => themeStore.current.isDark, updateColors, { immediate: true })

onMounted(() => {
  if (canvasRef.value) {
    ctx = canvasRef.value.getContext('2d')
    updateColors()
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
  filter: blur(60px); /* Increased blur for more ethereal feel */
  opacity: 0.8;
  background: radial-gradient(circle at 50% -20%, rgba(0, 40, 80, 0.3), transparent 70%);
}
</style>
