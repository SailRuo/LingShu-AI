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
  const taskTimers = new Map<string, number[]>();

  const { connect, on, register } = useWebSocket();

  const currentConversation = computed(() =>
    conversations.value.find((c) => c.id === currentConversationId.value) || null
  );

  const currentMessages = computed<AnyMessage[]>(
    () => (currentConversationId.value ? messagesMap.value[currentConversationId.value] || [] : [])
  );

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

      const res = await fetch(getFullUrl(`/api/chat/turns?${params}`));
      if (!res.ok) throw new Error('History fetch failed');
      
      const turns: any[] = await res.json();
      const convo = conversations.value.find(c => String(c.id) === String(conversationId));
      const agentId = convo?.metadata?.agentId;
      const agent = agentsStore.agents.find(a => String(a.id) === String(agentId));
      
      if (turns.length > 0) {
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
        
        messagesMap.value[conversationId] = formattedMessages;
      }
    } catch (err) {
      console.error('Load messages error:', err);
    } finally {
      isLoadingMessages.value = false;
    }
  }

  async function sendMessage(content: string, attachments: any[] = []) {
    if (!currentConversationId.value || (!content.trim() && attachments.length === 0)) return;

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

    if (!messagesMap.value[currentConversationId.value]) {
      messagesMap.value[currentConversationId.value] = [];
    }
    messagesMap.value[currentConversationId.value].push(userMsg);

    // 本地任务态原型：输入 #task 开头时直接进入任务执行卡片
    if (content.trim().startsWith('#task')) {
      enqueueTaskPrototype(content.trim(), agent);
      return;
    }

    // 更新会话列表最后一条消息
    const conv = conversations.value.find(c => c.id === currentConversationId.value);
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
    
    if (currentConversationId.value) {
      if (!messagesMap.value[currentConversationId.value]) {
        messagesMap.value[currentConversationId.value] = [];
      }
      messagesMap.value[currentConversationId.value].push(aiMsg);
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
              const messages = messagesMap.value[currentConversationId.value!] as TextMessage[];
              if (messages && messages.length > 0) {
                const lastIdx = messages.length - 1;
                const lastMsg = messages[lastIdx];
                if (lastMsg.type === 'text' && !lastMsg.isSelf) {
                  messages[lastIdx] = {
                    ...lastMsg,
                    content: lastMsg.content + chunk
                  };
                  // 更新会话列表的最后一条消息
                  const conv = conversations.value.find(c => c.id === currentConversationId.value);
                  if (conv) conv.lastMessage = messages[lastIdx].content;
                }
              }
            }
          }
        }
      }

      // 流式传输完成，更新状态
      const messages = messagesMap.value[currentConversationId.value!] as TextMessage[];
      if (messages && messages.length > 0) {
        const lastMsg = messages[messages.length - 1];
        if (lastMsg && lastMsg.type === 'text' && !lastMsg.isSelf) {
          lastMsg.status = 'sent';
        }
      }
    } catch (err) {
      console.error('Stream error:', err);
      // 更新错误状态
      const messages = messagesMap.value[currentConversationId.value!] as TextMessage[];
      if (messages && messages.length > 0) {
        const lastMsg = messages[messages.length - 1];
        if (lastMsg && lastMsg.type === 'text' && !lastMsg.isSelf) {
          lastMsg.content = '⚠️ 消息发送失败：' + (err as Error).message;
          lastMsg.status = 'failed';
        }
      }
    }
  }

  function enqueueTaskPrototype(raw: string, agent: any) {
    if (!currentConversationId.value) return;
    const text = raw.replace(/^#task\s*/i, '').trim();
    const workspace = extractWorkspacePath(text) || 'D:\\work\\demo';
    const commandCategory = inferCommandCategory(text);
    const taskTitle = text || '复杂编程任务执行';
    const taskMessageId = `task-${Date.now()}`;

    const steps: TaskStep[] = [
      { id: 'step-1', label: '解析任务并扫描项目结构', state: 'active' },
      { id: 'step-2', label: '检查目录权限与命令类别权限', state: 'pending' },
      { id: 'step-3', label: '等待首次审批', state: 'pending' },
      { id: 'step-4', label: '执行测试与环境检查', state: 'pending' },
      { id: 'step-5', label: '定位问题并修改代码', state: 'pending' },
      { id: 'step-6', label: '复跑测试并输出摘要', state: 'pending' }
    ];

    const snapshot: TaskExecutionSnapshot = {
      title: taskTitle,
      state: 'running',
      workspace,
      commandCategory,
      permissionApproved: false,
      steps,
      logs: [`[${new Date().toLocaleTimeString()}] 任务已创建，准备进入执行态`],
      approvalRequest: null
    };

    const taskMsg: TaskMessage = {
      id: taskMessageId,
      type: 'task',
      senderId: 'bot',
      senderName: agent?.displayName || '灵枢 AI',
      senderAvatar: agent?.avatar || '/linger.png',
      timestamp: new Date(),
      status: 'sent',
      isSelf: false,
      content: snapshot
    };

    messagesMap.value[currentConversationId.value].push(taskMsg);
    const conv = conversations.value.find(c => c.id === currentConversationId.value);
    if (conv) {
      conv.lastMessage = `[任务] ${taskTitle}`;
      conv.timestamp = new Date();
    }

    updateTaskMessage(currentConversationId.value, taskMessageId, draft => {
      draft.content.steps[0].state = 'done';
      draft.content.steps[1].state = 'active';
      draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 已解析任务并识别工作目录：${workspace}`);
    });

    const timer1 = window.setTimeout(() => {
      updateTaskMessage(currentConversationId.value!, taskMessageId, draft => {
        draft.content.steps[1].state = 'done';
        draft.content.steps[2].state = 'active';
        draft.content.state = 'waiting_approval';
        draft.content.approvalRequest = {
          id: `approval-${Date.now()}`,
          scope: 'directory',
          target: workspace,
          reason: `首次访问目录 ${workspace} 并执行 ${commandCategory} 命令，需要你审批并长期授权。`
        };
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 权限校验完成，等待用户审批`);
      });
    }, 800);
    trackTaskTimer(taskMessageId, timer1);
  }

  function handleTaskAction(conversationId: string, messageId: string, action: 'approve' | 'reject' | 'pause' | 'resume' | 'stop') {
    const msg = findTaskMessage(conversationId, messageId);
    if (!msg) return;

    if (action === 'approve') {
      clearTaskTimers(messageId);
      updateTaskMessage(conversationId, messageId, draft => {
        draft.content.permissionApproved = true;
        draft.content.state = 'running';
        draft.content.approvalRequest = null;
        draft.content.steps[2].state = 'done';
        draft.content.steps[3].state = 'active';
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 用户已审批：目录 + 命令类别长期授权`);
      });
      runTaskAfterApproval(conversationId, messageId);
      return;
    }

    if (action === 'reject') {
      clearTaskTimers(messageId);
      updateTaskMessage(conversationId, messageId, draft => {
        draft.content.state = 'stopped';
        draft.content.approvalRequest = null;
        draft.content.steps[2].state = 'failed';
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 用户拒绝审批，任务终止`);
      });
      return;
    }

    if (action === 'pause') {
      clearTaskTimers(messageId);
      updateTaskMessage(conversationId, messageId, draft => {
        if (draft.content.state === 'running') {
          draft.content.state = 'paused';
          draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 用户暂停任务`);
        }
      });
      return;
    }

    if (action === 'resume') {
      const latest = findTaskMessage(conversationId, messageId);
      if (!latest || latest.content.state !== 'paused') return;
      updateTaskMessage(conversationId, messageId, draft => {
        draft.content.state = 'running';
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 用户恢复任务，继续执行`);
      });
      resumeTaskFromCurrentStep(conversationId, messageId);
      return;
    }

    if (action === 'stop') {
      clearTaskTimers(messageId);
      updateTaskMessage(conversationId, messageId, draft => {
        draft.content.state = 'stopped';
        draft.content.approvalRequest = null;
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 用户终止任务`);
      });
    }
  }

  function runTaskAfterApproval(conversationId: string, messageId: string) {
    const timer2 = window.setTimeout(() => {
      updateTaskMessage(conversationId, messageId, draft => {
        if (draft.content.state !== 'running') return;
        draft.content.steps[3].state = 'done';
        draft.content.steps[4].state = 'active';
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] npm test 执行完成，已定位失败测试文件`);
      });
    }, 900);

    const timer3 = window.setTimeout(() => {
      updateTaskMessage(conversationId, messageId, draft => {
        if (draft.content.state !== 'running') return;
        draft.content.steps[4].state = 'done';
        draft.content.steps[5].state = 'active';
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 已完成代码修复，开始复跑测试`);
      });
    }, 1800);

    const timer4 = window.setTimeout(() => {
      updateTaskMessage(conversationId, messageId, draft => {
        if (draft.content.state !== 'running') return;
        draft.content.steps[5].state = 'done';
        draft.content.state = 'done';
        draft.content.summary = '任务完成：修改 2 个文件，测试通过。';
        draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 任务完成，输出变更摘要`);
      });
    }, 2800);

    trackTaskTimer(messageId, timer2);
    trackTaskTimer(messageId, timer3);
    trackTaskTimer(messageId, timer4);
  }

  function resumeTaskFromCurrentStep(conversationId: string, messageId: string) {
    const current = findTaskMessage(conversationId, messageId);
    if (!current || current.content.state !== 'running') return;

    const steps = current.content.steps;
    if (steps[5].state === 'done') {
      updateTaskMessage(conversationId, messageId, draft => {
        draft.content.state = 'done';
      });
      return;
    }

    if (steps[3].state === 'active' || steps[3].state === 'pending') {
      runTaskAfterApproval(conversationId, messageId);
      return;
    }

    if (steps[4].state === 'active') {
      const timer = window.setTimeout(() => {
        updateTaskMessage(conversationId, messageId, draft => {
          if (draft.content.state !== 'running') return;
          draft.content.steps[4].state = 'done';
          draft.content.steps[5].state = 'active';
          draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 继续执行：进入测试复跑阶段`);
        });
      }, 800);
      trackTaskTimer(messageId, timer);
      return;
    }

    if (steps[5].state === 'active') {
      const timer = window.setTimeout(() => {
        updateTaskMessage(conversationId, messageId, draft => {
          if (draft.content.state !== 'running') return;
          draft.content.steps[5].state = 'done';
          draft.content.state = 'done';
          draft.content.summary = '任务完成：修改 2 个文件，测试通过。';
          draft.content.logs.push(`[${new Date().toLocaleTimeString()}] 恢复后完成任务`);
        });
      }, 700);
      trackTaskTimer(messageId, timer);
    }
  }

  function trackTaskTimer(messageId: string, timerId: number) {
    const existing = taskTimers.get(messageId) || [];
    existing.push(timerId);
    taskTimers.set(messageId, existing);
  }

  function clearTaskTimers(messageId: string) {
    const timers = taskTimers.get(messageId) || [];
    timers.forEach(t => clearTimeout(t));
    taskTimers.delete(messageId);
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

  function inferCommandCategory(text: string): string {
    const normalized = text.toLowerCase();
    if (normalized.includes('npm') || normalized.includes('pnpm') || normalized.includes('yarn')) return 'npm';
    if (normalized.includes('python') || normalized.includes('pytest')) return 'python';
    if (normalized.includes('mvn') || normalized.includes('gradle') || normalized.includes('java')) return 'java';
    if (normalized.includes('git')) return 'git';
    return 'npm';
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
    createNewConversation
  };
});
