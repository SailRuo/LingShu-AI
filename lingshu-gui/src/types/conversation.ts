export type OnlineStatus = 'online' | 'offline' | 'away';
export type ConversationType = 'chat' | 'group' | 'system';

export interface Conversation {
  id: string;
  avatar: string;
  name: string;
  lastMessage: string;
  timestamp: Date;
  unreadCount: number;
  isPinned: boolean;
  isMuted: boolean;
  onlineStatus?: OnlineStatus;
  type: ConversationType;
  metadata?: Record<string, unknown>;
}

export interface ConversationListProps {
  conversations: Conversation[];
  selectedId: string | null;
  searchQuery: string;
  onSelect: (id: string) => void;
  onSearch: (query: string) => void;
  onContextMenu?: (id: string, event: MouseEvent) => void;
}
