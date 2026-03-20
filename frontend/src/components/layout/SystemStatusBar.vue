<script setup lang="ts">
import { Cpu, Database, Activity, Zap, BrainCircuit } from 'lucide-vue-next'
</script>

<template>
  <div class="system-status-bar">
    <div class="status-content">
      <!-- Left: System Status -->
      <div class="status-left">
        <div class="status-indicator">
          <div class="pulse-dot"></div>
          <span class="status-text">系统运行中</span>
        </div>
        
        <div class="metric-group">
          <div class="metric-item">
            <BrainCircuit :size="14" class="metric-icon" />
            <span class="metric-label">Ollama</span>
            <span class="metric-value online">在线</span>
          </div>
          <div class="metric-item">
            <Database :size="14" class="metric-icon" />
            <span class="metric-label">Neo4j</span>
            <span class="metric-value online">在线</span>
          </div>
          <div class="metric-item">
            <Cpu :size="14" class="metric-icon" />
            <span class="metric-label">VRAM</span>
            <span class="metric-value">8.4 / 16 GB</span>
          </div>
          <div class="metric-item">
            <Activity :size="14" class="metric-icon" />
            <span class="metric-label">延迟</span>
            <span class="metric-value">1.2ms</span>
          </div>
        </div>
      </div>

      <!-- Right: Sync & Version -->
      <div class="status-right">
        <div class="sync-indicator">
          <Zap :size="12" />
          <span>神经同步</span>
          <span class="sync-value">0.994σ</span>
        </div>
        <div class="version-tag">v1.0.0</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.system-status-bar {
  height: 100%;
  background: var(--color-glass-bg);
  backdrop-filter: blur(20px);
  border-top: 1px solid var(--color-glass-border);
  width: 100%;
}

.status-content {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  max-width: 100vw;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: var(--color-success);
  border-radius: 50%;
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { 
    opacity: 1;
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.4);
  }
  50% { 
    opacity: 0.8;
    box-shadow: 0 0 0 6px rgba(34, 197, 94, 0);
  }
}

.status-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text);
}

.metric-group {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-left: 24px;
  border-left: 1px solid var(--color-outline);
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.metric-icon {
  color: var(--color-text-dim);
}

.metric-label {
  font-size: 11px;
  color: var(--color-text-dim);
}

.metric-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
  font-family: 'Fira Code', monospace;
}

.metric-value.online {
  color: var(--color-success);
}

.status-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sync-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--color-surface);
  border-radius: 6px;
  font-size: 11px;
  color: var(--color-text-dim);
}

.sync-value {
  color: var(--color-primary);
  font-weight: 600;
  font-family: 'Fira Code', monospace;
}

.version-tag {
  padding: 4px 10px;
  background: var(--color-surface);
  border-radius: 4px;
  font-size: 10px;
  font-family: 'Fira Code', monospace;
  color: var(--color-text-dim);
}
</style>
