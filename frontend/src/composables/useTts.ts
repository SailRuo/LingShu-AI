import { ref, readonly, watch } from "vue";
import { useSettings } from "@/stores/settingsStore";
import { getFullUrl } from "@/utils/request";

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

interface TtsChunk {
  text: string;
  audio: HTMLAudioElement | null;
  status: "idle" | "loading" | "ready" | "error";
  index: number;
}

const chunks = ref<TtsChunk[]>([]);
const currentIndex = ref(0);
const CONCURRENCY_LIMIT = 2; 

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
  const { settings } = useSettings();
  const audioUrl = getFullUrl(
    `/api/tts/speak?text=${encodeURIComponent(chunk.text)}&seed=${settings.value.ttsDefaultSeed}`,
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
  const { settings } = useSettings();
  if (!settings.value.ttsEnabled || !text) return;

  if (isPlaying.value && currentPlayingId.value === messageId) {
    stop();
    return;
  }

  stop();
  isPlaying.value = true;
  currentPlayingId.value = messageId || null;

  const textChunks = text
    .split(/\n+/)
    .map(t => stripMarkdown(t))
    .filter(t => t.trim().length > 0);

  if (textChunks.length === 0) {
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
  fillLoadWindow();
}

export function useTts() {
  return {
    isPlaying: readonly(isPlaying),
    currentPlayingId: readonly(currentPlayingId),
    speak,
    stop,
  };
}
