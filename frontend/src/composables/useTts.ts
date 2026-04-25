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

    // 使用 fetch 替代直接 new Audio，以便捕获 400 等错误状态码
    const response = await fetch(url);
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      const errorDetail = errorData.detail || errorData.message || `HTTP ${response.status}`;
      console.error("TTS request failed:", errorDetail);
      // 可以在此处通过 message 组件提示用户，或者抛出错误
      throw new Error(errorDetail);
    }

    const audioBlob = await response.blob();
    const audioUrl = URL.createObjectURL(audioBlob);
    
    currentAudio = new Audio(audioUrl);

    currentAudio.onended = () => {
      isPlaying.value = false;
      currentPlayingId.value = null;
      URL.revokeObjectURL(audioUrl); // 播放结束释放资源
    };

    currentAudio.onerror = (e) => {
      console.error("TTS audio playback error:", e);
      isPlaying.value = false;
      currentPlayingId.value = null;
      URL.revokeObjectURL(audioUrl);
    };

    await currentAudio.play();
  } catch (error: any) {
    const errorDetail = error.message || error;
    console.error("TTS error:", errorDetail);
    
    // 弹窗提示用户
    if ((window as any).$message) {
      (window as any).$message.error(`语音合成失败: ${errorDetail}`);
    }
    
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
