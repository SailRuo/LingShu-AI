import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { useLocalStorage } from "@vueuse/core";
import { getFullUrl } from "@/utils/request";

export interface ChatSessionItem {
  id: number;
  userId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export function getClientUserId(): string {
  const storageKey = "lingshu_user_id";
  const existing = window.localStorage.getItem(storageKey);
  if (existing && existing.trim()) {
    return existing.trim();
  }
  const randomPart =
    typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const generated = `web:${randomPart}`;
  window.localStorage.setItem(storageKey, generated);
  return generated;
}

export const useChatSessionStore = defineStore("chat-session", () => {
  const userId = ref(getClientUserId());
  const sessions = ref<ChatSessionItem[]>([]);
  const isLoadingSessions = ref(false);
  const hasLoadedSessions = ref(false);
  const activeSessionId = useLocalStorage<number | null>(
    "lingshu-active-session-id",
    null,
  );

  const currentSession = computed(
    () =>
      sessions.value.find((session) => session.id === activeSessionId.value) ??
      null,
  );

  function setActiveSession(sessionId: number | null) {
    activeSessionId.value = sessionId;
  }

  function applyPreferredSession(preferredSessionId?: number | null) {
    if (preferredSessionId != null) {
      const preferred = sessions.value.find(
        (session) => session.id === preferredSessionId,
      );
      if (preferred) {
        activeSessionId.value = preferred.id;
        return;
      }
    }

    if (activeSessionId.value != null) {
      const existing = sessions.value.find(
        (session) => session.id === activeSessionId.value,
      );
      if (existing) {
        activeSessionId.value = existing.id;
        return;
      }
    }

    activeSessionId.value = sessions.value[0]?.id ?? null;
  }

  async function fetchSessions(preferredSessionId?: number | null) {
    if (isLoadingSessions.value) {
      return sessions.value;
    }

    isLoadingSessions.value = true;
    try {
      const params = new URLSearchParams({ userId: userId.value });
      const response = await fetch(getFullUrl(`/api/chat/sessions?${params}`));
      if (!response.ok) {
        throw new Error("Failed to fetch chat sessions");
      }

      const data: ChatSessionItem[] = await response.json();
      sessions.value = data;
      applyPreferredSession(preferredSessionId);
      hasLoadedSessions.value = true;
      return sessions.value;
    } finally {
      isLoadingSessions.value = false;
    }
  }

  async function createSession(title?: string) {
    const response = await fetch(getFullUrl("/api/chat/sessions"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: userId.value,
        title: title?.trim() ? title.trim() : null,
      }),
    });

    if (!response.ok) {
      throw new Error("Failed to create chat session");
    }

    const created: ChatSessionItem = await response.json();
    await fetchSessions(created.id);
    return created;
  }

  async function ensureActiveSession() {
    if (!hasLoadedSessions.value || sessions.value.length === 0) {
      await fetchSessions();
    } else {
      applyPreferredSession();
    }
    return activeSessionId.value;
  }

  return {
    userId,
    sessions,
    isLoadingSessions,
    hasLoadedSessions,
    activeSessionId,
    currentSession,
    setActiveSession,
    fetchSessions,
    createSession,
    ensureActiveSession,
  };
});
