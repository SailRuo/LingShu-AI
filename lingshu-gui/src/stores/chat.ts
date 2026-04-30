import { defineStore } from 'pinia';
import { ref, computed, onMounted } from 'vue';
import type { Conversation } from '../types/conversation';
import type { AnyMessage, TaskExecutionSnapshot, TaskMessage, TaskStep, TextMessage } from '../types/message';
import { useWebSocket } from '../composables/useWebSocket';
import { useAgentsStore } from './agents';
import { Message } from '@arco-design/web-vue';

// 获取完整 API URL
function getFullUrl(path: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  return `${baseUrl}${path}`;
}

// 获取或生成用户 ID
function getClientUserId(): string {
  const storageKey = 'lingshu_user_id';
  const existing = localStorage.getItem(storageKey);
  if (existing && existing.trim()) {
    return existing.trim();
  }
  const randomPart = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const generated = `web:${randomPart}`;
  localStorage.setItem(storageKey, generated);
  return generated;
}

type BackendTaskRunState =
  | 'PENDING'
  | 'RUNNING'
  | 'WAITING_APPROVAL'
  | 'PAUSED'
  | 'COMPLETED'
  | 'FAILED'
  | 'STOPPED';

interface BackendTaskEventView {
  id: number;
  sequenceNo: number;
  eventType: string;
  payloadJson: string;
  timestamp: number;
}

interface BackendTaskRunView {
  id: number;
  userId: string;
  chatSessionId: number | null;
  title: string;
  workspacePath: string;
  commandCategory: string;
  state: BackendTaskRunState;
  runtimeSnapshotJson: string | null;
  events: BackendTaskEventView[];
}

interface WebSocketTaskEventMessage {
  type: 'taskEvent';
  taskRunId: number;
  eventType: string;
  payload: Record<string, any>;
}

interface TaskIntentResponse {
  taskRequest: boolean;
  reason: string;
}

// 文件转 Base64
function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => {
      let base64 = reader.result as string;
      // 移除 data:image/png;base64, 前缀
      if (base64.indexOf(',') !== -1) {
        base64 = base64.split(',')[1];
      }
      resolve(base64);
    };
    reader.onerror = error => reject(error);
  });
}

export const useChatStore = defineStore('chat', () => {
  const agentsStore = useAgentsStore();
  const conversations = ref<Conversation[]>([]);
  const currentConversationId = ref<string | null>(null);
  const messagesMap = ref<Record<string, AnyMessage[]>>({});
  const isLoadingMessages = ref(false);
  const searchQuery = ref('');
  const activeNav = ref('message');
  const userId = ref(getClientUserId());
  const currentAgentId = ref<number | null>(null); // 当前会话绑定的智能体 ID
  const taskModeByConversation = ref<Record<string, boolean>>({});
  const { connect, on, register } = useWebSocket();

  const currentConversation = computed(() =>
    conversations.value.find((c) => c.id === currentConversationId.value) || null
  );

  const currentMessages = computed<AnyMessage[]>(
    () => (currentConversationId.value ? messagesMap.value[currentConversationId.value] || [] : [])
  );

  const currentTaskModeEnabled = computed<boolean>(() => {
    if (!currentConversationId.value) return false;
    return Boolean(taskModeByConversation.value[currentConversationId.value]);
  });

  const filteredConversations = computed<Conversation[]>(() => {
    if (!searchQuery.value) return conversations.value;
    const q = searchQuery.value.toLowerCase();
    return conversations.value.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.lastMessage.toLowerCase().includes(q)
    );
  });

  const totalUnreadCount = computed<number>(
    () => conversations.value.reduce((sum, c) => sum + c.unreadCount, 0)
  );

  // 初始化 WebSocket
  onMounted(() => {
    connect();
    on('connected', () => {
      register(userId.value, 1);
    });
    on('taskEvent', async (message) => {
      await handleTaskEvent(message as WebSocketTaskEventMessage);
    });
  });

  async function loadConversations() {
    try {
      const res = await fetch(getFullUrl(`/api/chat/sessions?userId=${userId.value}`));
      if (!res.ok) throw new Error('Failed to fetch sessions');
      const sessions: any[] = await res.json();
      
      await agentsStore.fetchAgents();

      conversations.value = sessions.map(session => {
        const agent = agentsStore.agents.find(a => a.id === session.agentId);
        return {
          id: session.id.toString(),
          name: session.title || agent?.displayName || '新会话',
          lastMessage: session.lastMessage || '',
          timestamp: new Date(session.updatedAt || session.createdAt),
          avatar: agent?.avatar || '/linger.png',
          unreadCount: 0,
          isPinned: false,
          isMuted: false,
          type: 'chat',
          metadata: {
            agentId: session.agentId,
            sessionId: session.id
          }
        };
      });

      // 如果还没有选择会话且有活跃会话，默认选择第一个
      if (!currentConversationId.value && conversations.value.length > 0) {
        selectConversation(conversations.value[0].id);
      }
    } catch (err) {
      console.error('Load conversations error:', err);
    }
  }

  async function createNewConversation(agentId: number) {
    try {
      const agent = agentsStore.agents.find(a => a.id === agentId);
      const res = await fetch(getFullUrl('/api/chat/sessions'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: userId.value,
          title: agent?.displayName || '新对话',
          agentId: agentId
        })
      });
      
      if (!res.ok) throw new Error('Failed to create session');
      const newSession = await res.json();
      
      await loadConversations();
      selectConversation(newSession.id.toString());
      return newSession;
    } catch (err) {
      Message.error('创建会话失败');
      console.error(err);
    }
  }

  async function deleteConversation(sessionId: string) {
    try {
      const res = await fetch(getFullUrl(`/api/chat/sessions/${sessionId}`), {
        method: 'DELETE'
      });
      if (!res.ok) throw new Error('Delete session failed');
      
      Message.success('会话已删除');
      
      // 更新本地列表
      conversations.value = conversations.value.filter(c => c.id !== sessionId);
      delete messagesMap.value[sessionId];

      // 如果删除的是当前选中的会话，切换到第一个
      if (currentConversationId.value === sessionId) {
        currentConversationId.value = null;
        if (conversations.value.length > 0) {
          selectConversation(conversations.value[0].id);
        }
      }
    } catch (err) {
      Message.error('删除会话失败');
      console.error(err);
    }
  }

  // 初始化列表
  onMounted(() => {
    loadConversations();
  });

  async function selectConversation(id: string) {
    currentConversationId.value = id;
    // 切换会话时，重置当前选中的智能体为会话绑定的智能体（如果有）
    const conv = conversations.value.find(c => c.id === id);
    if (conv && conv.metadata && conv.metadata.agentId) {
      currentAgentId.value = conv.metadata.agentId as number;
    } else {
      currentAgentId.value = null;
    }
    await loadMessages(id);
  }

  async function loadMessages(conversationId: string) {
    isLoadingMessages.value = true;
    try {
      if (!messagesMap.value[conversationId]) {
        messagesMap.value[conversationId] = [];
      }
      
      // 通过 HTTP API 请求该会话的历史记录
      const params = new URLSearchParams({
        size: '50',
        userId: userId.value,
        sessionId: conversationId,
      });
      
      await agentsStore.fetchAgents();

      const [turnsResponse, tasksResponse] = await Promise.all([
        fetch(getFullUrl(`/api/chat/turns?${params}`)),
        fetch(getFullUrl(`/api/tasks?sessionId=${encodeURIComponent(conversationId)}&userId=${encodeURIComponent(userId.value)}`))
      ]);
      if (!turnsResponse.ok) throw new Error('History fetch failed');
      if (!tasksResponse.ok) throw new Error('Task history fetch failed');

      const turns: any[] = await turnsResponse.json();
      const taskRuns: BackendTaskRunView[] = await tasksResponse.json();
      const convo = conversations.value.find(c => String(c.id) === String(conversationId));
      const agentId = convo?.metadata?.agentId;
      const agent = agentsStore.agents.find(a => String(a.id) === String(agentId));

      const formattedMessages: AnyMessage[] = [];
      const chronologicalTurns = [...turns].reverse();

      chronologicalTurns.forEach((turn) => {
        if (turn.userMessage) {
          formattedMessages.push({
            id: `u-${turn.id}`,
            type: 'text',
            senderId: 'user',
            senderName: '我',
            senderAvatar: '',
            timestamp: new Date(turn.timestamp),
            status: 'sent',
            isSelf: true,
            content: turn.userMessage,
          });
        }

        if (turn.assistantMessage || turn.status === 'failed') {
          formattedMessages.push({
            id: `a-${turn.id}`,
            type: 'text',
            senderId: 'bot',
            senderName: agent?.displayName || '灵枢 AI',
            senderAvatar: agent?.avatar || '/linger.png',
            timestamp: new Date(turn.timestamp),
            status: 'sent',
            isSelf: false,
            content: turn.status === 'failed' ? `⚠️ ${turn.errorMessage || '请求失败'}` : (turn.assistantMessage || ''),
          });
        }
      });

      const taskMessages = taskRuns.map((run) => taskRunToMessage(run, {
        id: `task-run-${run.id}`,
        type: 'task',
        senderId: 'bot',
        senderName: agent?.displayName || '灵枢 AI',
        senderAvatar: agent?.avatar || '/linger.png',
        timestamp: taskCreatedTimestamp(run),
        status: 'sent',
        isSelf: false,
        content: createEmptyTaskSnapshot(run.title, run.workspacePath, run.commandCategory),
        metadata: {
          taskRunId: run.id,
          conversationId,
          chatSessionId: run.chatSessionId
        }
      }));

      messagesMap.value[conversationId] = [...formattedMessages, ...taskMessages]
        .sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
    } catch (err) {
      console.error('Load messages error:', err);
    } finally {
      isLoadingMessages.value = false;
    }
  }

  async function sendMessage(content: string, attachments: any[] = [], taskModeEnabled = false) {
    if (!currentConversationId.value || (!content.trim() && attachments.length === 0)) return;

    if (taskModeEnabled && attachments.length > 0) {
      Message.warning('任务模式暂不支持附件，请先移除附件后再执行任务');
      return;
    }

    // 处理图片附件为 Base64
    const images: string[] = [];
    const imageFiles = attachments.filter(a => a.type === 'image');
    for (const img of imageFiles) {
      try {
        const base64 = await fileToBase64(img.file);
        images.push(base64);
      } catch (err) {
        console.error('Failed to convert image to base64', err);
      }
    }

    const userMsg: TextMessage = {
      id: Date.now().toString(),
      type: 'text',
      senderId: 'user',
      senderName: '我',
      timestamp: new Date(),
      status: 'sent',
      isSelf: true,
      content: content,
      metadata: { 
        attachments: attachments.map(a => ({ 
          name: a.name, 
          type: a.type, 
          size: a.size 
        })) 
      }
    };

    const conversationId = currentConversationId.value;
    if (!messagesMap.value[conversationId]) {
      messagesMap.value[conversationId] = [];
    }
    messagesMap.value[conversationId].push(userMsg);

    // 更新会话列表最后一条消息
    const conv = conversations.value.find(c => c.id === conversationId);
    if (conv) {
      conv.lastMessage = content;
      conv.timestamp = new Date();
    }

    // 发送消息到后端流式接口
    const convo = conversations.value.find(c => c.id === currentConversationId.value);
    const agentId = convo?.metadata?.agentId;
    const sessionId = convo?.id;

    // 获取当前智能体信息
    const agent = agentsStore.agents.find(a => a.id === agentId);

    if (taskModeEnabled) {
      const intent = await detectTaskIntent(content);
      if (!intent.taskRequest) {
        Message.info('该消息更适合普通对话，已按聊天发送');
      } else {
      const tempTaskMessage = createPendingTaskMessage(content, conversationId, agent);
      messagesMap.value[conversationId].push(tempTaskMessage);
      await startTaskRun({
        tempMessageId: tempTaskMessage.id,
        conversationId,
        agent,
        sessionId: sessionId ? Number(sessionId) : null,
        requestText: content
      });
      return;
      }
    }

    // 添加一个空的 AI 消息用于流式更新
    const aiMsg: TextMessage = {
      id: (Date.now() + 1).toString(),
      type: 'text',
      senderId: 'bot',
      senderName: agent?.displayName || '灵枢 AI',
      senderAvatar: agent?.avatar || '/linger.png',
      timestamp: new Date(),
      status: 'sending',
      isSelf: false,
      content: ''
    };
    
    if (conversationId) {
      if (!messagesMap.value[conversationId]) {
        messagesMap.value[conversationId] = [];
      }
      messagesMap.value[conversationId].push(aiMsg);
    }

    // 使用流式 API
    const payload = {
      message: content,
      images: images,
      userId: userId.value,
      sessionId: sessionId,
      agentId: agentId
    };

    try {
      const res = await fetch(getFullUrl('/api/chat/stream'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok) throw new Error('Stream request failed');

      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      if (!reader) throw new Error('No reader found');

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed) continue;

          if (trimmed.startsWith('data:')) {
            const dataMatch = line.match(/^(\s*data:\s?)/);
            if (dataMatch) {
              const prefixLen = dataMatch[0].length;
              const chunk = line.slice(prefixLen).replace(/\r$/, '');

              // 更新最后一条 AI 消息
              const messages = messagesMap.value[conversationId] as TextMessage[];
              if (messages && messages.length > 0) {
                const lastIdx = messages.length - 1;
                const lastMsg = messages[lastIdx];
                if (lastMsg.type === 'text' && !lastMsg.isSelf) {
                  messages[lastIdx] = {
                    ...lastMsg,
                    content: lastMsg.content + chunk
                  };
                  // 更新会话列表的最后一条消息
                  const conv = conversations.value.find(c => c.id === conversationId);
                  if (conv) conv.lastMessage = messages[lastIdx].content;
                }
              }
            }
          }
        }
      }

      // 流式传输完成，更新状态
      const messages = messagesMap.value[conversationId] as TextMessage[];
      if (messages && messages.length > 0) {
        const lastMsg = messages[messages.length - 1];
        if (lastMsg && lastMsg.type === 'text' && !lastMsg.isSelf) {
          lastMsg.status = 'sent';
        }
      }
    } catch (err) {
      console.error('Stream error:', err);
      // 更新错误状态
      const messages = messagesMap.value[conversationId] as TextMessage[];
      if (messages && messages.length > 0) {
        const lastMsg = messages[messages.length - 1];
        if (lastMsg && lastMsg.type === 'text' && !lastMsg.isSelf) {
          lastMsg.content = '⚠️ 消息发送失败：' + (err as Error).message;
          lastMsg.status = 'failed';
        }
      }
    }
  }

  async function handleTaskAction(
    conversationId: string,
    messageId: string,
    action: 'approve' | 'reject' | 'pause' | 'resume' | 'stop'
  ) {
    const msg = findTaskMessage(conversationId, messageId);
    if (!msg) return;
    const taskRunId = msg.metadata?.taskRunId;
    if (!taskRunId) return;

    try {
      let updatedRun: BackendTaskRunView;
      if (action === 'approve' || action === 'reject') {
        updatedRun = await mutateTaskRun<BackendTaskRunView>(
          `/api/tasks/${taskRunId}/approve?userId=${encodeURIComponent(userId.value)}`,
          {
            method: 'POST',
            body: JSON.stringify({
              grantWorkspace: action === 'approve',
              grantCommandCategory: action === 'approve'
            })
          }
        );
      } else {
        updatedRun = await mutateTaskRun<BackendTaskRunView>(
          `/api/tasks/${taskRunId}/${action}?userId=${encodeURIComponent(userId.value)}`,
          { method: 'POST' }
        );
      }
      replaceTaskMessageFromRun(conversationId, messageId, updatedRun, msg.senderName, msg.senderAvatar);
    } catch (error) {
      console.error(`Task action ${action} failed`, error);
      Message.error(`任务${actionLabel(action)}失败`);
      updateTaskMessage(conversationId, messageId, draft => {
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 操作失败：${(error as Error).message}`);
      });
    }
  }

  function findTaskMessage(conversationId: string, messageId: string): TaskMessage | null {
    const messages = messagesMap.value[conversationId] || [];
    const match = messages.find(m => m.id === messageId && m.type === 'task');
    return (match as TaskMessage) || null;
  }

  function updateTaskMessage(conversationId: string, messageId: string, updater: (draft: TaskMessage) => void) {
    const messages = messagesMap.value[conversationId];
    if (!messages) return;
    const idx = messages.findIndex(m => m.id === messageId && m.type === 'task');
    if (idx === -1) return;
    const msg = messages[idx] as TaskMessage;
    const draft: TaskMessage = {
      ...msg,
      content: {
        ...msg.content,
        steps: msg.content.steps.map(s => ({ ...s })),
        logs: [...msg.content.logs],
        approvalRequest: msg.content.approvalRequest ? { ...msg.content.approvalRequest } : null
      }
    };
    updater(draft);
    messages[idx] = draft;
  }

  function upsertTaskMessageFromRun(
    conversationId: string,
    run: BackendTaskRunView,
    senderName: string,
    senderAvatar?: string,
    messageId?: string
  ) {
    const existing = messageId
      ? findTaskMessage(conversationId, messageId)
      : findTaskMessageByRunId(conversationId, run.id);
    const nextMessage = taskRunToMessage(run, existing ?? {
      id: messageId ?? `task-run-${run.id}`,
      type: 'task',
      senderId: 'bot',
      senderName,
      senderAvatar,
      timestamp: new Date(),
      status: 'sent',
      isSelf: false,
      content: createEmptyTaskSnapshot(run.title, run.workspacePath, run.commandCategory),
      metadata: { taskRunId: run.id, conversationId }
    } as TaskMessage);

    const messages = messagesMap.value[conversationId] || [];
    const idx = existing ? messages.findIndex((message) => message.id === existing.id) : -1;
    if (idx >= 0) {
      messages[idx] = nextMessage;
    } else {
      messages.push(nextMessage);
    }
    messagesMap.value[conversationId] = [...messages].sort(
      (a, b) => a.timestamp.getTime() - b.timestamp.getTime()
    );

    const conv = conversations.value.find(c => c.id === conversationId);
    if (conv) {
      conv.lastMessage = `[任务] ${run.title}`;
      conv.timestamp = new Date();
    }
  }

  function replaceTaskMessageFromRun(
    conversationId: string,
    messageId: string,
    run: BackendTaskRunView,
    senderName: string,
    senderAvatar?: string
  ) {
    upsertTaskMessageFromRun(conversationId, run, senderName, senderAvatar, messageId);
  }

  function findTaskMessageByRunId(conversationId: string, taskRunId: number): TaskMessage | null {
    const messages = messagesMap.value[conversationId] || [];
    const match = messages.find(
      (message) => message.type === 'task' && message.metadata?.taskRunId === taskRunId
    );
    return (match as TaskMessage) || null;
  }

  async function handleTaskEvent(message: WebSocketTaskEventMessage) {
    const taskLocation = findTaskLocation(message.taskRunId);
    if (!taskLocation) return;
    try {
      const run = await fetchTaskRun(message.taskRunId);
      replaceTaskMessageFromRun(
        taskLocation.conversationId,
        taskLocation.message.id,
        run,
        taskLocation.message.senderName,
        taskLocation.message.senderAvatar
      );
    } catch (error) {
      console.error('Failed to sync task run after taskEvent', error);
    }
  }

  function findTaskLocation(taskRunId: number): { conversationId: string; message: TaskMessage } | null {
    for (const [conversationId, messages] of Object.entries(messagesMap.value)) {
      const taskMessage = messages.find(
        (message) => message.type === 'task' && message.metadata?.taskRunId === taskRunId
      ) as TaskMessage | undefined;
      if (taskMessage) {
        return { conversationId, message: taskMessage };
      }
    }
    return null;
  }

  async function startTaskRun(params: {
    tempMessageId: string;
    conversationId: string;
    agent: any;
    sessionId: number | null;
    requestText: string;
  }) {
    try {
      const run = await mutateTaskRun<BackendTaskRunView>('/api/tasks/start', {
        method: 'POST',
        body: JSON.stringify({
          userId: userId.value,
          chatSessionId: params.sessionId,
          requestText: params.requestText,
          workspacePath: extractWorkspacePath(params.requestText) || 'E:\\Project\\LingShu-AI',
          commandCategory: inferCommandCategory(params.requestText)
        })
      });
      replaceTaskMessageFromRun(
        params.conversationId,
        params.tempMessageId,
        run,
        params.agent?.displayName || '灵枢 AI',
        params.agent?.avatar || '/linger.png'
      );
    } catch (error) {
      console.error('Failed to start task run', error);
      updateTaskMessage(params.conversationId, params.tempMessageId, draft => {
        draft.content.state = 'failed';
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 任务创建失败：${(error as Error).message}`);
      });
      Message.error('任务创建失败');
    }
  }

  async function fetchTaskRun(taskRunId: number): Promise<BackendTaskRunView> {
    const response = await fetch(getFullUrl(`/api/tasks/${taskRunId}?userId=${encodeURIComponent(userId.value)}`));
    if (!response.ok) {
      throw new Error(`Failed to fetch task run ${taskRunId}`);
    }
    return response.json();
  }

  async function mutateTaskRun<T>(path: string, init: RequestInit): Promise<T> {
    const response = await fetch(getFullUrl(path), {
      headers: {
        'Content-Type': 'application/json',
        ...(init.headers || {})
      },
      ...init
    });
    if (!response.ok) {
      throw new Error(await response.text() || 'Request failed');
    }
    return response.json();
  }

  async function detectTaskIntent(message: string): Promise<TaskIntentResponse> {
    try {
      const response = await fetch(getFullUrl('/api/chat/task-intent'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message })
      });
      if (!response.ok) {
        return { taskRequest: true, reason: 'intent-check-failed-open' };
      }
      return response.json();
    } catch {
      return { taskRequest: true, reason: 'intent-check-error-failed-open' };
    }
  }

  function createPendingTaskMessage(requestText: string, conversationId: string, agent: any): TaskMessage {
    return {
      id: `task-pending-${Date.now()}`,
      type: 'task',
      senderId: 'bot',
      senderName: agent?.displayName || '灵枢 AI',
      senderAvatar: agent?.avatar || '/linger.png',
      timestamp: new Date(),
      status: 'sent',
      isSelf: false,
      metadata: { conversationId },
      content: {
        title: requestText.trim() || '任务执行',
        state: 'running',
        workspace: extractWorkspacePath(requestText) || 'E:\\Project\\LingShu-AI',
        commandCategory: inferCommandCategory(requestText),
        permissionApproved: false,
        steps: [{ id: 'task_created', label: '创建任务并绑定会话', state: 'active' }],
        logs: [`[${new Date().toLocaleTimeString()}] 正在创建任务...`],
        approvalRequest: null
      }
    };
  }

  function taskRunToMessage(run: BackendTaskRunView, existing: TaskMessage): TaskMessage {
    return {
      ...existing,
      timestamp: existing.timestamp || taskCreatedTimestamp(run),
      content: buildTaskSnapshotFromRun(run),
      metadata: {
        ...(existing.metadata || {}),
        taskRunId: run.id,
        chatSessionId: run.chatSessionId
      }
    };
  }

  function buildTaskSnapshotFromRun(run: BackendTaskRunView): TaskExecutionSnapshot {
    const snapshot = createEmptyTaskSnapshot(run.title, run.workspacePath, run.commandCategory);
    snapshot.state = mapTaskState(run.state);
    snapshot.permissionApproved = snapshot.state !== 'waiting_approval';

    const orderedEvents = [...(run.events || [])].sort((a, b) => a.sequenceNo - b.sequenceNo);
    for (const event of orderedEvents) {
      const payload = parsePayload(event.payloadJson);
      applyTaskEventToSnapshot(snapshot, event.eventType, payload, event.timestamp);
    }

    if (snapshot.state === 'running' && snapshot.steps.length > 0 && !snapshot.steps.some(step => step.state === 'active')) {
      let lastPending: TaskStep | undefined;
      for (let i = snapshot.steps.length - 1; i >= 0; i -= 1) {
        if (snapshot.steps[i].state === 'pending') {
          lastPending = snapshot.steps[i];
          break;
        }
      }
      if (lastPending) {
        lastPending.state = 'active';
      }
    }

    return snapshot;
  }

  function createEmptyTaskSnapshot(title: string, workspace: string, commandCategory: string): TaskExecutionSnapshot {
    return {
      title,
      state: 'running',
      workspace,
      commandCategory,
      permissionApproved: false,
      steps: [],
      logs: [],
      approvalRequest: null
    };
  }

  function applyTaskEventToSnapshot(
    snapshot: TaskExecutionSnapshot,
    eventType: string,
    payload: Record<string, any>,
    timestamp: number
  ) {
    const time = new Date(timestamp).toLocaleTimeString();
    switch (eventType) {
      case 'TASK_CREATED':
        upsertStep(snapshot.steps, 'task_created', '创建任务并绑定会话', 'done');
        pushTaskLog(snapshot.logs, `[${time}] 任务已创建`);
        break;
      case 'APPROVAL_REQUIRED':
        snapshot.state = 'waiting_approval';
        snapshot.permissionApproved = false;
        upsertStep(snapshot.steps, 'approval', '检查并处理权限审批', 'active');
        snapshot.approvalRequest = {
          id: `approval-${timestamp}`,
          scope: 'directory',
          target: payload.workspacePath || snapshot.workspace,
          reason: buildApprovalReason(payload, snapshot.commandCategory)
        };
        pushTaskLog(snapshot.logs, `[${time}] 等待目录/命令权限审批`);
        break;
      case 'APPROVAL_GRANTED':
        snapshot.permissionApproved = true;
        snapshot.approvalRequest = null;
        upsertStep(snapshot.steps, 'approval', '检查并处理权限审批', 'done');
        pushTaskLog(snapshot.logs, `[${time}] 审批通过，已写入长期授权`);
        break;
      case 'APPROVAL_REJECTED':
        snapshot.state = 'stopped';
        snapshot.permissionApproved = false;
        upsertStep(snapshot.steps, 'approval', '检查并处理权限审批', 'failed');
        snapshot.approvalRequest = null;
        pushTaskLog(snapshot.logs, `[${time}] 审批被拒绝，任务停止`);
        break;
      case 'TASK_RESUMED':
        snapshot.state = 'running';
        pushTaskLog(snapshot.logs, `[${time}] 任务恢复执行`);
        break;
      case 'TASK_PAUSED':
        snapshot.state = 'paused';
        pushTaskLog(snapshot.logs, `[${time}] 任务已暂停`);
        break;
      case 'TASK_STOPPED':
        snapshot.state = 'stopped';
        completeActiveSteps(snapshot.steps, 'failed');
        pushTaskLog(snapshot.logs, `[${time}] 任务已终止`);
        break;
      case 'TASK_COMPLETED':
        snapshot.state = 'done';
        completeActiveSteps(snapshot.steps, 'done');
        snapshot.summary = payload.summary || '任务已完成';
        pushTaskLog(snapshot.logs, `[${time}] 任务完成`);
        break;
      case 'TASK_FAILED':
        snapshot.state = 'failed';
        completeActiveSteps(snapshot.steps, 'failed');
        pushTaskLog(snapshot.logs, `[${time}] 任务失败`);
        break;
      case 'STEP_STARTED':
        markStepStarted(snapshot.steps, payload.step);
        pushTaskLog(snapshot.logs, `[${time}] 开始步骤：${toStepLabel(payload.step)}`);
        break;
      case 'STEP_COMPLETED':
        markStepCompleted(snapshot.steps, payload.step);
        if (payload.result) {
          snapshot.summary = String(payload.result);
        }
        pushTaskLog(snapshot.logs, `[${time}] 完成步骤：${toStepLabel(payload.step)}`);
        break;
      case 'LOG':
        if (payload.message) {
          const prefix = payload.step ? `${toStepLabel(payload.step)}：` : '';
          pushTaskLog(snapshot.logs, `[${time}] ${prefix}${payload.message}`);
        }
        if (payload.step) {
          upsertStep(snapshot.steps, String(payload.step), toStepLabel(payload.step), 'active');
        }
        break;
      default:
        pushTaskLog(snapshot.logs, `[${time}] ${eventType}`);
    }
  }

  function upsertStep(steps: TaskStep[], stepId: string, label: string, state: TaskStep['state']) {
    if (!stepId) return;
    const step = steps.find(item => item.id === stepId);
    if (step) {
      // Keep terminal states stable unless failed overrides done.
      if (step.state === 'done' && state !== 'failed') return;
      if (step.state === 'failed') return;
      step.label = label || step.label;
      step.state = state;
      return;
    }
    steps.push({ id: stepId, label, state });
  }

  function markStepStarted(steps: TaskStep[], rawStep: unknown) {
    const stepId = typeof rawStep === 'string' ? rawStep : 'execution';
    const label = toStepLabel(stepId);
    upsertStep(steps, stepId, label, 'active');
  }

  function markStepCompleted(steps: TaskStep[], rawStep: unknown) {
    const stepId = typeof rawStep === 'string' ? rawStep : 'execution';
    const label = toStepLabel(stepId);
    upsertStep(steps, stepId, label, 'done');
  }

  function completeActiveSteps(steps: TaskStep[], finalState: 'done' | 'failed') {
    for (const step of steps) {
      if (step.state === 'active') {
        step.state = finalState;
      }
    }
  }

  function toStepLabel(rawStep: unknown): string {
    const step = typeof rawStep === 'string' ? rawStep : '';
    if (!step) return '执行流程';
    const builtIn: Record<string, string> = {
      task_created: '创建任务并绑定会话',
      approval: '检查并处理权限审批',
      agent_execution: '智能体执行',
      execution_plan: '生成执行计划',
      agent_resume: '恢复智能体上下文'
    };
    if (builtIn[step]) return builtIn[step];
    return step.replace(/_/g, ' ');
  }

  function pushTaskLog(logs: string[], message: string) {
    if (!logs.includes(message)) {
      logs.push(message);
    }
  }

  function parsePayload(payloadJson: string): Record<string, any> {
    try {
      return payloadJson ? JSON.parse(payloadJson) : {};
    } catch {
      return {};
    }
  }

  function taskCreatedTimestamp(run: BackendTaskRunView): Date {
    const createdEvent = [...(run.events || [])].sort((a, b) => a.timestamp - b.timestamp)[0];
    return new Date(createdEvent?.timestamp || Date.now());
  }

  function mapTaskState(state: BackendTaskRunState): TaskExecutionSnapshot['state'] {
    switch (state) {
      case 'WAITING_APPROVAL':
        return 'waiting_approval';
      case 'PAUSED':
        return 'paused';
      case 'COMPLETED':
        return 'done';
      case 'FAILED':
        return 'failed';
      case 'STOPPED':
        return 'stopped';
      case 'PENDING':
      case 'RUNNING':
      default:
        return 'running';
    }
  }

  function buildApprovalReason(payload: Record<string, any>, commandCategory: string): string {
    const fragments: string[] = [];
    if (payload.workspacePath && payload.requiresWorkspaceApproval) {
      fragments.push(`访问目录 ${payload.workspacePath}`);
    }
    if (commandCategory && payload.requiresCommandApproval) {
      fragments.push(`执行 ${commandCategory} 命令`);
    }
    if (fragments.length === 0) {
      return '本次任务需要额外权限审批。';
    }
    return `首次需要${fragments.join('、')}，审批通过后会记为长期授权。`;
  }

  function actionLabel(action: 'approve' | 'reject' | 'pause' | 'resume' | 'stop'): string {
    switch (action) {
      case 'approve':
        return '审批';
      case 'reject':
        return '拒绝';
      case 'pause':
        return '暂停';
      case 'resume':
        return '恢复';
      case 'stop':
        return '终止';
      default:
        return '操作';
    }
  }

  function inferCommandCategory(text: string): string {
    const normalized = text.toLowerCase();
    if (normalized.includes('git')) return 'git';
    if (normalized.includes('npm') || normalized.includes('pnpm') || normalized.includes('yarn') || normalized.includes('node') || normalized.includes('npx')) return 'node';
    if (normalized.includes('python') || normalized.includes('pytest') || normalized.includes('pip') || normalized.includes('uv ')) return 'python';
    if (normalized.includes('mvn') || normalized.includes('gradle') || normalized.includes('java') || normalized.includes('javac')) return 'java';
    if (normalized.includes('powershell') || normalized.includes('pwsh') || normalized.includes('cmd') || normalized.includes('bash') || normalized.includes('sh ')) return 'shell';
    return 'auto';
  }

  function extractWorkspacePath(text: string): string | null {
    const winPath = text.match(/[A-Za-z]:\\[^\s"'，。；;]+/);
    if (winPath) return winPath[0];
    return null;
  }

  function retrySendMessage(_messageId: string) {
    // TODO: 重发失败消息
  }

  function setSearchQuery(query: string) {
    searchQuery.value = query;
  }

  function setActiveNav(nav: string) {
    activeNav.value = nav;
  }

  function setAgentId(agentId: number | null) {
    currentAgentId.value = agentId;
    // 同时更新当前会话的元数据
    const conv = conversations.value.find(c => c.id === currentConversationId.value);
    if (conv) {
      if (!conv.metadata) conv.metadata = {};
      conv.metadata.agentId = agentId;
    }
  }

  function setTaskModeEnabled(enabled: boolean) {
    if (!currentConversationId.value) return;
    taskModeByConversation.value = {
      ...taskModeByConversation.value,
      [currentConversationId.value]: enabled
    };
  }

  return {
    conversations,
    currentConversationId,
    messagesMap,
    isLoadingMessages,
    searchQuery,
    activeNav,
    userId,
    currentConversation,
    currentMessages,
    currentTaskModeEnabled,
    filteredConversations,
    totalUnreadCount,
    loadConversations,
    selectConversation,
    loadMessages,
    sendMessage,
    deleteConversation,
    retrySendMessage,
    handleTaskAction,
    setSearchQuery,
    setActiveNav,
    currentAgentId,
    setAgentId,
    setTaskModeEnabled,
    createNewConversation
  };
});
