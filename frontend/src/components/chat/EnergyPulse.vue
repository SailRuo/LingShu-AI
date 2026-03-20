<script setup lang="ts">
interface Props {
  isThinking?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isThinking: false
})
</script>

<template>
  <div class="pulse-container" :class="{ 'is-thinking': isThinking }">
    <div class="pulse-sphere">
      <div class="inner-core" />
      <div class="orbit-ring" v-if="isThinking" />
    </div>
    <div class="message-wrapper">
      <slot>
        <div class="welcome-text">
          欢迎回来
        </div>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.pulse-container {
  --pulse-speed: 5s;
  --pulse-scale: 1.12;
  --core-glow: 30px;
  --core-speed: 3s;
  
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 0;
  transition: all 0.8s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.pulse-container.is-thinking {
  --pulse-speed: 1.2s;
  --pulse-scale: 1.25;
  --core-glow: 60px;
  --core-speed: 0.8s;
  transform: translateY(-10px) scale(1.05);
}

@keyframes energy-breath {
  0%, 100% { transform: scale(1); opacity: 0.45; filter: blur(18px); }
  50%      { transform: scale(var(--pulse-scale)); opacity: 0.85; filter: blur(35px); }
}

@keyframes core-glow {
  0%, 100% { box-shadow: 0 0 var(--core-glow) var(--color-pulse-ring); }
  50%      { box-shadow: 0 0 calc(var(--core-glow) * 2.5) var(--color-pulse-ring); }
}

@keyframes orbit {
  from { transform: rotate(0deg) translateX(70px) rotate(0deg); opacity: 0; }
  20%  { opacity: 1; }
  80%  { opacity: 1; }
  to   { transform: rotate(360deg) translateX(70px) rotate(-360deg); opacity: 0; }
}

.pulse-sphere {
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, var(--color-pulse-core) 0%, var(--color-pulse-ring) 45%, transparent 80%);
  border-radius: 50%;
  animation: energy-breath var(--pulse-speed) ease-in-out infinite;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: all 0.6s ease;
}

.inner-core {
  width: 28px;
  height: 28px;
  background: var(--color-pulse-ring);
  border-radius: 50%;
  animation: core-glow var(--core-speed) ease-in-out infinite;
  transition: all 0.6s ease;
  z-index: 2;
}

.orbit-ring {
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--color-accent);
  border-radius: 50%;
  filter: blur(2px);
  box-shadow: 0 0 10px var(--color-accent);
  animation: orbit 2s linear infinite;
}

.welcome-text {
  margin-top: 36px;
  font-size: 1.4rem;
  max-width: 800px;
  text-align: center;
  line-height: 1.5;
  color: var(--color-text-dim);
  transition: all 0.4s ease;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.highlight {
  color: var(--color-primary);
  font-weight: 700;
  text-shadow: 0 0 20px var(--color-primary-dim);
  transition: color 0.4s ease;
}

.is-thinking .welcome-text {
  opacity: 0.6;
  filter: blur(1px);
}
</style>
