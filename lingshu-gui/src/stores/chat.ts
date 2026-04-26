import { defineStore } from 'pinia';
import { ref, computed, onMounted } from 'vue';
import type { Conversation } from '../types/conversation';
import type { AnyMessage, TextMessage } from '../types/message';
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
    setSearchQuery,
    setActiveNav,
    currentAgentId,
    setAgentId,
    createNewConversation
  };
});

