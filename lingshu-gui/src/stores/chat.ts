import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { Conversation } from '../types/conversation';
import type { AnyMessage } from '../types/message';

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([
    {
      id: '1',
      name: '灵枢 AI',
      lastMessage: '您好！我是灵枢 AI 助手，有什么可以帮您的吗？',
      timestamp: new Date(),
      avatar: '/bot.png',
      unreadCount: 0,
      isPinned: true,
      isMuted: false,
      type: 'system',
    },
    {
      id: '2',
      name: '文件传输助手',
      lastMessage: '[文件] 实现方案.pdf',
      timestamp: new Date(Date.now() - 1000 * 60 * 30),
      avatar: 'https://api.dicebear.com/7.x/bottts/svg?seed=file',
      unreadCount: 0,
      isPinned: false,
      isMuted: false,
      type: 'system',
    },
    {
      id: '3',
      name: 'XImage 开发小组',
      lastMessage: '郭浩振：邀请您进行远程控制...',
      timestamp: new Date(Date.now() - 1000 * 60 * 60 * 2),
      avatar: 'https://api.dicebear.com/7.x/identicon/svg?seed=ximage',
      unreadCount: 5,
      isPinned: false,
      isMuted: false,
      type: 'group',
    },
    {
      id: '4',
      name: '沐辰',
      lastMessage: '[图片]',
      timestamp: new Date(Date.now() - 1000 * 60 * 60 * 24),
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=muchen',
      unreadCount: 1,
      isPinned: false,
      isMuted: false,
      type: 'chat',
    },
    {
      id: '5',
      name: '大家中醫',
      lastMessage: '三张图教你“蜜肿、细节”！别急着...',
      timestamp: new Date(Date.now() - 1000 * 60 * 60 * 12),
      avatar: 'https://api.dicebear.com/7.x/initials/svg?seed=中医',
      unreadCount: 0,
      isPinned: false,
      isMuted: true,
      type: 'system',
    }
  ]);
  const currentConversationId = ref<string | null>(null);
  const messagesMap = ref<Record<string, AnyMessage[]>>({});
  const isLoadingMessages = ref(false);
  const searchQuery = ref('');
  const activeNav = ref('message');

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

  async function loadConversations() {
    // 数据已在初始化时填充
  }

  async function selectConversation(id: string) {
    currentConversationId.value = id;
    await loadMessages(id);
  }

  async function loadMessages(conversationId: string) {
    isLoadingMessages.value = true;
    try {
      // TODO: 接入后端 API
      messagesMap.value[conversationId] = [];
    } finally {
      isLoadingMessages.value = false;
    }
  }

  async function sendMessage(content: string) {
    if (!currentConversationId.value || !content.trim()) return;
    // TODO: 接入 WebSocket / API
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

  return {
    conversations,
    currentConversationId,
    messagesMap,
    isLoadingMessages,
    searchQuery,
    activeNav,
    currentConversation,
    currentMessages,
    filteredConversations,
    totalUnreadCount,
    loadConversations,
    selectConversation,
    loadMessages,
    sendMessage,
    retrySendMessage,
    setSearchQuery,
    setActiveNav,
  };
});
