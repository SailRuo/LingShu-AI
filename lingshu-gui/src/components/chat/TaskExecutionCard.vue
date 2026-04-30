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

    <section v-if="message.content.summary" class="task-summary">
      <h5>结果摘要</h5>
      <p>{{ message.content.summary }}</p>
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
  background: var(--bg-input-area);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  color: var(--text-primary);
  user-select: text;
  -webkit-user-select: text;
}

.task-header {
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-chat-window);
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
  border: 1px solid var(--border-color-dark);
  background: var(--bg-hover);
}

.state-running { color: var(--color-primary); }
.state-waiting_approval { color: var(--color-warning); }
.state-paused { color: var(--color-info); }
.state-done { color: var(--color-success); }
.state-stopped { color: var(--color-error); }
.state-failed { color: var(--color-error); }

.task-meta, .task-steps, .task-approval, .task-logs, .task-summary, .task-actions {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
}

.task-meta {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.task-meta code {
  background: var(--bg-hover);
  padding: 2px 6px;
  border-radius: 6px;
  color: var(--text-primary);
}

.task-steps h5, .task-approval h5, .task-logs h5, .task-summary h5 {
  margin: 0 0 8px;
  font-size: 13px;
}

.task-summary p {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.task-steps ul {
  margin: 0;
  padding-left: 0;
  list-style: none;
  display: grid;
  gap: 6px;
  font-size: 13px;
}

.step-done { color: var(--color-success); }
.step-active { color: var(--color-primary); font-weight: 700; }
.step-failed { color: var(--color-error); }
.step-pending { color: var(--text-tertiary); }

.task-approval {
  background: var(--bg-chat-window);
}

.task-approval p {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.task-logs pre {
  margin: 0;
  background: var(--bg-chat-window);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px;
  max-height: 130px;
  overflow: auto;
  font-size: 11px;
  line-height: 1.45;
  font-family: Consolas, "Courier New", monospace;
  white-space: pre-wrap;
  user-select: text;
  -webkit-user-select: text;
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
  border: 1px solid var(--border-color);
  background: var(--bg-input);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  color: var(--text-primary);
}

.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn.primary {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.btn.danger {
  background: var(--bg-hover);
  border-color: var(--border-color-dark);
  color: var(--color-error);
}
</style>
