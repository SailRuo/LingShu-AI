import type { Ref } from 'vue';
import type { BaseMessage, AnyMessage } from './message';
import type { Conversation } from './conversation';

export interface ChatState {
  conversations: Conversation[];
  currentConversationId: string | null;
  messagesMap: Record<string, AnyMessage[]>;
  isLoadingMessages: boolean;
  searchQuery: string;
  activeNav: string;
}

export interface ChatActions {
  loadConversations: () => Promise<void>;
  selectConversation: (id: string) => Promise<void>;
  loadMessages: (conversationId: string) => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
  retrySendMessage: (messageId: string) => void;
  setSearchQuery: (query: string) => void;
  setActiveNav: (nav: string) => void;
}

export interface ChatGetters {
  currentConversation: Ref<Conversation | null>;
  currentMessages: Ref<AnyMessage[]>;
  filteredConversations: Ref<Conversation[]>;
  totalUnreadCount: Ref<number>;
}

export type ChatStore = ChatState & ChatActions & ChatGetters;
