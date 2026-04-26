import { ref, onUnmounted } from 'vue';

export interface WebSocketMessage {
  type: string;
  [key: string]: any;
}

export type MessageHandler = (message: WebSocketMessage) => void;

export function useWebSocket() {
  const socket = ref<WebSocket | null>(null);
  const isConnected = ref(false);
  const handlers = new Map<string, Set<MessageHandler>>();
  const reconnectCount = ref(0);
  const maxReconnects = 5;
  const reconnectTimer = ref<any>(null);

  function connect(url: string = 'ws://localhost:8080/ws/chat') {
    if (socket.value?.readyState === WebSocket.OPEN) return;

    socket.value = new WebSocket(url);

    socket.value.onopen = () => {
      console.log('WebSocket connected');
      isConnected.value = true;
      reconnectCount.value = 0;
      emit('connected', { type: 'connected', message: '连接成功' });
    };

    socket.value.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        const typeHandlers = handlers.get(message.type);
        if (typeHandlers) {
          typeHandlers.forEach(handler => handler(message));
        }
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e);
      }
    };

    socket.value.onclose = () => {
      console.log('WebSocket closed');
      isConnected.value = false;
      handleReconnect(url);
    };

    socket.value.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  function handleReconnect(url: string) {
    if (reconnectCount.value < maxReconnects) {
      const delay = Math.pow(2, reconnectCount.value) * 1000;
      reconnectTimer.value = setTimeout(() => {
        reconnectCount.value++;
        connect(url);
      }, delay);
    }
  }

  function disconnect() {
    if (reconnectTimer.value) clearTimeout(reconnectTimer.value);
    socket.value?.close();
    socket.value = null;
    isConnected.value = false;
  }

  function send(message: WebSocketMessage) {
    if (socket.value?.readyState === WebSocket.OPEN) {
      socket.value.send(JSON.stringify(message));
    } else {
      console.warn('WebSocket is not open. Message not sent:', message);
    }
  }

  function on(type: string, handler: MessageHandler) {
    if (!handlers.has(type)) {
      handlers.set(type, new Set());
    }
    handlers.get(type)!.add(handler);
  }

  function off(type: string, handler: MessageHandler) {
    const typeHandlers = handlers.get(type);
    if (typeHandlers) {
      typeHandlers.delete(handler);
    }
  }

  function emit(type: string, message: WebSocketMessage) {
    const typeHandlers = handlers.get(type);
    if (typeHandlers) {
      typeHandlers.forEach(handler => handler(message));
    }
  }

  function register(userId: string, sessionId?: number | null) {
    send({
      type: 'register',
      userId,
      sessionId
    });
  }

  function sendChat(params: {
    message: string;
    agentId?: number;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
    images?: string[];
    sessionId?: number | null;
    enableThinking?: boolean;
  }) {
    send({
      type: 'chat',
      ...params
    });
  }

  onUnmounted(() => {
    disconnect();
  });

  return {
    isConnected,
    connect,
    disconnect,
    send,
    on,
    off,
    register,
    sendChat
  };
}
