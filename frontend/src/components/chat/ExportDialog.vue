<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { NModal, NCard, NRadioGroup, NRadio, NButton, NSpace, NTag } from "naive-ui";
import type { ExportFormat, ExportRange } from "@/utils/chatExport";

const props = defineProps<{
  show: boolean;
  messageCount: number;
  exporting?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
  (e: "confirm", payload: { format: ExportFormat; range: ExportRange }): void;
}>();

const format = ref<ExportFormat>("markdown");
const range = ref<ExportRange>("all");

watch(
  () => props.show,
  (visible) => {
    if (visible) {
      format.value = "markdown";
      range.value = "all";
    }
  },
);

const previewCount = computed(() => {
  if (range.value === "all") {
    return props.messageCount;
  }
  return Math.min(10, props.messageCount);
});

const estimatedSize = computed(() => {
  if (format.value === "markdown") {
    const kb = Math.max(3, Math.round(previewCount.value * 0.8));
    return `${kb} KB`;
  }
  const kb = Math.max(120, Math.round(previewCount.value * 24));
  return `${kb} KB`;
});

function closeDialog() {
  emit("update:show", false);
}

function handleConfirm() {
  emit("confirm", {
    format: format.value,
    range: range.value,
  });
}
</script>

<template>
  <NModal :show="show" preset="card" :mask-closable="!exporting" @update:show="emit('update:show', $event)">
    <NCard title="导出对话" size="small" :bordered="false" class="export-card" closable @close="closeDialog">
      <div class="section-title">导出格式</div>
      <NRadioGroup v-model:value="format" class="radio-group">
        <NSpace vertical>
          <NRadio value="markdown">Markdown (.md)</NRadio>
          <NRadio value="png">长图 (.png)</NRadio>
        </NSpace>
      </NRadioGroup>

      <div class="section-title">时间范围</div>
      <NRadioGroup v-model:value="range" class="radio-group">
        <NSpace vertical>
          <NRadio value="all">全部消息</NRadio>
          <NRadio value="recent10">最近 10 条</NRadio>
        </NSpace>
      </NRadioGroup>

      <div class="preview-box">
        <div>将导出 <NTag size="small" type="success">{{ previewCount }} 条消息</NTag></div>
        <div>预计文件大小：{{ estimatedSize }}</div>
      </div>

      <template #footer>
        <div class="footer-actions">
          <NButton secondary :disabled="exporting" @click="closeDialog">取消</NButton>
          <NButton type="primary" :loading="exporting" :disabled="messageCount === 0" @click="handleConfirm">
            确认导出
          </NButton>
        </div>
      </template>
    </NCard>
  </NModal>
</template>

<style scoped>
.export-card {
  width: min(92vw, 420px);
}

.section-title {
  margin: 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dim);
}

.radio-group {
  margin-bottom: 10px;
}

.preview-box {
  margin-top: 6px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--color-outline);
  background: color-mix(in srgb, var(--color-surface) 82%, transparent);
  color: var(--color-text-dim);
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
