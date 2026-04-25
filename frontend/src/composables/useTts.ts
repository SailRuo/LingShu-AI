import { ref, readonly } from "vue";
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

  result = result.replace(/\n{3,}/g, "\n\n");
  result = result.trim();

  return result;
}

const isPlaying = ref(false);
const currentPlayingId = ref<string | null>(null);
let currentAudio: HTMLAudioElement | null = null;

function stop() {
  if (currentAudio) {
    try {
      currentAudio.pause();
      currentAudio.currentTime = 0;
    } catch (e) {
      // ignore
    }
    currentAudio = null;
  }
  isPlaying.value = false;
  currentPlayingId.value = null;
}

async function speak(text: string, messageId?: string): Promise<void> {
  const { settings } = useSettings();

  if (!settings.value.ttsEnabled || !text) return;

  const cleanText = stripMarkdown(text);
  if (!cleanText) return;

  if (isPlaying.value && currentPlayingId.value === messageId) {
    stop();
    return;
  }

  stop();
  isPlaying.value = true;
  currentPlayingId.value = messageId || null;

  try {
    const url = getFullUrl(
      `/api/tts/speak?text=${encodeURIComponent(cleanText)}&seed=${settings.value.ttsDefaultSeed}`,
    );
    currentAudio = new Audio(url);

    currentAudio.onended = () => {
      isPlaying.value = false;
      currentPlayingId.value = null;
    };

    currentAudio.onerror = (e) => {
      console.error("TTS audio playback error:", e);
      isPlaying.value = false;
      currentPlayingId.value = null;
    };

    await currentAudio.play();
  } catch (error) {
    console.error("TTS error:", error);
    isPlaying.value = false;
    currentPlayingId.value = null;
  }
}

export function useTts() {
  return {
    isPlaying: readonly(isPlaying),
    currentPlayingId: readonly(currentPlayingId),
    speak,
    stop,
  };
}
