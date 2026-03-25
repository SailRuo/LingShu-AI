import { ref } from "vue";
import type { ChatMessage, ChatToolStep } from "@/types";

interface HistoryToolStep {
  id?: string;
  name?: string;
  toolName?: string;
  toolCallId?: string;
  arguments?: string;
  command?: string;
  input?: string;
  result?: string;
  output?: string;
  isError?: boolean;
}

interface HistoryMessage {
  id: number;
  role: string;
  content: string;
  timestamp: string;
  toolSteps?: HistoryToolStep[];
  toolCalls?: string;
  toolCallId?: string;
  toolName?: string;
}

function toTimestamp(value?: string): number {
  if (!value) {
    return Date.now();
  }

  const timestamp = new Date(value).getTime();
  return Number.isNaN(timestamp) ? Date.now() : timestamp;
}

function normalizeToolStep(step: HistoryToolStep): ChatToolStep {
  return {
    id: step.id ?? step.toolCallId,
    toolCallId: step.toolCallId ?? step.id,
    toolName: step.toolName ?? step.name ?? "",
    arguments: step.arguments ?? step.command ?? step.input,
    command: step.command,
    input: step.input,
    result: step.result ?? step.output,
    output: step.output ?? step.result,
    isError: step.isError ?? false,
  };
}

function parseToolCalls(toolCalls?: string): ChatToolStep[] {
  if (!toolCalls?.trim()) {
    return [];
  }

  try {
    const parsed = JSON.parse(toolCalls);
    if (!Array.isArray(parsed)) {
      return [];
    }

    return parsed.map((call) =>
      normalizeToolStep({
        id: typeof call?.id === "string" ? call.id : undefined,
        name: typeof call?.name === "string" ? call.name : undefined,
        arguments:
          typeof call?.arguments === "string" ? call.arguments : undefined,
      })
    );
  } catch {
    return [];
  }
}

function attachToolResult(toolSteps: ChatToolStep[], message: HistoryMessage) {
  const toolCallId = message.toolCallId ?? "";
  const fallbackStep: ChatToolStep = {
    id: toolCallId || undefined,
    toolCallId: toolCallId || undefined,
    toolName: message.toolName ?? "",
    result: message.content ?? "",
    output: message.content ?? "",
  };

  if (!toolSteps.length) {
    toolSteps.push(fallbackStep);
    return;
  }

  if (toolCallId) {
    for (let i = toolSteps.length - 1; i >= 0; i--) {
      const step = toolSteps[i];
      if (step.id === toolCallId || step.toolCallId === toolCallId) {
        toolSteps[i] = {
          ...step,
          result: message.content ?? "",
          output: message.content ?? "",
          toolName: step.toolName || message.toolName || "",
        };
        return;
      }
    }
  }

  const lastIndex = toolSteps.length - 1;
  toolSteps[lastIndex] = {
    ...toolSteps[lastIndex],
    result: message.content ?? "",
    output: message.content ?? "",
    toolName: toolSteps[lastIndex].toolName || message.toolName || "",
  };
}

function aggregateHistoryMessages(historyMessages: HistoryMessage[]): ChatMessage[] {
  const chronological = [...historyMessages].reverse();
  const aggregated: ChatMessage[] = [];

  let index = 0;
  while (index < chronological.length) {
    const current = chronological[index];

    if (current.role === "user") {
      aggregated.push({
        role: "user",
        content: current.content ?? "",
        timestamp: toTimestamp(current.timestamp),
      });
      index++;
      continue;
    }

    if (current.role === "assistant") {
      const contentParts: string[] = [];
      const toolSteps: ChatToolStep[] = current.toolSteps?.map(normalizeToolStep) ?? [];

      while (index < chronological.length) {
        const message = chronological[index];

        if (message.role !== "assistant" && message.role !== "tool") {
          break;
        }

        if (message.role === "assistant") {
          const content = message.content?.trim();
          if (content) {
            contentParts.push(content);
          }

          if (message.toolSteps?.length) {
            toolSteps.push(...message.toolSteps.map(normalizeToolStep));
          } else if (message.toolCalls) {
            toolSteps.push(...parseToolCalls(message.toolCalls));
          }

          index++;
          continue;
        }

        attachToolResult(toolSteps, message);
        index++;
      }

      aggregated.push({
        role: "assistant",
        content: contentParts.join("\n\n"),
        timestamp: toTimestamp(current.timestamp),
        toolSteps: toolSteps.length ? toolSteps : undefined,
        isToolStepsExpanded: false,
      });
      continue;
    }

    if (current.role === "tool") {
      const toolSteps: ChatToolStep[] = [];

      while (index < chronological.length && chronological[index].role === "tool") {
        attachToolResult(toolSteps, chronological[index]);
        index++;
      }

      aggregated.push({
        role: "assistant",
        content: "",
        timestamp: toTimestamp(current.timestamp),
        toolSteps: toolSteps.length ? toolSteps : undefined,
        isToolStepsExpanded: false,
      });
      continue;
    }

    index++;
  }

  return aggregated;
}

function mergeToolSteps(
  previousSteps?: ChatToolStep[],
  nextSteps?: ChatToolStep[]
): ChatToolStep[] | undefined {
  const merged = [...(previousSteps ?? []), ...(nextSteps ?? [])];
  return merged.length ? merged : undefined;
}

function mergeMessageBatches(
  olderMessages: ChatMessage[],
  currentMessages: ChatMessage[]
): ChatMessage[] {
  if (!olderMessages.length) {
    return currentMessages;
  }

  if (!currentMessages.length) {
    return olderMessages;
  }

  const lastOlder = olderMessages[olderMessages.length - 1];
  const firstCurrent = currentMessages[0];

  if (lastOlder.role !== "assistant" || firstCurrent.role !== "assistant") {
    return [...olderMessages, ...currentMessages];
  }

  return [
    ...olderMessages.slice(0, -1),
    {
      role: "assistant",
      content: [lastOlder.content, firstCurrent.content]
        .filter((part) => part?.trim())
        .join("\n\n"),
      timestamp: lastOlder.timestamp,
      toolSteps: mergeToolSteps(lastOlder.toolSteps, firstCurrent.toolSteps),
      isToolStepsExpanded: false,
    },
    ...currentMessages.slice(1),
  ];
}

export function useChat() {
  const messages = ref<ChatMessage[]>([]);
  const inputMessage = ref("");
  const isTyping = ref(false);
  const welcomeGreeting = ref("欢迎回来");
  const isLoadingHistory = ref(false);
  const hasMoreHistory = ref(true);
  const oldestMessageId = ref<number | null>(null);

  async function loadHistory(size: number = 20): Promise<boolean> {
    if (isLoadingHistory.value || !hasMoreHistory.value) {
      return false;
    }

    isLoadingHistory.value = true;

    try {
      const params = new URLSearchParams({ size: size.toString() });
      if (oldestMessageId.value) {
        params.append("beforeId", oldestMessageId.value.toString());
      }

      const res = await fetch(`/api/chat/history?${params}`);
      if (!res.ok) throw new Error("History fetch failed");

      const historyMessages: HistoryMessage[] = await res.json();

      if (historyMessages.length === 0) {
        hasMoreHistory.value = false;
        return false;
      }

      const formattedMessages = aggregateHistoryMessages(historyMessages);

      if (historyMessages.length < size) {
        hasMoreHistory.value = false;
      }

      const oldestMsg = historyMessages[historyMessages.length - 1];
      if (oldestMsg) {
        oldestMessageId.value = oldestMsg.id;
      }

      messages.value = mergeMessageBatches(formattedMessages, messages.value);

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
      const res = await fetch("/api/chat/welcome");
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
        let lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          if (line.trim().startsWith("data:")) {
            let content = line.replace(/^data:\s?/, "");
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
    await loadHistory(20);
  }

  async function sendMessage(scrollCallback?: () => void) {
    const text = inputMessage.value.trim();
    if (!text || isTyping.value) return;

    messages.value.push({ role: "user", content: text, timestamp: Date.now() });
    inputMessage.value = "";
    isTyping.value = true;
    scrollCallback?.();

    const assistantMessage: ChatMessage = {
      role: "assistant",
      content: "",
      timestamp: Date.now(),
    };
    messages.value.push(assistantMessage);

    try {
      const res = await fetch("/api/chat/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: text }),
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

        let lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (let line of lines) {
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

  function formatTime(ts: number): string {
    const diff = Math.floor((Date.now() - ts) / 1000);
    if (diff < 60) return "刚刚";
    if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
    return new Date(ts).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  return {
    messages,
    inputMessage,
    isTyping,
    welcomeGreeting,
    isLoadingHistory,
    hasMoreHistory,
    initWelcome,
    initChat,
    loadHistory,
    sendMessage,
    formatTime,
  };
}
