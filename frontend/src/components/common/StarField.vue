<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

interface Star {
  id: number
  x: number
  y: number
  size: number
  delay: number
  duration: number
  opacity: number
  blur: number
  driftDuration: number
  driftX: number
  driftY: number
  driftEnabled: boolean
}

interface Meteor {
  id: number
  top: number
  left: number
  delay: number
  duration: number
  angle: number
  distanceX: number
  distanceY: number
  size: number
  trailLength: number
  trailWidth: number
  headGlow: number
  brightness: number
  endScale: number
  trajectory: 'glide' | 'vanish'
}

const stars = ref<Star[]>([])
const meteors = ref<Meteor[]>([])
let resizeTimer: number | null = null

const random = (min: number, max: number) => Math.random() * (max - min) + min
const STAR_DRIFT_ANGLE = 18
const STAR_DRIFT_RAD = STAR_DRIFT_ANGLE * Math.PI / 180

const pickStartFromPerimeter = (width: number, height: number) => {
  const perimeterWeight = width * 2 + height * 2
  const hit = random(0, perimeterWeight)
  const cornerSafe = 0.12

  if (hit < width) {
    return {
      edge: 'top' as const,
      startX: random(cornerSafe, 1 - cornerSafe) * width,
      startY: random(-0.12, 0.04) * height
    }
  }
  if (hit < width + height) {
    return {
      edge: 'right' as const,
      startX: random(0.96, 1.12) * width,
      startY: random(cornerSafe, 1 - cornerSafe) * height
    }
  }
  if (hit < width * 2 + height) {
    return {
      edge: 'bottom' as const,
      startX: random(cornerSafe, 1 - cornerSafe) * width,
      startY: random(0.96, 1.12) * height
    }
  }
  return {
    edge: 'left' as const,
    startX: random(-0.12, 0.04) * width,
    startY: random(cornerSafe, 1 - cornerSafe) * height
  }
}

const inwardDirection = (edge: 'top' | 'right' | 'bottom' | 'left') => {
  if (edge === 'top') return { x: 0, y: 1 }
  if (edge === 'right') return { x: -1, y: 0 }
  if (edge === 'bottom') return { x: 0, y: -1 }
  return { x: 1, y: 0 }
}

const rotate2D = (x: number, y: number, degrees: number) => {
  const rad = degrees * Math.PI / 180
  const cos = Math.cos(rad)
  const sin = Math.sin(rad)
  return {
    x: x * cos - y * sin,
    y: x * sin + y * cos
  }
}

const pickSeparatedStart = (
  width: number,
  height: number,
  usedStarts: Array<{ x: number; y: number }>
) => {
  if (usedStarts.length === 0) {
    return pickStartFromPerimeter(width, height)
  }

  let best = pickStartFromPerimeter(width, height)
  let bestMinDistance = -1

  for (let i = 0; i < 14; i++) {
    const candidate = pickStartFromPerimeter(width, height)
    let minDistance = Number.POSITIVE_INFINITY

    for (const used of usedStarts) {
      const d = Math.hypot(candidate.startX - used.x, candidate.startY - used.y)
      if (d < minDistance) {
        minDistance = d
      }
    }

    if (minDistance > bestMinDistance) {
      best = candidate
      bestMinDistance = minDistance
    }
  }

  return best
}

const generateStars = () => {
  const newStars: Star[] = []
  const width = window.innerWidth
  const height = window.innerHeight
  const areaRatio = (width * height) / (1920 * 1080)
  const starCount = Math.floor(230 * Math.max(0.9, Math.min(2.6, areaRatio)))

  for (let i = 0; i < starCount; i++) {
    const bucket = Math.random()
    const isMicro = bucket < 0.72
    const isMedium = bucket >= 0.72 && bucket < 0.94
    const driftDistance = random(28, 88)
    const driftEnabled = Math.random() < 0.05

    newStars.push({
      id: i + 1,
      x: random(0, 100),
      y: random(0, 100),
      size: isMicro ? random(0.55, 1.2) : isMedium ? random(1.15, 2) : random(2, 3.2),
      delay: random(0, 10),
      duration: isMicro ? random(4.5, 10) : random(3.4, 8.4),
      opacity: isMicro ? random(0.2, 0.6) : random(0.45, 0.95),
      blur: isMicro ? random(0.1, 1.1) : random(0, 0.85),
      driftDuration: random(380, 760),
      driftX: Math.cos(STAR_DRIFT_RAD) * driftDistance,
      driftY: Math.sin(STAR_DRIFT_RAD) * driftDistance,
      driftEnabled
    })
  }
  stars.value = newStars
}

const generateMeteors = () => {
  const newMeteors: Meteor[] = []
  const numMeteors = 6
  const width = window.innerWidth
  const height = window.innerHeight
  const centerX = width * 0.5
  const centerY = height * 0.46
  let currentDelay = random(1.2, 3.8)
  const shouldSpawnVanish = Math.random() < 0.45
  const vanishIndex = shouldSpawnVanish ? Math.floor(random(0, numMeteors)) : -1
  const usedStarts: Array<{ x: number; y: number }> = []

  for (let i = 0; i < numMeteors; i++) {
    const trajectory: Meteor['trajectory'] = i === vanishIndex ? 'vanish' : 'glide'
    const start = pickSeparatedStart(width, height, usedStarts)
    const startX = start.startX
    const startY = start.startY
    usedStarts.push({ x: startX, y: startY })
    let targetX = centerX + random(-width * 0.12, width * 0.12)
    let targetY = centerY + random(-height * 0.1, height * 0.1)

    if (trajectory === 'glide') {
      const normal = inwardDirection(start.edge)
      const offset = random(-38, 38)
      const direction = rotate2D(normal.x, normal.y, offset)
      const distance = random(Math.min(width, height) * 0.24, Math.min(width, height) * 0.58)
      targetX = startX + direction.x * distance
      targetY = startY + direction.y * distance
    }

    const distanceX = targetX - startX
    const distanceY = targetY - startY
    const angle = Math.atan2(distanceY, distanceX) * 180 / Math.PI

    newMeteors.push({
      id: i + 1,
      top: (startY / height) * 100,
      left: (startX / width) * 100,
      delay: currentDelay,
      duration: trajectory === 'vanish' ? random(26, 38) : random(22, 34),
      angle,
      distanceX,
      distanceY,
      size: trajectory === 'vanish' ? random(1.8, 3) : random(2, 3.6),
      trailLength: trajectory === 'vanish' ? random(90, 150) : random(130, 210),
      trailWidth: random(1.1, 2.1),
      headGlow: trajectory === 'vanish' ? random(8, 14) : random(10, 18),
      brightness: trajectory === 'vanish' ? random(0.68, 0.9) : random(0.8, 1),
      endScale: trajectory === 'vanish' ? random(0.22, 0.48) : random(0.82, 1.05),
      trajectory
    })

    currentDelay += random(2.8, 6.5)
  }

  meteors.value = newMeteors
}

const regenerateScene = () => {
  generateStars()
  generateMeteors()
}

const handleResize = () => {
  if (resizeTimer) {
    window.clearTimeout(resizeTimer)
  }

  resizeTimer = window.setTimeout(() => {
    generateStars()
    generateMeteors()
  }, 150)
}

onMounted(() => {
  regenerateScene()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) {
    window.clearTimeout(resizeTimer)
  }
})
</script>

<template>
  <div class="star-field">
    <div 
      v-for="star in stars" 
      :key="`star-${star.id}`" 
      class="star-wrap"
      :class="{ 'is-drifting': star.driftEnabled }"
      :style="{
        left: `${star.x}%`,
        top: `${star.y}%`,
        '--drift-x': `${star.driftX}px`,
        '--drift-y': `${star.driftY}px`,
        '--drift-duration': `${star.driftDuration}s`
      }"
    >
      <div
        class="star"
        :style="{
          width: `${star.size}px`,
          height: `${star.size}px`,
          animationDelay: `${star.delay}s`,
          animationDuration: `${star.duration}s`,
          opacity: star.opacity,
          filter: `blur(${star.blur}px)`
        }"
      ></div>
    </div>
    
    <div 
      v-for="meteor in meteors" 
      :key="`meteor-${meteor.id}`" 
      class="meteor"
      :style="{
        top: `${meteor.top}%`,
        left: `${meteor.left}%`,
        width: `${meteor.size}px`,
        height: `${meteor.size}px`,
        animationDelay: `${meteor.delay}s`,
        animationDuration: `${meteor.duration}s`,
        '--angle': `${meteor.angle}deg`,
        '--distance-x': `${meteor.distanceX}px`,
        '--distance-y': `${meteor.distanceY}px`,
        '--trail-length': `${meteor.trailLength}px`,
        '--trail-width': `${meteor.trailWidth}px`,
        '--meteor-glow': `${meteor.headGlow}px`,
        '--meteor-brightness': meteor.brightness,
        '--meteor-end-scale': meteor.endScale,
        '--meteor-opacity-mid': meteor.trajectory === 'vanish' ? 0.64 : 0.88,
        '--meteor-opacity-late': meteor.trajectory === 'vanish' ? 0.36 : 0.48,
        '--meteor-scale-mid': meteor.trajectory === 'vanish' ? 0.68 : 1,
        '--meteor-scale-late': meteor.trajectory === 'vanish' ? 0.52 : 0.94,
        '--meteor-end-scale-late': meteor.trajectory === 'vanish' ? meteor.endScale * 1.1 : Math.min(1.06, meteor.endScale * 1.02)
      }"
    >
      <div class="meteor-head"></div>
      <div class="meteor-trail"></div>
    </div>
  </div>
</template>

<style scoped>
.star-field {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: visible;
}

.star {
  position: relative;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 50%;
  animation: twinkle ease-in-out infinite;
  box-shadow:
    0 0 4px rgba(255, 255, 255, 0.5),
    0 0 10px rgba(202, 227, 255, 0.22);
  will-change: opacity, transform;
}

.star-wrap {
  position: absolute;
  width: 0;
  height: 0;
  will-change: transform;
}

.star-wrap.is-drifting {
  animation: star-drift var(--drift-duration) linear infinite;
}

@keyframes star-drift {
  0% {
    transform: translate3d(0, 0, 0);
  }
  100% {
    transform: translate3d(var(--drift-x), var(--drift-y), 0);
  }
}

@keyframes twinkle {
  0%, 100% {
    opacity: 0.3;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
}

.meteor {
  position: absolute;
  animation: meteor-move linear infinite;
  opacity: 0;
  transform: translate3d(0, 0, 0) rotate(var(--angle)) scale(1);
  will-change: transform, opacity, filter;
  filter: brightness(var(--meteor-brightness));
}

.meteor-head {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background:
    radial-gradient(circle, rgba(255, 255, 255, 1) 0 36%, rgba(196, 228, 255, 0.98) 50%, rgba(134, 196, 255, 0.18) 72%, transparent 100%);
  box-shadow:
    0 0 calc(var(--meteor-glow) * 0.5) rgba(255, 255, 255, 0.95),
    0 0 var(--meteor-glow) rgba(117, 188, 255, 0.55),
    0 0 calc(var(--meteor-glow) * 1.8) rgba(117, 188, 255, 0.18);
}

.meteor-trail {
  position: absolute;
  top: 50%;
  left: 0;
  width: var(--trail-length);
  height: var(--trail-width);
  background:
    linear-gradient(to right,
      rgba(120, 190, 255, 0) 0%,
      rgba(120, 190, 255, 0.06) 14%,
      rgba(180, 225, 255, 0.25) 48%,
      rgba(245, 251, 255, 0.82) 78%,
      rgba(255, 255, 255, 0.98) 100%);
  transform: translate(-100%, -50%);
  transform-origin: right center;
  filter: blur(0.8px);
  opacity: 0.9;
}

.meteor-trail::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(to right,
      rgba(255, 255, 255, 0) 0%,
      rgba(172, 222, 255, 0.06) 40%,
      rgba(172, 222, 255, 0.3) 78%,
      rgba(255, 255, 255, 0.85) 100%);
  filter: blur(4px);
  opacity: 0.72;
}

@keyframes meteor-move {
  0% {
    transform: translate3d(0, 0, 0) rotate(var(--angle)) scale(1.08);
    opacity: 0;
  }
  8% {
    transform: translate3d(calc(var(--distance-x) * 0.12), calc(var(--distance-y) * 0.12), 0) rotate(var(--angle)) scale(0.95);
    opacity: 0.92;
  }
  18% {
    transform: translate3d(calc(var(--distance-x) * 0.42), calc(var(--distance-y) * 0.42), 0) rotate(var(--angle)) scale(var(--meteor-scale-mid));
    opacity: var(--meteor-opacity-mid);
  }
  28% {
    transform: translate3d(calc(var(--distance-x) * 0.68), calc(var(--distance-y) * 0.68), 0) rotate(var(--angle)) scale(var(--meteor-scale-late));
    opacity: var(--meteor-opacity-late);
  }
  38% {
    transform: translate3d(calc(var(--distance-x) * 0.86), calc(var(--distance-y) * 0.86), 0) rotate(var(--angle)) scale(var(--meteor-end-scale-late));
    opacity: 0.12;
  }
  46% {
    transform: translate3d(var(--distance-x), var(--distance-y), 0) rotate(var(--angle)) scale(var(--meteor-end-scale));
    opacity: 0;
  }
  100% {
    transform: translate3d(var(--distance-x), var(--distance-y), 0) rotate(var(--angle)) scale(var(--meteor-end-scale));
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .star,
  .meteor {
    animation: none;
  }

  .meteor {
    display: none;
  }
}
</style>
