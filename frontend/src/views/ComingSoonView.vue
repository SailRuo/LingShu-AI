<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { Construction } from 'lucide-vue-next'

defineProps<{
  title?: string
}>()
</script>

<template>
  <div class="coming-soon-view">
    <div class="content">
      <div class="icon-wrapper">
        <n-icon :size="48" :component="Construction" />
      </div>
      <h2 class="title">{{ title || '功能开发中' }}</h2>
      <p class="description">该功能正在紧锣密鼓地开发中，敬请期待</p>
    </div>
  </div>
</template>

<style scoped>
.coming-soon-view {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.content {
  text-align: center;
  max-width: 400px;
}

.icon-wrapper {
  width: 96px;
  height: 96px;
  margin: 0 auto 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-dim);
  border-radius: 24px;
  color: var(--color-primary);
}

.title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 12px;
}

.description {
  font-size: 14px;
  color: var(--color-text-dim);
  margin: 0;
  line-height: 1.6;
}
</style>
