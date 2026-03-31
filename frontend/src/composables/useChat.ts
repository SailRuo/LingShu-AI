import { ref } from "vue";
import { getFullUrl } from "@/utils/request";
import type {
  ChatMessage,
  ChatMessageSegment,
  ChatToolSegment,
  ChatToolStep,
} from "@/types";

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
  const result = step.result ?? step.output;
  const isError = step.isError ?? false;
  return {
    id: step.id ?? step.toolCallId,
    toolCallId: step.toolCallId ?? step.id,
    toolName: step.toolName ?? step.name ?? "",
    arguments: step.arguments ?? step.command ?? step.input,
    command: step.command,
    input: step.input,
    result,
    output: step.output ?? step.result,
    isError,
    status: isError ? "error" : result ? "success" : "running",
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
    status: "success",
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
          status: message.content ? "success" : step.status ?? "running",
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
    status: message.content ? "success" : toolSteps[lastIndex].status ?? "running",
  };
}

function getToolStepKey(step: Partial<ChatToolStep>, fallbackIndex: number): string {
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

function mergeToolStepLists(
  previousSteps?: ChatToolStep[],
  nextSteps?: ChatToolStep[]
): ChatToolStep[] | undefined {
  const merged = new Map<string, ChatToolStep>();

  [...(previousSteps ?? []), ...(nextSteps ?? [])].forEach((step, index) => {
    const key = getToolStepKey(step, index);
    const previous = merged.get(key);
    merged.set(key, {
      ...previous,
      ...step,
      id: step.id ?? previous?.id,
      toolCallId: step.toolCallId ?? previous?.toolCallId,
      toolName: step.toolName ?? previous?.toolName ?? "",
      arguments: step.arguments ?? previous?.arguments,
      command: step.command ?? previous?.command,
      input: step.input ?? previous?.input,
      result: step.result ?? previous?.result,
      output: step.output ?? previous?.output,
      isError: step.isError ?? previous?.isError ?? false,
      status: step.status ?? previous?.status,
      timestamp: step.timestamp ?? previous?.timestamp,
    });
  });

  const result = Array.from(merged.values());
  return result.length ? result : undefined;
}

function buildToolStepsFromSegments(segments?: ChatMessageSegment[]): ChatToolStep[] | undefined {
  const toolSteps = segments?.filter(
    (segment): segment is ChatToolSegment => segment.type === "tool"
  );
  return mergeToolStepLists(undefined, toolSteps);
}

function mergeSegments(
  previousSegments?: ChatMessageSegment[],
  nextSegments?: ChatMessageSegment[]
): ChatMessageSegment[] | undefined {
  const merged: ChatMessageSegment[] = [];
  const toolSegments = new Map<string, number>();

  [...(previousSegments ?? []), ...(nextSegments ?? [])].forEach((segment, index) => {
    if (segment.type === "text") {
      const content = segment.content ?? "";
      if (!content) {
        return;
      }

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
      arguments: toolSegment.arguments ?? previous.arguments,
      command: toolSegment.command ?? previous.command,
      input: toolSegment.input ?? previous.input,
      result: toolSegment.result ?? previous.result,
      output: toolSegment.output ?? previous.output,
      isError: toolSegment.isError ?? previous.isError ?? false,
      status: toolSegment.status ?? previous.status,
      timestamp: toolSegment.timestamp ?? previous.timestamp,
    };
  });

  return merged.length ? merged : undefined;
}

function aggregateHistoryMessages(historyMessages: HistoryMessage[]): ChatMessage[] {
  const chronological = [...historyMessages].reverse();
  const aggregated: ChatMessage[] = [];

  let index = 0;
  while (index < chronological.length) {
    const current = chronological[index];

    if (current.role === "user") {
      let content = current.content ?? "";
      let images: string[] | undefined = undefined;

      // 如果内容是 JSON 数组（多模态格式），进行解析
      if (content.trim().startsWith('[')) {
        try {
          const parsed = JSON.parse(content);
          if (Array.isArray(parsed) && parsed.some((p: any) => p.type === 'text' || p.type === 'image')) {
            let extractedContent = "";
            let extractedImages: string[] = [];
            for (const part of parsed) {
              if (part.type === 'text') {
                extractedContent += (part.text || part.content || "");
              } else if (part.type === 'image') {
                if (part.base64) {
                  extractedImages.push(`data:${part.mimeType || 'image/jpeg'};base64,${part.base64}`);
                } else if (part.url) {
                  extractedImages.push(part.url);
                }
              }
            }
            content = extractedContent;
            if (extractedImages.length > 0) {
              images = extractedImages;
            }
          }
        } catch (e) {
          // 如果解析失败，保持原样
        }
      }

      aggregated.push({
        id: current.id,
        role: "user",
        content: content,
        timestamp: toTimestamp(current.timestamp),
        images: images
      });
      index++;
      continue;
    }

    if (current.role === "assistant") {
      let segments: ChatMessageSegment[] = [];
      let toolSteps: ChatToolStep[] = mergeToolStepLists(
        undefined,
        current.toolSteps?.map(normalizeToolStep)
      ) ?? [];

      if (toolSteps.length) {
        segments = mergeSegments(
          segments,
          toolSteps.map((step) => ({ ...step, type: "tool" as const }))
        ) ?? [];
      }

      while (index < chronological.length) {
        const message = chronological[index];

        if (message.role !== "assistant" && message.role !== "tool") {
          break;
        }

        if (message.role === "assistant") {
          const content = message.content?.trim();
          if (content) {
            segments = mergeSegments(segments, [
              {
                type: "text",
                content: content,
                timestamp: toTimestamp(message.timestamp),
              },
            ]) ?? [];
          }

          if (message.toolSteps?.length) {
            const nextToolSteps = message.toolSteps.map(normalizeToolStep);
            toolSteps = mergeToolStepLists(toolSteps, nextToolSteps) ?? [];
            segments = mergeSegments(
              segments,
              nextToolSteps.map((step) => ({ ...step, type: "tool" as const }))
            ) ?? [];
          } else if (message.toolCalls) {
            const nextToolSteps = parseToolCalls(message.toolCalls);
            toolSteps = mergeToolStepLists(toolSteps, nextToolSteps) ?? [];
            segments = mergeSegments(
              segments,
              nextToolSteps.map((step) => ({ ...step, type: "tool" as const }))
            ) ?? [];
          }

          index++;
          continue;
        }

        attachToolResult(toolSteps, message);
        segments = mergeSegments(
          segments,
          toolSteps.map((step) => ({ ...step, type: "tool" as const }))
        ) ?? [];
        index++;
      }

      aggregated.push({
        id: current.id,
        role: "assistant",
        content: segments
          .filter((segment): segment is Extract<ChatMessageSegment, { type: "text" }> => segment.type === "text")
          .map((segment) => segment.content)
          .join(""),
        timestamp: toTimestamp(current.timestamp),
        segments: segments.length ? segments : undefined,
        toolSteps: buildToolStepsFromSegments(segments) ?? (toolSteps.length ? toolSteps : undefined),
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

      const segments = mergeSegments(
        undefined,
        toolSteps.map((step) => ({ ...step, type: "tool" as const }))
      );

      aggregated.push({
        id: current.id,
        role: "assistant",
        content: "",
        timestamp: toTimestamp(current.timestamp),
        segments,
        toolSteps: buildToolStepsFromSegments(segments) ?? (toolSteps.length ? toolSteps : undefined),
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
  return mergeToolStepLists(previousSteps, nextSteps);
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
      id: firstCurrent.id ?? lastOlder.id,
      role: "assistant",
      content: [lastOlder.content, firstCurrent.content]
        .filter((part) => part?.trim())
        .join("\n\n"),
      timestamp: lastOlder.timestamp,
      segments: mergeSegments(lastOlder.segments, firstCurrent.segments),
      toolSteps: mergeToolSteps(lastOlder.toolSteps, firstCurrent.toolSteps),
      isToolStepsExpanded: false,
    },
    ...currentMessages.slice(1),
  ];
}

export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const inputMessage = ref('')
  const inputImages = ref<string[]>([])
  const isTyping = ref(false);
  const welcomeGreeting = ref("欢迎回来");
  const isLoadingHistory = ref(false);
  const hasMoreHistory = ref(true);
  const oldestMessageId = ref<number | null>(null);

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
      isLoading: true,  // 标记为加载状态
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
          (!!toolStep.toolName && !targetId && step.toolName === toolStep.toolName && step.status === "running")
      );

      const previous = targetIndex >= 0 ? nextSteps[targetIndex] : undefined;
      const merged: ChatToolStep = {
        id: (toolStep.id ?? previous?.id ?? targetId) || undefined,
        toolCallId: (toolStep.toolCallId ?? previous?.toolCallId ?? targetId) || undefined,
        toolName: toolStep.toolName ?? previous?.toolName ?? "",
        arguments: toolStep.arguments ?? previous?.arguments,
        command: toolStep.command ?? previous?.command,
        input: toolStep.input ?? previous?.input,
        result: toolStep.result ?? previous?.result,
        output: toolStep.output ?? previous?.output,
        isError: toolStep.isError ?? previous?.isError ?? false,
        status:
          toolStep.status ??
          (toolStep.isError ? "error" : toolStep.result || previous?.result ? "success" : previous?.status ?? "running"),
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
      
      // 尝试解析 JSON 格式的错误消息
      if (normalizedError.includes('{')) {
        try {
          // 提取 JSON 部分
          const jsonMatch = normalizedError.match(/\{.*\}/);
          if (jsonMatch) {
            const errorObj = JSON.parse(jsonMatch[0]);
            const innerMessage = errorObj.error?.message || errorObj.message;
            if (innerMessage) {
              normalizedError = innerMessage;
            }
          }
        } catch (e) {
          // 解析失败则保持原样
        }
      }

      // 针对特定错误的友好提示
      if (normalizedError.toLowerCase().includes('context size') || 
          normalizedError.toLowerCase().includes('context_length_exceeded') ||
          normalizedError.toLowerCase().includes('too many tokens')) {
        normalizedError = "对话上下文过长，请尝试开启新对话或清理历史记录。";
      } else if (normalizedError.includes('聊天处理失败:')) {
        normalizedError = normalizedError.replace('聊天处理失败:', '').trim();
      }

      const nextToolSteps = (message.toolSteps ?? []).map((step) => {
        if (step.status !== "running") {
          return step;
        }

        return {
          ...step,
          isError: true,
          status: "error" as const,
          result: step.result ?? normalizedError,
          output: step.output ?? step.result ?? normalizedError,
        };
      });

      const nextSegments = (message.segments ?? []).map((segment) => {
        if (segment.type !== "tool" || segment.status !== "running") {
          return segment;
        }

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
    try {
      const res = await fetch(getFullUrl("/api/chat/history?size=6"));
      if (!res.ok) {
        throw new Error("History sync failed");
      }

      const historyMessages: HistoryMessage[] = await res.json();
      const formattedMessages = aggregateHistoryMessages(historyMessages);
      const latestAssistant = [...formattedMessages]
        .reverse()
        .find((message) => message.role === "assistant");

      if (!latestAssistant) {
        return null;
      }

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
        (segment) => segment.type === "reasoning"
      );
      
      const mergedSegments = [
        ...currentReasoningSegments,
        ...(latestAssistant.segments ?? []).filter(
          (segment) => segment.type !== "reasoning"
        ),
      ];

      messages.value[lastAssistantIndex] = {
        ...currentMessage,
        ...latestAssistant,
        segments: mergedSegments.length > 0 ? mergedSegments : latestAssistant.segments,
      };
      
      return latestAssistant;
    } catch (error) {
      console.error("Sync latest assistant message error:", error);
      return null;
    }
  }

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

      const res = await fetch(getFullUrl(`/api/chat/history?${params}`));
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
      const res = await fetch(getFullUrl("/api/chat/welcome"));
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
    const currentImages = [...inputImages.value];
    if ((!text && currentImages.length === 0) || isTyping.value) return;

    messages.value.push({ 
      role: "user", 
      content: text, 
      timestamp: Date.now(),
      ...(currentImages.length > 0 ? { images: currentImages } : {})
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
      const payload: Record<string, any> = { message: text };
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

  async function clearHistory() {
    try {
      const res = await fetch(getFullUrl("/api/chat/history"), {
        method: "DELETE",
      });
      if (!res.ok) throw new Error("Clear history failed");

      messages.value = [];
      hasMoreHistory.value = false;
      oldestMessageId.value = null;
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

  return {
    messages,
    inputMessage,
    inputImages,
    isTyping,
    welcomeGreeting,
    isLoadingHistory,
    hasMoreHistory,
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
    formatTime,
  };
}
