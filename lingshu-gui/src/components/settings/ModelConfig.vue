<script setup lang="ts">
import { onMounted } from 'vue';
import { useSettingsStore } from '../../stores/settings';

const store = useSettingsStore();

const sourceOptions = [
  { value: 'ollama', label: 'Ollama (本地)' },
  { value: 'lmstudio', label: 'LM Studio' },
  { value: 'openai', label: 'OpenAI 兼容' }
];

const handleSourceChange = (val: string, type: 'llm' | 'embedding' | 'memoryModel') => {
  const defaults: Record<string, string> = {
    ollama: 'http://localhost:11434',
    lmstudio: 'http://localhost:1234/v1'
  };

  if (defaults[val]) {
    if (type === 'llm') {
      store.llm.baseUrl = defaults[val];
    } else if (type === 'embedding') {
      store.embedding.baseUrl = defaults[val];
    } else if (type === 'memoryModel') {
      store.memoryModel.baseUrl = defaults[val];
    }
  }

  // 自动刷新对应类型的模型列表
  if (type === 'llm') {
    store.fetchChatModels(true);
  } else if (type === 'embedding') {
    store.fetchEmbedModels(true);
  } else if (type === 'memoryModel') {
    store.fetchMemoryModels(true);
  }
};

const ttsFormatOptions = [
  { value: 'mp3', label: 'MP3' },
  { value: 'opus', label: 'Opus' },
  { value: 'aac', label: 'AAC' },
  { value: 'flac', label: 'FLAC' },
  { value: 'wav', label: 'WAV' },
  { value: 'pcm', label: 'PCM' }
];

onMounted(() => {
  store.fetchSettings();
});
</script>

<template>
  <div class="model-config">
    <a-spin :loading="!store.isLoaded" tip="正在加载模型配置...">
      <a-tabs default-active-key="llm" type="line" class="model-tabs">
        <!-- 对话模型 (LLM) -->
        <a-tab-pane key="llm" title="对话模型">
          <div class="settings-group">
            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">模型来源</span>
                <span class="sub-text">选择模型提供来源，Ollama/LM Studio 为本地部署，OpenAI 兼容支持各类云端服务</span>
              </div>
              <div class="row-action">
                <a-select v-model="store.llm.source" :options="sourceOptions" :style="{ width: '160px' }" size="small" @change="(val) => handleSourceChange(val as string, 'llm')" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">模型</span>
                <span class="sub-text">选择对话使用的语言模型</span>
              </div>
              <div class="row-action model-select-action">
                <a-select
                  v-model="store.llm.model"
                  :options="store.chatModelOptions"
                  :loading="store.loadingChatModels"
                  placeholder="选择或输入模型名称..."
                  :style="{ width: '320px' }"
                  size="small"
                  allow-search
                  allow-create
                  @dropdown-show="store.fetchChatModels(true)"
                />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">服务地址</span>
                <span class="sub-text">模型 API 的服务端点地址</span>
              </div>
              <div class="row-action">
                <a-input v-model="store.llm.baseUrl" placeholder="http://localhost:11434" :style="{ width: '320px' }" size="small" @blur="store.fetchChatModels(true)" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">API 密钥</span>
                <span class="sub-text">OpenAI 兼容 API 需要提供密钥，Ollama 本地服务可留空</span>
              </div>
              <div class="row-action">
                <a-input-password v-model="store.llm.apiKey" placeholder="sk-..." :style="{ width: '320px' }" size="small" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">启用思考模式</span>
                <span class="sub-text">开启后模型在回复前会进行深度推理（适用于 qwen3 等支持思考的模型）</span>
              </div>
              <div class="row-action">
                <a-switch v-model="store.llm.enableThinking" size="small" />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <!-- 向量模型 (Embedding) -->
        <a-tab-pane key="embedding" title="向量模型">
          <div class="settings-group">
            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">模型来源</span>
                <span class="sub-text">选择向量化模型的提供来源</span>
              </div>
              <div class="row-action">
                <a-select v-model="store.embedding.source" :options="sourceOptions" :style="{ width: '160px' }" size="small" @change="(val) => handleSourceChange(val as string, 'embedding')" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">模型</span>
                <span class="sub-text">用于文本向量化的模型，影响记忆检索质量</span>
              </div>
              <div class="row-action model-select-action">
                <a-select
                  v-model="store.embedding.model"
                  :options="store.embedModelOptions"
                  :loading="store.loadingEmbedModels"
                  placeholder="选择或输入模型名称..."
                  :style="{ width: '320px' }"
                  size="small"
                  allow-search
                  allow-create
                  @dropdown-show="store.fetchEmbedModels(true)"
                />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">服务地址</span>
                <span class="sub-text">向量模型服务端点，默认与对话模型共享地址</span>
              </div>
              <div class="row-action">
                <a-input v-model="store.embedding.baseUrl" placeholder="http://localhost:11434" :style="{ width: '320px' }" size="small" @blur="store.fetchEmbedModels(true)" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">API 密钥</span>
                <span class="sub-text">OpenAI 兼容 API 需要提供密钥，Ollama 本地服务可留空</span>
              </div>
              <div class="row-action">
                <a-input-password v-model="store.embedding.apiKey" placeholder="sk-..." :style="{ width: '320px' }" size="small" />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <!-- 记忆模型 (Memory Model) -->
        <a-tab-pane key="memory" title="记忆模型">
          <div class="settings-group">
            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">模型来源</span>
                <span class="sub-text">用于长期记忆处理的模型来源，留空则默认使用对话模型配置</span>
              </div>
              <div class="row-action">
                <a-select v-model="store.memoryModel.source" :options="sourceOptions" :style="{ width: '160px' }" size="small" allow-clear placeholder="默认使用对话模型" @change="(val) => handleSourceChange(val as string, 'memoryModel')" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">模型</span>
                <span class="sub-text">用于记忆提取和关联分析的模型</span>
              </div>
              <div class="row-action model-select-action">
                <a-select
                  v-model="store.memoryModel.model"
                  :options="store.memoryModelOptions"
                  :loading="store.loadingMemoryModels"
                  placeholder="选择或输入模型名称..."
                  :style="{ width: '320px' }"
                  size="small"
                  allow-search
                  allow-create
                  @dropdown-show="store.fetchMemoryModels(true)"
                />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">服务地址</span>
                <span class="sub-text">记忆模型服务端点，留空则默认使用对话模型地址</span>
              </div>
              <div class="row-action">
                <a-input v-model="store.memoryModel.baseUrl" placeholder="默认使用对话模型地址" :style="{ width: '320px' }" size="small" @blur="store.fetchMemoryModels(true)" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">API 密钥</span>
                <span class="sub-text">记忆模型密钥，留空则默认使用对话模型密钥</span>
              </div>
              <div class="row-action">
                <a-input-password v-model="store.memoryModel.apiKey" placeholder="默认使用对话模型密钥" :style="{ width: '320px' }" size="small" />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <!-- 语音合成 (TTS) -->
        <a-tab-pane key="tts" title="语音合成">
          <div class="settings-group">
            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">启用 TTS</span>
                <span class="sub-text">开启后将 AI 回复文本转换为语音输出</span>
              </div>
              <div class="row-action">
                <a-switch v-model="store.tts.enabled" size="small" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">TTS 服务地址</span>
                <span class="sub-text">OpenAI 兼容的 TTS 服务地址，如 openai-edge-tts</span>
              </div>
              <div class="row-action">
                <a-input v-model="store.tts.baseUrl" placeholder="http://localhost:5050" :style="{ width: '280px' }" size="small" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">API 密钥</span>
                <span class="sub-text">如果 TTS 服务需要认证，请填入密钥</span>
              </div>
              <div class="row-action">
                <a-input-password v-model="store.tts.apiKey" placeholder="sk-..." :style="{ width: '280px' }" size="small" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">默认语音</span>
                <span class="sub-text">TTS 使用的默认音色名称</span>
              </div>
              <div class="row-action">
                <a-input v-model="store.tts.defaultVoice" placeholder="alloy" :style="{ width: '160px' }" size="small" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">默认语速</span>
                <span class="sub-text">取值范围 0.25 ~ 4.0，1.0 为正常语速</span>
              </div>
              <div class="row-action">
                <a-input-number
                  v-model="store.tts.defaultSpeed"
                  :min="0.25"
                  :max="4.0"
                  :step="0.25"
                  :precision="2"
                  :style="{ width: '120px' }"
                  size="small"
                />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">默认格式</span>
                <span class="sub-text">音频输出格式，MP3 兼容性最好</span>
              </div>
              <div class="row-action">
                <a-select v-model="store.tts.defaultFormat" :options="ttsFormatOptions" :style="{ width: '120px' }" size="small" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">随机种子</span>
                <span class="sub-text">音频生成的随机种子，-1 表示随机</span>
              </div>
              <div class="row-action">
                <a-input-number v-model="store.tts.defaultSeed" :min="-1" :step="1" :style="{ width: '120px' }" size="small" />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <!-- 语音识别 (ASR) -->
        <a-tab-pane key="asr" title="语音识别">
          <div class="settings-group">
            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">启用 ASR</span>
                <span class="sub-text">开启后支持语音输入转文字</span>
              </div>
              <div class="row-action">
                <a-switch v-model="store.asr.enabled" size="small" />
              </div>
            </div>

            <div class="setting-row">
              <div class="row-label">
                <span class="main-text">ASR 服务地址</span>
                <span class="sub-text">语音识别服务端点地址，如 SenseVoice 服务</span>
              </div>
              <div class="row-action">
                <a-input v-model="store.asr.url" placeholder="http://localhost:50001" :style="{ width: '280px' }" size="small" />
              </div>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>

      <!-- 保存按钮 -->
      <div class="save-area">
        <a-button type="primary" :loading="store.saving" @click="store.saveSettings">保存配置</a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.model-config {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.model-tabs {
  flex: 1;
}

:deep(.arco-tabs-content) {
  padding-top: 16px;
}

.settings-group {
  background-color: var(--bg-input);
  border-radius: 12px;
  padding: 4px 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-color);
}

.setting-row:last-child {
  border-bottom: none;
}

.row-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 55%;
}

.main-text {
  font-size: 14px;
  color: var(--text-primary);
}

.sub-text {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.4;
}

.row-action {
  display: flex;
  align-items: center;
}

.model-select-action {
  display: flex;
  align-items: center;
  gap: 4px;
}

.save-area {
  display: flex;
  justify-content: flex-end;
  padding: 20px 0;
  margin-top: auto;
}
</style>
