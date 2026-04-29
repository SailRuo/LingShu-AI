<script setup lang="ts">
import { computed } from 'vue';
import type { TaskMessage, TaskExecutionState } from '../../types/message';

const props = defineProps<{
  message: TaskMessage;
}>();

const emit = defineEmits<{
  action: [action: 'approve' | 'reject' | 'pause' | 'resume' | 'stop', messageId: string];
}>();

const stateLabel = computed(() => {
  const mapping: Record<TaskExecutionState, string> = {
    running: '执行中',
    waiting_approval: '等待审批',
    paused: '已暂停',
    done: '已完成',
    stopped: '已终止',
    failed: '执行失败'
  };
  return mapping[props.message.content.state];
});

const stateClass = computed(() => `state-${props.message.content.state}`);

const canPause = computed(() => props.message.content.state === 'running');
const canResume = computed(() => props.message.content.state === 'paused');
const canStop = computed(() => ['running', 'paused', 'waiting_approval'].includes(props.message.content.state));
</script>

<template>
  <article class="task-card">
    <header class="task-header">
      <div class="task-title">{{ message.content.title }}</div>
      <span class="state-pill" :class="stateClass">{{ stateLabel }}</span>
    </header>

    <section class="task-meta">
      <div><strong>目录：</strong><code>{{ message.content.workspace }}</code></div>
      <div><strong>命令类别：</strong><code>{{ message.content.commandCategory }}</code></div>
      <div>
        <strong>权限：</strong>
        <span v-if="message.content.permissionApproved">已授权</span>
        <span v-else>未授权</span>
      </div>
    </section>

    <section class="task-steps">
      <h5>步骤进度</h5>
      <ul>
        <li v-for="step in message.content.steps" :key="step.id" :class="`step-${step.state}`">
          <span v-if="step.state === 'done'">✅</span>
          <span v-else-if="step.state === 'active'">👉</span>
          <span v-else-if="step.state === 'failed'">❌</span>
          <span v-else>⬜</span>
          {{ step.label }}
        </li>
      </ul>
    </section>

    <section v-if="message.content.approvalRequest" class="task-approval">
      <h5>审批请求</h5>
      <p>{{ message.content.approvalRequest.reason }}</p>
      <div class="actions">
        <button class="btn primary" @click="emit('action', 'approve', message.id)">同意并长期授权</button>
        <button class="btn danger" @click="emit('action', 'reject', message.id)">拒绝</button>
      </div>
    </section>

    <section class="task-logs">
      <h5>实时日志</h5>
      <pre>{{ message.content.logs.join('\n') }}</pre>
    </section>

    <footer class="task-actions">
      <button class="btn" :disabled="!canPause" @click="emit('action', 'pause', message.id)">暂停</button>
      <button class="btn" :disabled="!canResume" @click="emit('action', 'resume', message.id)">恢复</button>
      <button class="btn danger" :disabled="!canStop" @click="emit('action', 'stop', message.id)">终止</button>
    </footer>
  </article>
</template>

<style scoped>
.task-card {
  width: min(760px, 100%);
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #d6e4ff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 22px rgba(47, 111, 237, 0.12);
}

.task-header {
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  border-bottom: 1px solid #e5e7eb;
}

.task-title {
  font-size: 14px;
  font-weight: 700;
}

.state-pill {
  font-size: 11px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid;
}

.state-running { color: #166534; background: #ecfdf3; border-color: #86efac; }
.state-waiting_approval { color: #92400e; background: #fef3c7; border-color: #fcd34d; }
.state-paused { color: #1e3a8a; background: #dbeafe; border-color: #93c5fd; }
.state-done { color: #065f46; background: #d1fae5; border-color: #6ee7b7; }
.state-stopped { color: #7f1d1d; background: #fee2e2; border-color: #fca5a5; }
.state-failed { color: #7f1d1d; background: #ffe4e6; border-color: #fda4af; }

.task-meta, .task-steps, .task-approval, .task-logs, .task-actions {
  padding: 10px 14px;
  border-bottom: 1px solid #eceff5;
}

.task-meta {
  display: grid;
  gap: 6px;
  font-size: 12px;
}

.task-meta code {
  background: #eef2f7;
  padding: 2px 6px;
  border-radius: 6px;
}

.task-steps h5, .task-approval h5, .task-logs h5 {
  margin: 0 0 8px;
  font-size: 13px;
}

.task-steps ul {
  margin: 0;
  padding-left: 0;
  list-style: none;
  display: grid;
  gap: 6px;
  font-size: 13px;
}

.step-done { color: #047857; }
.step-active { color: #1d4ed8; font-weight: 700; }
.step-failed { color: #b91c1c; }
.step-pending { color: #64748b; }

.task-approval {
  background: #fffbeb;
}

.task-approval p {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}

.task-logs pre {
  margin: 0;
  background: #0f172a;
  color: #d1d5db;
  border-radius: 8px;
  padding: 10px;
  max-height: 130px;
  overflow: auto;
  font-size: 11px;
  line-height: 1.45;
  font-family: Consolas, "Courier New", monospace;
  white-space: pre-wrap;
}

.task-actions {
  border-bottom: none;
  display: flex;
  gap: 8px;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.btn {
  border: 1px solid #d1d5db;
  background: white;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn.primary {
  background: #22c55e;
  border-color: #22c55e;
  color: #fff;
}

.btn.danger {
  background: #fff1f2;
  border-color: #fda4af;
  color: #9f1239;
}
</style>
