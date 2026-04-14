import html2canvas from "html2canvas";
import type { ChatMessage, ChatMessageSegment } from "@/types";

export type ExportFormat = "markdown" | "png";
export type ExportRange = "all" | "recent10";

export interface ExportOptions {
  range: ExportRange;
  title?: string;
}

interface ExportMessage {
  role: "user" | "assistant";
  content: string;
  timestamp: number;
  images?: string[];
}

function getTimeLabel(timestamp: number): string {
  const date = new Date(timestamp);
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}:${String(date.getSeconds()).padStart(2, "0")}`;
}

function getDateTimeLabel(timestamp: number): string {
  const date = new Date(timestamp);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${y}-${m}-${d} ${hh}:${mm}:${ss}`;
}

function buildTimestampForFile(): string {
  const date = new Date();
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${y}-${m}-${d}_${hh}-${mm}-${ss}`;
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function escapeMarkdown(text: string): string {
  return text
    .replace(/\\/g, "\\\\")
    .replace(/([*_\[\]()#+\-.!|>])/g, "\\$1");
}

function isTextSegment(segment: ChatMessageSegment): segment is ChatMessageSegment & { type: "text"; content: string } {
  return segment.type === "text" && "content" in segment && typeof segment.content === "string";
}

function extractAssistantContent(message: ChatMessage): string {
  if (Array.isArray(message.segments) && message.segments.length > 0) {
    const textContent = message.segments
      .filter(isTextSegment)
      .map((segment) => segment.content)
      .join("\n")
      .trim();
    if (textContent) {
      return textContent;
    }
  }
  return (message.content || "").trim();
}

function normalizeMessages(messages: ChatMessage[], range: ExportRange): ExportMessage[] {
  const base = range === "recent10" ? messages.slice(-10) : messages;
  return base
    .filter((message) => message.role === "user" || message.role === "assistant")
    .map((message) => {
      if (message.role === "assistant") {
        return {
          role: "assistant",
          content: extractAssistantContent(message),
          timestamp: message.timestamp,
        } satisfies ExportMessage;
      }
      return {
        role: "user",
        content: (message.content || "").trim(),
        timestamp: message.timestamp,
        images: message.images,
      } satisfies ExportMessage;
    })
    .filter((message) => message.content || (message.images && message.images.length > 0));
}

export function getExportMessageCount(messages: ChatMessage[], range: ExportRange): number {
  return normalizeMessages(messages, range).length;
}

export function exportConversationToMarkdown(messages: ChatMessage[], options: ExportOptions): void {
  const normalizedMessages = normalizeMessages(messages, options.range);
  if (!normalizedMessages.length) {
    throw new Error("暂无可导出的消息");
  }

  const firstTimestamp = normalizedMessages[0].timestamp;
  const lastTimestamp = normalizedMessages[normalizedMessages.length - 1].timestamp;
  const durationMinutes = Math.max(1, Math.round((lastTimestamp - firstTimestamp) / 60000));
  const title = options.title || "灵枢 AI 对话记录";

  const lines: string[] = [];
  lines.push(`# ${title}`);
  lines.push("");
  lines.push(`**导出时间**: ${getDateTimeLabel(Date.now())}  `);
  lines.push(`**消息数量**: ${normalizedMessages.length} 条  `);
  lines.push(`**会话时长**: ${durationMinutes} 分钟  `);
  lines.push("");
  lines.push("---");
  lines.push("");

  normalizedMessages.forEach((message) => {
    const roleLabel = message.role === "user" ? "👤 用户" : "🤖 灵枢";
    lines.push(`### ${roleLabel} (${getTimeLabel(message.timestamp)})`);
    lines.push("");

    if (message.content) {
      lines.push(escapeMarkdown(message.content));
      lines.push("");
    }

    if (message.images?.length) {
      lines.push(`_附带图片 ${message.images.length} 张（导出时省略图片内容）_`);
      lines.push("");
    }
  });

  const content = `${lines.join("\n")}\n`;
  const blob = new Blob([content], { type: "text/markdown;charset=utf-8" });
  downloadBlob(blob, `灵枢对话_${buildTimestampForFile()}.md`);
}

async function waitForImagesLoaded(container: HTMLElement): Promise<void> {
  const images = Array.from(container.querySelectorAll("img"));
  if (!images.length) {
    return;
  }
  await Promise.all(
    images.map(
      (img) =>
        new Promise<void>((resolve) => {
          if (img.complete) {
            resolve();
            return;
          }
          img.onload = () => resolve();
          img.onerror = () => resolve();
        }),
    ),
  );
}

function buildRenderContainer(messages: ExportMessage[]): HTMLDivElement {
  const container = document.createElement("div");
  container.style.position = "fixed";
  container.style.left = "-99999px";
  container.style.top = "0";
  container.style.width = "860px";
  container.style.padding = "32px";
  container.style.background = "#ffffff";
  container.style.color = "#111827";
  container.style.fontFamily = "'Segoe UI', 'PingFang SC', sans-serif";
  container.style.lineHeight = "1.6";

  const title = document.createElement("h1");
  title.textContent = "灵枢 AI 对话记录";
  title.style.margin = "0 0 8px";
  title.style.fontSize = "30px";
  title.style.color = "#0f172a";
  container.appendChild(title);

  const meta = document.createElement("p");
  meta.textContent = `导出时间：${getDateTimeLabel(Date.now())} ｜ 消息数：${messages.length}`;
  meta.style.margin = "0 0 24px";
  meta.style.color = "#475569";
  meta.style.fontSize = "14px";
  container.appendChild(meta);

  messages.forEach((message) => {
    const card = document.createElement("section");
    card.style.border = "1px solid #e2e8f0";
    card.style.borderRadius = "14px";
    card.style.padding = "14px 16px";
    card.style.marginBottom = "14px";
    card.style.background = message.role === "user" ? "#f8fafc" : "#f0fdf4";

    const header = document.createElement("div");
    header.style.display = "flex";
    header.style.justifyContent = "space-between";
    header.style.alignItems = "center";
    header.style.marginBottom = "8px";

    const role = document.createElement("strong");
    role.textContent = message.role === "user" ? "用户" : "灵枢";
    role.style.color = message.role === "user" ? "#1d4ed8" : "#166534";

    const time = document.createElement("span");
    time.textContent = getTimeLabel(message.timestamp);
    time.style.color = "#64748b";
    time.style.fontSize = "12px";

    header.appendChild(role);
    header.appendChild(time);
    card.appendChild(header);

    const body = document.createElement("p");
    body.textContent = message.content || "(空内容)";
    body.style.whiteSpace = "pre-wrap";
    body.style.margin = "0";
    body.style.fontSize = "15px";
    body.style.color = "#0f172a";
    card.appendChild(body);

    if (message.images?.length) {
      const imageWrap = document.createElement("div");
      imageWrap.style.display = "flex";
      imageWrap.style.gap = "8px";
      imageWrap.style.flexWrap = "wrap";
      imageWrap.style.marginTop = "10px";

      message.images.slice(0, 6).forEach((src) => {
        const img = document.createElement("img");
        img.src = src;
        img.style.width = "120px";
        img.style.height = "120px";
        img.style.objectFit = "cover";
        img.style.borderRadius = "8px";
        img.style.border = "1px solid #cbd5e1";
        imageWrap.appendChild(img);
      });

      card.appendChild(imageWrap);
    }

    container.appendChild(card);
  });

  return container;
}

export async function exportConversationToPng(messages: ChatMessage[], options: ExportOptions): Promise<void> {
  const normalizedMessages = normalizeMessages(messages, options.range);
  if (!normalizedMessages.length) {
    throw new Error("暂无可导出的消息");
  }

  const container = buildRenderContainer(normalizedMessages);
  document.body.appendChild(container);

  try {
    await waitForImagesLoaded(container);
    const canvas = await html2canvas(container, {
      scale: 2,
      useCORS: true,
      backgroundColor: "#ffffff",
      logging: false,
      windowWidth: 920,
    });

    await new Promise<void>((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (!blob) {
          reject(new Error("PNG 生成失败"));
          return;
        }
        downloadBlob(blob, `灵枢对话_${buildTimestampForFile()}.png`);
        resolve();
      }, "image/png");
    });
  } finally {
    if (container.parentElement) {
      container.parentElement.removeChild(container);
    }
  }
}
