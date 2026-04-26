import { ref, readonly, watch } from "vue";

function stripMarkdown(text: string): string {
  if (!text) return text;

  let result = text;

  result = result.replace(/!\[([^\]]*)\]\([^)]+\)/g, "");

  result = result.replace(/\[([^\]]+)\]\([^)]+\)/g, "$1");

  result = result.replace(/```[\s\S]*?```/g, "");
  result = result.replace(/`([^`]+)`/g, "$1");

  result = result.replace(/\*\*([^*]+)\*\*/g, "$1");
  result = result.replace(/__([^_]+)__/g, "$1");
  result = result.replace(/\*([^*]+)\*/g, "$1");
  result = result.replace(/_([^_]+)_/g, "$1");
  result = result.replace(/~~([^~]+)~~/g, "$1");

  result = result.replace(/^#{1,6}\s+/gm, "");

  result = result.replace(/^[*+-]\s+/gm, "");
  result = result.replace(/^\d+\.\s+/gm, "");

  result = result.replace(/^>\s*/gm, "");

  result = result.replace(/\n+/g, " ");
  result = result.replace(/\s{2,}/g, " ");
  result = result.trim();

  return result;
}

const isPlaying = ref(false);
const currentPlayingId = ref<string | null>(null);

// 从 localStorage 读取持久化的自动语音状态
const storedAutoTts = localStorage.getItem('lingshu_auto_tts');
const autoTtsEnabled = ref(storedAutoTts === 'true');

// 监听状态变化并持久化到 localStorage
watch(autoTtsEnabled, (newValue) => {
  localStorage.setItem('lingshu_auto_tts', String(newValue));
});

interface TtsChunk {
  text: string;
  audio: HTMLAudioElement | null;
  status: "idle" | "loading" | "ready" | "error";
  index: number;
}

const chunks = ref<TtsChunk[]>([]);
const currentIndex = ref(0);
const CONCURRENCY_LIMIT = 2; 

function getFullUrl(path: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  return `${baseUrl}${path}`;
}

function stop() {
  chunks.value.forEach((chunk) => {
    if (chunk.audio) {
      chunk.audio.pause();
      chunk.audio.src = "";
      chunk.audio.onended = null;
      chunk.audio.onerror = null;
    }
  });
  chunks.value = [];
  currentIndex.value = 0;
  isPlaying.value = false;
  currentPlayingId.value = null;
}

function loadChunk(index: number) {
  if (index >= chunks.value.length || chunks.value[index].status !== "idle") return;

  const chunk = chunks.value[index];
  // 默认使用 -1 作为 seed
  const audioUrl = getFullUrl(
    `/api/tts/speak?text=${encodeURIComponent(chunk.text)}&seed=-1`,
  );

  chunk.status = "loading";
  chunk.audio = new Audio(audioUrl);
  
  chunk.audio.oncanplaythrough = () => {
    if (chunk.status === "loading") {
      chunk.status = "ready";
      fillLoadWindow(); 
    }
  };

  chunk.audio.onended = () => {
    playNext();
  };

  chunk.audio.onerror = () => {
    console.error(`TTS Chunk ${index} load error`);
    chunk.status = "error";
    fillLoadWindow();
    if (currentIndex.value === index) playNext();
  };
}

function fillLoadWindow() {
  const loadingCount = chunks.value.filter(c => c.status === "loading").length;
  const nextToLoad = chunks.value.find(c => c.status === "idle");
  
  if (nextToLoad && loadingCount < CONCURRENCY_LIMIT) {
    loadChunk(nextToLoad.index);
    fillLoadWindow();
  }
}

async function playNext() {
  currentIndex.value++;
  
  if (currentIndex.value >= chunks.value.length) {
    stop(); 
    return;
  }

  const chunk = chunks.value[currentIndex.value];
  
  if (chunk.status === "error") {
    playNext();
    return;
  }

  try {
    if (chunk.status === "ready" && chunk.audio) {
        await chunk.audio.play();
    }
  } catch (e) {
    console.error("Play error:", e);
    playNext();
  }
}

watch([isPlaying, currentIndex, chunks], () => {
    if (!isPlaying.value) return;
    const current = chunks.value[currentIndex.value];
    if (current && current.status === "ready" && current.audio?.paused) {
        current.audio.play().catch(() => {});
    }
}, { deep: true });

async function speak(text: string, messageId?: string): Promise<void> {
  console.log('[TTS] speak called, text length:', text.length, 'messageId:', messageId);
  if (!text) return;

  if (isPlaying.value && currentPlayingId.value === messageId) {
    console.log('[TTS] Stopping current playback for same message');
    stop();
    return;
  }

  stop();
  isPlaying.value = true;
  currentPlayingId.value = messageId || null;

  // 按照中英文句号、问号、感叹号、换行符进行分段
  const textChunks = text
    .split(/([。！？\.!\?\n]+)/)
    .reduce((acc: string[], curr: string, i: number, arr: string[]) => {
      if (i % 2 === 0) {
        const punctuation = arr[i + 1] || '';
        const combined = curr + punctuation;
        const stripped = stripMarkdown(combined).trim();
        if (stripped.length > 0) {
          acc.push(stripped);
        }
      }
      return acc;
    }, []);

  console.log('[TTS] Parsed chunks count:', textChunks.length);

  if (textChunks.length === 0) {
    console.log('[TTS] No valid chunks, stopping');
    stop();
    return;
  }

  chunks.value = textChunks.map((t, i) => ({
    text: t,
    audio: null,
    status: "idle",
    index: i
  }));

  currentIndex.value = 0;
  console.log('[TTS] Starting playback, calling fillLoadWindow');
  fillLoadWindow();
}

// 增量添加文本（用于流式输出）
function appendText(text: string, messageId: string, isFinished: boolean = false) {
  console.log('[TTS] appendText called, text length:', text.length, 'isFinished:', isFinished, 'currentPlayingId:', currentPlayingId.value);
  
  if (!isPlaying.value || currentPlayingId.value !== messageId) {
    console.log('[TTS] Not playing this message, starting new playback');
    // 如果当前没有在播放这条消息，则启动播放
    speak(text, messageId);
    return;
  }

  // 按照中英文句号、问号、感叹号、换行符进行分段
  const parts = text.split(/([。！？\.!\?\n]+)/);
  const textChunks: string[] = [];
  
  for (let i = 0; i < parts.length; i += 2) {
    const content = parts[i];
    const punctuation = parts[i + 1] || '';
    
    // 如果是最后一部分且没有标点符号，且消息还没结束，则跳过（等待后续内容）
    if (i === parts.length - 1 && !punctuation && !isFinished) {
      continue;
    }
    
    const combined = content + punctuation;
    const stripped = stripMarkdown(combined).trim();
    if (stripped.length > 0) {
      textChunks.push(stripped);
    }
  }

  console.log('[TTS] appendText parsed chunks:', textChunks.length, 'existing chunks:', chunks.value.length);

  if (textChunks.length === 0) return;

  // 找出新增的 chunk
  const existingCount = chunks.value.length;
  
  if (textChunks.length > existingCount) {
    const newChunks = textChunks.slice(existingCount).map((t, i) => ({
      text: t,
      audio: null,
      status: "idle" as const,
      index: existingCount + i
    }));

    console.log('[TTS] Adding', newChunks.length, 'new chunks');
    chunks.value.push(...newChunks);
    fillLoadWindow();
  }
}

function toggleAutoTts() {
  autoTtsEnabled.value = !autoTtsEnabled.value;
  if (!autoTtsEnabled.value) {
    stop();
  }
}

export function useTts() {
  return {
    isPlaying: readonly(isPlaying),
    currentPlayingId: readonly(currentPlayingId),
    autoTtsEnabled: readonly(autoTtsEnabled),
    speak,
    appendText,
    stop,
    toggleAutoTts
  };
}
