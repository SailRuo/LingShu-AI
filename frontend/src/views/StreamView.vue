<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { NSpin, NEmpty, NIcon, NTag, NScrollbar } from 'naive-ui'
import { Zap, BrainCircuit, Workflow, AlertCircle, Database, Search } from 'lucide-vue-next'

interface SemanticMatch {
  factId: number | null
  score: number
  contentSnippet: string
}

interface MemoryRetrievalEvent {
  query: string
  extractedEntities: string[]
  graphMatchedIds: number[]
  graphMatchedContent?: string[]
  semanticMatches: SemanticMatch[]
  finalRankedIds: number[]
  finalRankedContent?: string[]
  baseFactContents?: string[]
  timestamp: string
  fallbackActivated?: boolean
}

const events = ref<MemoryRetrievalEvent[]>([])
const isLoading = ref(true)
const pollTimer = ref<number | null>(null)
const error = ref<string | null>(null)

const fetchEvents = async () => {
  try {
    const res = await fetch('http://localhost:8080/api/memory/events')
    if (!res.ok) throw new Error('获取事件流失败')
    const data = await res.json()
    // 反转数组，让最新事件排在前面
    events.value = (data || []).reverse()
    error.value = null
  } catch (err: any) {
    console.error('Failed to fetch stream events:', err)
    error.value = err.message || '获取事件流失败'
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  fetchEvents()
  pollTimer.value = window.setInterval(fetchEvents, 3000)
})

onBeforeUnmount(() => {
  if (pollTimer.value) clearInterval(pollTimer.value)
})

const formatTime = (isoString: string) => {
  const d = new Date(isoString)
  if (Number.isNaN(d.getTime())) return '未知时间'
  return d.toLocaleTimeString('zh-CN', { hour12: false }) + '.' + d.getMilliseconds().toString().padStart(3, '0')
}

const getScoreColor = (score: number) => {
  if (score >= 0.8) return 'var(--color-success)'
  if (score >= 0.6) return 'var(--color-warning)'
  return 'var(--color-text-dim)'
}
</script>

<template>
  <div class="stream-view">
    <header class="view-header">
      <div class="header-title">
        <NIcon :size="24" color="var(--color-primary)"><Workflow /></NIcon>
        <h2>检索溯源事件流</h2>
      </div>
      <p class="header-desc">记录实时发生的 RAG 检索链路，包含实体激活、图谱召回与向量打分记录。</p>
    </header>

    <div class="stream-content">
      <div v-if="isLoading && events.length === 0" class="loading-state">
        <NSpin size="large" stroke="var(--color-primary)" />
        <span>正在接入神经漫游接口...</span>
      </div>

      <div v-else-if="error" class="error-state">
        <NIcon :size="48" color="var(--color-error)"><AlertCircle /></NIcon>
        <span>{{ error }}</span>
      </div>

      <div v-else-if="events.length === 0" class="empty-state">
        <NEmpty description="暂无检索事件，尝试发送包含既有事实的消息激活它。" />
      </div>

      <NScrollbar v-else class="event-list-scroll">
        <transition-group name="list" tag="div" class="event-list">
          <div v-for="(event, i) in events" :key="event.timestamp + i" class="event-card glass">

            <div class="event-time-bar">
              <span class="time-label">{{ formatTime(event.timestamp) }}</span>
              <div class="line"></div>
              <div class="dot pulse"></div>
            </div>

            <div class="event-body">
              <div class="query-section">
                <NIcon :size="16" class="icon-query"><Search /></NIcon>
                <div class="query-text">{{ event.query }}</div>
              </div>

              <div class="pipeline-stages">
                <!-- 阶段 1: 实体提取与图谱激活 -->
                <div class="stage-block">
                  <div class="stage-header">
                    <NIcon :size="14" color="var(--color-accent)"><BrainCircuit /></NIcon>
                    <span>1. GAM-RAG 图谱激活</span>
                  </div>
                  <div class="stage-content">
                    <div class="entity-list" v-if="event.extractedEntities && event.extractedEntities.length > 0">
                      <NTag v-for="(entity, ei) in event.extractedEntities" :key="ei" size="small" round :bordered="false" class="entity-tag">
                        {{ entity }}
                      </NTag>
                    </div>
                    <div class="empty-note" v-else>未提取到关键实体</div>

                    <div class="graph-hits" v-if="event.graphMatchedContent && event.graphMatchedContent.length > 0">
                      激活事实:
                      <div class="graph-content-list">
                        <div v-for="(content, gi) in event.graphMatchedContent" :key="gi" class="graph-content-item">
                          <NIcon :size="10" color="var(--color-primary)"><FlashOutline /></NIcon>
                          {{ content }}
                        </div>
                      </div>
                    </div>
                    <div class="graph-hits" v-else-if="event.baseFactContents && event.baseFactContents.length > 0">
                      用户基础事实 ({{ event.baseFactContents.length }}):
                      <div class="graph-content-list">
                        <div v-for="(content, bi) in event.baseFactContents" :key="bi" class="graph-content-item base-fact">
                          <NIcon :size="10" color="var(--color-accent)"><BrainCircuit /></NIcon>
                          {{ content }}
                        </div>
                      </div>
                    </div>
                    <div class="empty-note" v-else>未触发图谱激活</div>
                  </div>
                </div>

                <!-- 阶段 2: 向量召回 -->
                <div class="stage-block">
                  <div class="stage-header">
                    <NIcon :size="14" color="var(--color-info)"><Database /></NIcon>
                    <span>2. Semantic 向量检索</span>
                  </div>
                  <div class="stage-content">
                    <div class="semantic-list" v-if="event.semanticMatches && event.semanticMatches.length > 0">
                      <div class="match-item" v-for="(match, mi) in event.semanticMatches" :key="mi">
                        <div class="match-score" :style="{ color: getScoreColor(match.score) }">
                          {{ (match.score * 100).toFixed(1) }}%
                        </div>
                        <div class="match-snippet">
                          <span class="match-id" v-if="match.factId">#{{ match.factId }}</span>
                          {{ match.contentSnippet }}
                        </div>
                      </div>
                    </div>
                    <div class="empty-note" v-else>未匹配到相关高维片段</div>
                  </div>
                </div>

                <!-- 阶段 3: 最终采纳 -->
                <div class="stage-block final-stage">
                  <div class="stage-header">
                    <NIcon :size="14" color="var(--color-success)"><Zap /></NIcon>
                    <span>3. 最终上下文装配</span>
                  </div>
                  <div class="stage-content">
                    <div class="final-hits" v-if="event.finalRankedContent && event.finalRankedContent.length > 0">
                      装配事实内容:
                      <div class="final-content-list">
                        <div v-for="(content, ci) in event.finalRankedContent" :key="ci" class="final-content-item">
                          <NIcon :size="10" color="var(--color-success)"><Database /></NIcon>
                          {{ content }}
                        </div>
                      </div>
                    </div>
                    <div class="fallback-section" v-else-if="event.fallbackActivated">
                      <div class="fallback-note">
                        <NIcon :size="12" color="var(--color-accent)"><BrainCircuit /></NIcon>
                        已通过图谱基础事实提供上下文（Fallback 激活）
                      </div>
                      <div class="final-content-list" v-if="event.finalRankedContent && event.finalRankedContent.length > 0">
                        <div v-for="(content, fi) in event.finalRankedContent" :key="fi" class="final-content-item">
                          <NIcon :size="10" color="var(--color-success)"><Database /></NIcon>
                          {{ content }}
                        </div>
                      </div>
                    </div>
                    <div class="empty-note" v-else>未提供附加上下文</div>
                  </div>
                </div>

              </div>
            </div>
          </div>
        </transition-group>
      </NScrollbar>
    </div>
  </div>
</template>

<style scoped>
.stream-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
  box-sizing: border-box;
}

.view-header {
  margin-bottom: 24px;
  flex-shrink: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.header-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.5px;
}

.header-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-dim);
}

.stream-content {
  flex: 1;
  min-height: 0;
  position: relative;
  border-radius: 16px;
  background: var(--color-glass-bg);
  border: 1px solid var(--color-glass-border);
  overflow: hidden;
  padding: 20px;
}

.loading-state, .error-state, .empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--color-text-dim);
  font-size: 14px;
}

.event-list-scroll {
  height: 100%;
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-right: 12px;
  padding-bottom: 20px;
}

/* 列表动画 */
.list-enter-active,
.list-leave-active {
  transition: all 0.5s ease;
}
.list-enter-from {
  opacity: 0;
  transform: translateY(-20px) scale(0.98);
}
.list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

.event-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(var(--color-surface-rgb), 0.4);
  border: 1px solid var(--color-outline);
  transition: all 0.3s ease;
}

.event-card:hover {
  background: rgba(var(--color-surface-rgb), 0.8);
  border-color: var(--color-primary-dim);
}

.event-time-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 70px;
  flex-shrink: 0;
}

.time-label {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 8px;
}

.line {
  width: 2px;
  flex: 1;
  background: linear-gradient(to bottom, var(--color-primary-dim), transparent);
  border-radius: 2px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
  margin-top: -4px;
}

.dot.pulse {
  box-shadow: 0 0 0 0 rgba(var(--color-primary-rgb), 0.4);
  animation: pulse-dot 2s infinite;
}

@keyframes pulse-dot {
  0% { box-shadow: 0 0 0 0 rgba(var(--color-primary-rgb), 0.4); }
  70% { box-shadow: 0 0 0 8px rgba(var(--color-primary-rgb), 0); }
  100% { box-shadow: 0 0 0 0 rgba(var(--color-primary-rgb), 0); }
}

.event-body {
  flex: 1;
  min-width: 0;
}

.query-section {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  border-left: 3px solid var(--color-primary);
}

.icon-query {
  margin-top: 2px;
  color: var(--color-primary);
}

.query-text {
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.5;
  word-break: break-all;
}

.pipeline-stages {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: auto auto;
  gap: 16px;
}

.stage-block {
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
  border: 1px solid var(--color-outline);
  overflow: hidden;
}

/* 前两个阶段并排显示 */
.stage-block:nth-child(1),
.stage-block:nth-child(2) {
  grid-column: span 1;
}

/* 第三个阶段独占一整行 */
.stage-block:nth-child(3) {
  grid-column: 1 / -1;
}

.stage-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.1);
  border-bottom: 1px solid var(--color-outline);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
}

.stage-content {
  padding: 12px;
  font-size: 12px;
}

.empty-note {
  color: var(--color-text-dim);
  font-style: italic;
  opacity: 0.7;
}

.fallback-note {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 500;
}

.fallback-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.graph-content-item.base-fact {
  background: rgba(var(--color-accent-rgb, 0, 200, 200), 0.08);
  border-left-color: var(--color-accent);
}

.entity-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.graph-hits {
  margin-top: 8px;
  color: var(--color-text);
}

.id-list {
  font-family: var(--font-mono);
  color: var(--color-primary);
  opacity: 0.9;
}

.graph-content-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.graph-content-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: rgba(var(--color-primary-rgb), 0.08);
  border-radius: 6px;
  border-left: 2px solid var(--color-primary);
  color: var(--color-text);
  font-size: 11px;
  line-height: 1.4;
}

.semantic-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.match-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.match-score {
  font-family: var(--font-mono);
  font-weight: 600;
  background: rgba(0, 0, 0, 0.2);
  padding: 2px 4px;
  border-radius: 4px;
}

.match-snippet {
  flex: 1;
  color: var(--color-text);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.match-id {
  color: var(--color-info);
  font-family: var(--font-mono);
  margin-right: 4px;
}

.final-stage {
  grid-column: 1 / -1;
  background: rgba(var(--color-success-rgb), 0.03);
  border-color: rgba(var(--color-success-rgb), 0.2);
}

.final-stage .stage-header {
  background: rgba(var(--color-success-rgb), 0.1);
  border-bottom-color: rgba(var(--color-success-rgb), 0.2);
}

.id-list-final {
  font-family: var(--font-mono);
  color: var(--color-success);
  font-weight: 600;
}

.final-content-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.final-content-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: rgba(var(--color-success-rgb), 0.08);
  border-radius: 6px;
  border-left: 2px solid var(--color-success);
  color: var(--color-text);
  font-size: 11px;
  line-height: 1.4;
}

@media (prefers-reduced-motion: reduce) {
  .dot.pulse {
    animation: none !important;
    box-shadow: none;
  }

  .event-card {
    transition: none;
  }
}
</style>
