import { computed, ref, watch } from "vue";
import { getFullUrl } from "@/utils/request";
import { useChatSessionStore } from "@/stores/chatSessionStore";
import type {
  ChatMessage,
  ChatMessageSegment,
  ChatToolSegment,
  ChatToolStep,
} from "@/types";

interface TurnArtifact {
  artifactType: string;
  mimeType?: string;
  url?: string;
  base64Data?: string;
}

interface TurnToolStep {
  toolCallId?: string;
  toolName?: string;
  skillName?: string;
  arguments?: string;
  result?: string;
  isError?: boolean;
  artifacts?: TurnArtifact[];
}

interface TurnHistoryItem {
  id: number;
  timestamp: number;
  status: string;
  userMessage?: string;
  userImages?: string[];
  assistantMessage?: string;
  errorMessage?: string;
  toolSteps?: TurnToolStep[];
  segments?: Array<{
    type: string;
    toolCallId?: string;
    toolName?: string;
    skillName?: string;
    arguments?: string;
    result?: string;
    isError?: boolean;
    content?: string;
    artifacts?: TurnArtifact[];
  }>;
}

function getToolStepKey(
  step: Partial<ChatToolStep>,
  fallbackIndex: number,
): string {
  return (
    step.toolCallId ||
    step.id ||
    [
      step.toolName ?? "",
      step.arguments ?? step.command ?? step.input ?? "",
    ].join("::") ||
    `tool-step-${fallbackIndex}`
  );
}

function mergeSegments(
  previousSegments?: ChatMessageSegment[],
  nextSegments?: ChatMessageSegment[],
): ChatMessageSegment[] | undefined {
  const merged: ChatMessageSegment[] = [];
  const toolSegments = new Map<string, number>();

  [...(previousSegments ?? []), ...(nextSegments ?? [])].forEach(
    (segment, index) => {
      if (segment.type === "text") {
        const content = segment.content ?? "";
        if (!content) return;

        const last = merged[merged.length - 1];
        if (last?.type === "text") {
          merged[merged.length - 1] = {
            ...last,
            content: `${last.content}${content}`,
            timestamp: segment.timestamp ?? last.timestamp,
          };
        } else {
          merged.push({
            type: "text",
            content,
            timestamp: segment.timestamp,
          });
        }
        return;
      }

      if (segment.type === "reasoning") {
        const content = segment.content ?? "";
        const last = merged[merged.length - 1];
        if (last?.type === "reasoning") {
          merged[merged.length - 1] = {
            ...last,
            content: `${last.content}${content}`,
            timestamp: segment.timestamp ?? last.timestamp,
          };
        } else {
          merged.push({
            type: "reasoning",
            content,
            timestamp: segment.timestamp,
          });
        }
        return;
      }

      if (segment.type === "image") {
        merged.push(segment);
        return;
      }

      const toolSegment = segment as ChatToolSegment;
      const key = getToolStepKey(toolSegment, index);
      const existingIndex = toolSegments.get(key);
      if (existingIndex == null) {
        toolSegments.set(key, merged.length);
        merged.push({
          ...toolSegment,
          type: "tool",
        });
        return;
      }

      const previous = merged[existingIndex] as ChatToolSegment;
      merged[existingIndex] = {
        ...previous,
        ...toolSegment,
        type: "tool",
        id: toolSegment.id ?? previous.id,
        toolCallId: toolSegment.toolCallId ?? previous.toolCallId,
        toolName: toolSegment.toolName ?? previous.toolName,
        skillName: toolSegment.skillName ?? previous.skillName,
        arguments: toolSegment.arguments ?? previous.arguments,
        command: toolSegment.command ?? previous.command,
        input: toolSegment.input ?? previous.input,
        result: toolSegment.result ?? previous.result,
        output: toolSegment.output ?? previous.output,
        isError: toolSegment.isError ?? previous.isError ?? false,
        status: toolSegment.status ?? previous.status,
        timestamp: toolSegment.timestamp ?? previous.timestamp,
        artifacts: toolSegment.artifacts ?? previous.artifacts,
      };
    },
  );

  return merged.length ? merged : undefined;
}

function mapTurnsToMessages(turns: TurnHistoryItem[]): ChatMessage[] {
  const chronologicalTurns = [...turns].reverse();
  const mapped: ChatMessage[] = [];

  chronologicalTurns.forEach((turn) => {
    mapped.push({
      id: turn.id * 2,
      role: "user",
      content: turn.userMessage ?? "",
      timestamp: turn.timestamp ?? Date.now(),
      images: turn.userImages?.length ? turn.userImages : undefined,
    });

    const toolSegments: ChatToolSegment[] = (turn.toolSteps ?? []).map(
      (step) => ({
        type: "tool",
        id: step.toolCallId,
        toolCallId: step.toolCallId,
        toolName: step.toolName ?? "",
        skillName: step.skillName,
        arguments: step.arguments,
        result: step.result,
        output: step.result,
        isError: !!step.isError,
        status: step.isError ? "error" : step.result ? "success" : "running",
        timestamp: turn.timestamp,
        artifacts: step.artifacts?.map((artifact) => ({
          artifactType: artifact.artifactType,
          mimeType: artifact.mimeType,
          url: artifact.url,
          base64Data: artifact.base64Data,
        })),
      }),
    );

    const finalText =
      turn.status === "failed"
        ? `⚠️ ${turn.errorMessage || "请求失败"}`
        : (turn.assistantMessage ?? "");

    const orderedSegments: ChatMessageSegment[] = [];
    (turn.segments ?? []).forEach((segment) => {
      if (segment.type === "text") {
        orderedSegments.push({
          type: "text" as const,
          content: segment.content ?? "",
          timestamp: turn.timestamp,
        });
        return;
      }
      if (segment.type === "tool") {
        orderedSegments.push({
          type: "tool" as const,
          id: segment.toolCallId,
          toolCallId: segment.toolCallId,
          toolName: segment.toolName ?? "",
          skillName: segment.skillName,
          arguments: segment.arguments,
          result: segment.result,
          output: segment.result,
          isError: !!segment.isError,
          status: segment.isError ? "error" : segment.result ? "success" : "running",
          timestamp: turn.timestamp,
          artifacts: segment.artifacts?.map((artifact) => ({
            artifactType: artifact.artifactType,
            mimeType: artifact.mimeType,
            url: artifact.url,
            base64Data: artifact.base64Data,
          })),
        });
      }
    });

    const segments: ChatMessageSegment[] = orderedSegments.length
      ? orderedSegments
      : [
          ...toolSegments,
          ...(finalText
            ? [
                {
                  type: "text" as const,
                  content: finalText,
                  timestamp: turn.timestamp,
                },
              ]
            : []),
        ];

    mapped.push({
      id: turn.id * 2 + 1,
      role: "assistant",
      content: finalText,
      timestamp: (turn.timestamp ?? Date.now()) + 1,
      segments: segments.length ? segments : undefined,
      toolSteps: toolSegments.length
        ? toolSegments.map((segment) => ({
            id: segment.id,
            toolCallId: segment.toolCallId,
            toolName: segment.toolName,
            skillName: segment.skillName,
            arguments: segment.arguments,
            result: segment.result,
            output: segment.output,
            isError: segment.isError,
            status: segment.status,
            timestamp: segment.timestamp,
            artifacts: segment.artifacts,
          }))
        : undefined,
      isToolStepsExpanded: false,
    });
  });

  return mapped;
}

export function useChat() {
  const sessionStore = useChatSessionStore();
  const messages = ref<ChatMessage[]>([]);
  const inputMessage = ref("");
  const inputImages = ref<string[]>([]);
  const isTyping = ref(false);
  const welcomeGreeting = ref("欢迎回来");
  const isLoadingHistory = ref(false);
  const hasMoreHistory = ref(true);
  const oldestMessageId = ref<number | null>(null);
  const currentSessionId = computed(() => sessionStore.activeSessionId);

  function resetConversationState() {
    messages.value = [];
    hasMoreHistory.value = true;
    oldestMessageId.value = null;
  }

  function ensureAssistantMessage(): ChatMessage {
    const lastMessage = messages.value[messages.value.length - 1];
    if (lastMessage?.role === "assistant") {
      return lastMessage;
    }

    const assistantMessage: ChatMessage = {
      role: "assistant",
      content: "",
      timestamp: Date.now(),
      segments: [],
      toolSteps: [],
      isToolStepsExpanded: true,
      isLoading: true,
    };
    messages.value.push(assistantMessage);
    return assistantMessage;
  }

  function updateLastAssistant(updater: (message: ChatMessage) => ChatMessage) {
    const assistantMessage = ensureAssistantMessage();
    const lastIndex = messages.value.length - 1;
    messages.value[lastIndex] = updater({
      ...assistantMessage,
      segments: assistantMessage.segments ? [...assistantMessage.segments] : [],
      toolSteps: assistantMessage.toolSteps ? [...assistantMessage.toolSteps] : [],
    });
  }

  function startAssistantMessage() {
    ensureAssistantMessage();
  }

  function appendAssistantChunk(chunk: string) {
    updateLastAssistant((message) => ({
      ...message,
      content: message.content + chunk,
      segments:
        mergeSegments(message.segments, [
          {
            type: "text",
            content: chunk,
            timestamp: Date.now(),
          },
        ]) ?? [],
    }));
  }

  function appendReasoningChunk(chunk: string) {
    updateLastAssistant((message) => {
      const segments = message.segments ?? [];
      const lastSegment = segments[segments.length - 1];

      if (lastSegment?.type === "reasoning") {
        const updatedSegments = [...segments];
        updatedSegments[segments.length - 1] = {
          ...lastSegment,
          content: lastSegment.content + chunk,
        };
        return {
          ...message,
          segments: updatedSegments,
        };
      }

      return {
        ...message,
        segments: [
          ...segments,
          {
            type: "reasoning" as const,
            content: chunk,
            timestamp: Date.now(),
          },
        ],
      };
    });
  }

  function upsertToolStep(toolStep: Partial<ChatToolStep>) {
    updateLastAssistant((message) => {
      const nextSteps = [...(message.toolSteps ?? [])];
      const targetId = toolStep.toolCallId ?? toolStep.id ?? "";
      const targetIndex = nextSteps.findIndex(
        (step) =>
          (!!targetId && (step.toolCallId === targetId || step.id === targetId)) ||
          (!!toolStep.toolName &&
            !targetId &&
            step.toolName === toolStep.toolName &&
            step.status === "running"),
      );

      const previous = targetIndex >= 0 ? nextSteps[targetIndex] : undefined;
      const merged: ChatToolStep = {
        id: (toolStep.id ?? previous?.id ?? targetId) || undefined,
        toolCallId:
          (toolStep.toolCallId ?? previous?.toolCallId ?? targetId) || undefined,
        toolName: toolStep.toolName ?? previous?.toolName ?? "",
        skillName: toolStep.skillName ?? previous?.skillName,
        arguments: toolStep.arguments ?? previous?.arguments,
        command: toolStep.command ?? previous?.command,
        input: toolStep.input ?? previous?.input,
        result: toolStep.result ?? previous?.result,
        output: toolStep.output ?? previous?.output,
        isError: toolStep.isError ?? previous?.isError ?? false,
        artifacts: toolStep.artifacts ?? previous?.artifacts,
        status:
          toolStep.status ??
          (toolStep.isError
            ? "error"
            : toolStep.result || previous?.result
              ? "success"
              : (previous?.status ?? "running")),
        timestamp: toolStep.timestamp ?? previous?.timestamp ?? Date.now(),
      };

      if (targetIndex >= 0) {
        nextSteps[targetIndex] = merged;
      } else {
        nextSteps.push(merged);
      }

      const nextSegments =
        mergeSegments(message.segments, [
          {
            ...merged,
            type: "tool" as const,
          },
        ]) ?? [];

      return {
        ...message,
        segments: nextSegments,
        toolSteps: nextSteps,
        isToolStepsExpanded: true,
      };
    });
  }

  function failLatestAssistantMessage(errorMessage?: string) {
    updateLastAssistant((message) => {
      let normalizedError = (errorMessage ?? "").trim() || "发生错误";

      if (normalizedError.includes("{")) {
        try {
          const jsonMatch = normalizedError.match(/\{.*\}/);
          if (jsonMatch) {
            const errorObj = JSON.parse(jsonMatch[0]);
            const innerMessage = errorObj.error?.message || errorObj.message;
            if (innerMessage) normalizedError = innerMessage;
          }
        } catch {
          // no-op
        }
      }

      if (
        normalizedError.toLowerCase().includes("context size") ||
        normalizedError.toLowerCase().includes("context_length_exceeded") ||
        normalizedError.toLowerCase().includes("too many tokens")
      ) {
        normalizedError = "对话上下文过长，请尝试开启新对话或清理历史记录。";
      } else if (normalizedError.includes("聊天处理失败:")) {
        normalizedError = normalizedError.replace("聊天处理失败:", "").trim();
      }

      const nextToolSteps = (message.toolSteps ?? []).map((step) => {
        if (step.status !== "running") return step;
        return {
          ...step,
          isError: true,
          status: "error" as const,
          result: step.result ?? normalizedError,
          output: step.output ?? step.result ?? normalizedError,
        };
      });

      const nextSegments = (message.segments ?? []).map((segment) => {
        if (segment.type !== "tool" || segment.status !== "running") return segment;
        return {
          ...segment,
          isError: true,
          status: "error" as const,
          result: segment.result ?? normalizedError,
          output: segment.output ?? segment.result ?? normalizedError,
        };
      });

      const fallbackContent = `⚠️ ${normalizedError}`;
      return {
        ...message,
        content: message.content?.trim() ? message.content : fallbackContent,
        segments:
          nextSegments.length > 0
            ? nextSegments
            : [
                {
                  type: "text" as const,
                  content: fallbackContent,
                  timestamp: Date.now(),
                },
              ],
        toolSteps: nextToolSteps,
        isToolStepsExpanded: true,
      };
    });
  }

  async function syncLatestAssistantMessage() {
    if (currentSessionId.value == null) {
      return null;
    }

    try {
      const params = new URLSearchParams({
        size: "1",
        userId: sessionStore.userId,
        sessionId: currentSessionId.value.toString(),
      });
      const res = await fetch(getFullUrl(`/api/chat/turns?${params}`));
      if (!res.ok) throw new Error("History sync failed");

      const turns: TurnHistoryItem[] = await res.json();
      const formattedMessages = mapTurnsToMessages(turns);
      const latestAssistant = formattedMessages.find(
        (message) => message.role === "assistant",
      );

      if (!latestAssistant) return null;

      const lastAssistantIndex = [...messages.value]
        .map((message, index) => ({ message, index }))
        .reverse()
        .find((entry) => entry.message.role === "assistant")?.index;

      if (lastAssistantIndex == null) {
        messages.value.push(latestAssistant);
        return latestAssistant;
      }

      const currentMessage = messages.value[lastAssistantIndex];
      const currentReasoningSegments = (currentMessage.segments ?? []).filter(
        (segment) => segment.type === "reasoning",
      );
      const currentToolSegments = (currentMessage.segments ?? []).filter(
        (segment) => segment.type === "tool",
      );
      const latestNonReasoningSegments = (latestAssistant.segments ?? []).filter(
        (segment) => segment.type !== "reasoning",
      );
      const latestHasToolSegments = latestNonReasoningSegments.some(
        (segment) => segment.type === "tool",
      );

      const mergedSegments = [
        ...currentReasoningSegments,
        ...(latestHasToolSegments
          ? latestNonReasoningSegments
          : mergeSegments(currentToolSegments, latestNonReasoningSegments) ?? latestNonReasoningSegments),
      ];

      messages.value[lastAssistantIndex] = {
        ...currentMessage,
        ...latestAssistant,
        isLoading: false,
        segments:
          mergedSegments.length > 0 ? mergedSegments : latestAssistant.segments,
        toolSteps:
          latestAssistant.toolSteps && latestAssistant.toolSteps.length > 0
            ? latestAssistant.toolSteps
            : currentMessage.toolSteps,
      };

      return messages.value[lastAssistantIndex];
    } catch (error) {
      console.error("Sync latest assistant message error:", error);
      return null;
    }
  }

  async function loadHistory(
    size: number = 20,
    options?: { reset?: boolean; sessionId?: number | null },
  ): Promise<boolean> {
    const targetSessionId = options?.sessionId ?? currentSessionId.value;
    if (targetSessionId == null) {
      resetConversationState();
      return false;
    }

    if (isLoadingHistory.value || (!hasMoreHistory.value && !options?.reset)) {
      return false;
    }

    if (options?.reset) {
      resetConversationState();
    }

    isLoadingHistory.value = true;

    try {
      const params = new URLSearchParams({
        size: size.toString(),
        userId: sessionStore.userId,
        sessionId: targetSessionId.toString(),
      });
      if (!options?.reset && oldestMessageId.value) {
        params.append("beforeId", oldestMessageId.value.toString());
      }

      const res = await fetch(getFullUrl(`/api/chat/turns?${params}`));
      if (!res.ok) throw new Error("History fetch failed");

      const turns: TurnHistoryItem[] = await res.json();

      if (turns.length === 0) {
        hasMoreHistory.value = false;
        if (options?.reset) {
          messages.value = [];
        }
        return false;
      }

      const formattedMessages = mapTurnsToMessages(turns);

      if (turns.length < size) {
        hasMoreHistory.value = false;
      }

      const oldestTurn = turns[turns.length - 1];
      if (oldestTurn) {
        oldestMessageId.value = oldestTurn.id;
      }

      messages.value = [...formattedMessages, ...messages.value];
      return true;
    } catch (err) {
      console.error("Load history error:", err);
      return false;
    } finally {
      isLoadingHistory.value = false;
    }
  }

  async function initWelcome() {
    try {
      const welcomeParams = new URLSearchParams({ userId: sessionStore.userId });
      if (currentSessionId.value != null) {
        welcomeParams.set("sessionId", currentSessionId.value.toString());
      }
      const res = await fetch(getFullUrl(`/api/chat/welcome?${welcomeParams}`));
      if (!res.ok) throw new Error("Welcome stream failed");

      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      if (!reader) return;

      welcomeGreeting.value = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          if (line.trim().startsWith("data:")) {
            const content = line.replace(/^data:\s?/, "");
            welcomeGreeting.value += content;
          }
        }
      }
    } catch (err) {
      console.error("Welcome fetch error:", err);
      welcomeGreeting.value = "欢迎回来。今天有什么我可以帮你的吗？";
    }
  }

  async function initChat() {
    await sessionStore.ensureActiveSession();
    await loadHistory(20, { reset: true });
  }

  async function sendMessage(scrollCallback?: () => void) {
    const text = inputMessage.value.trim();
    const currentImages = [...inputImages.value];
    if (
      (!text && currentImages.length === 0) ||
      isTyping.value ||
      currentSessionId.value == null
    ) {
      return;
    }

    messages.value.push({
      role: "user",
      content: text,
      timestamp: Date.now(),
      ...(currentImages.length > 0 ? { images: currentImages } : {}),
    });

    inputMessage.value = "";
    inputImages.value = [];
    isTyping.value = true;
    scrollCallback?.();

    const assistantMessage: ChatMessage = {
      role: "assistant",
      content: "",
      timestamp: Date.now(),
    };
    messages.value.push(assistantMessage);

    try {
      const payload: Record<string, any> = {
        message: text,
        userId: sessionStore.userId,
        sessionId: currentSessionId.value,
      };
      if (currentImages.length > 0) {
        payload.images = currentImages;
      }

      const res = await fetch(getFullUrl("/api/chat/stream"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!res.ok) throw new Error("Stream request failed");

      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      if (!reader) throw new Error("No reader found");

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed) continue;

          if (trimmed.startsWith("data:")) {
            const dataMatch = line.match(/^(\s*data:\s?)/);
            if (dataMatch) {
              const prefixLen = dataMatch[0].length;
              const content = line.slice(prefixLen).replace(/\r$/, "");

              const targetIdx = messages.value.length - 1;
              if (
                targetIdx >= 0 &&
                messages.value[targetIdx].role === "assistant"
              ) {
                messages.value[targetIdx] = {
                  ...messages.value[targetIdx],
                  content: messages.value[targetIdx].content + content,
                };
              }

              scrollCallback?.();
            }
          }
        }
      }
    } catch (err) {
      console.error("Streaming error:", err);
      const lastMsg = messages.value[messages.value.length - 1];
      if (lastMsg && lastMsg.role === "assistant") {
        lastMsg.content = "⚠️ 系统中枢连接异常，此时无法建立流式传输。";
      }
    } finally {
      isTyping.value = false;
      scrollCallback?.();
    }
  }

  async function clearHistory() {
    if (currentSessionId.value == null) {
      resetConversationState();
      return;
    }

    try {
      const params = new URLSearchParams({
        userId: sessionStore.userId,
        sessionId: currentSessionId.value.toString(),
      });
      const res = await fetch(getFullUrl(`/api/chat/turns?${params}`), {
        method: "DELETE",
      });
      if (!res.ok) throw new Error("Clear history failed");

      resetConversationState();
      hasMoreHistory.value = false;
    } catch (err) {
      console.error("Clear history error:", err);
      throw err;
    }
  }

  function formatTime(ts: number): string {
    const diff = Math.floor((Date.now() - ts) / 1000);
    if (diff < 60) return "刚刚";
    if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
    return new Date(ts).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  watch(
    () => sessionStore.activeSessionId,
    async (nextSessionId, previousSessionId) => {
      if (nextSessionId === previousSessionId) {
        return;
      }

      resetConversationState();
      isTyping.value = false;

      if (nextSessionId != null) {
        await loadHistory(20, { reset: true, sessionId: nextSessionId });
      }
    },
  );

  return {
    messages,
    inputMessage,
    inputImages,
    isTyping,
    welcomeGreeting,
    isLoadingHistory,
    hasMoreHistory,
    currentSessionId,
    initWelcome,
    initChat,
    loadHistory,
    startAssistantMessage,
    appendAssistantChunk,
    appendReasoningChunk,
    upsertToolStep,
    failLatestAssistantMessage,
    syncLatestAssistantMessage,
    sendMessage,
    clearHistory,
    resetConversationState,
    formatTime,
  };
}
