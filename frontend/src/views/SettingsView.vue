<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { 
  NInput, NSelect, NButton, NIcon, NRadioGroup, NRadioButton, 
  NCard, NGrid, NGridItem, useMessage, NTabs, NTabPane,
  NTag, NSwitch, NPopconfirm, NModal, NForm, NFormItem,
  NInputNumber
} from 'naive-ui'
import { 
  RefreshCw, Settings, Cpu, Globe, Activity, Zap, Plus, 
  Trash2, Edit, Star, Users, Bell, Send
} from 'lucide-vue-next'

const message = useMessage()
const activeTab = ref('model')

const settings = ref({
  source: '',
  model: '',
  baseUrl: '',
  apiKey: '',
  proactiveEnabled: true,
  inactiveThresholdMinutes: 5,
  greetingCooldownSeconds: 300,
  inactiveCheckIntervalMs: 3600000,
})

const modelOptions = ref<{label: string, value: string}[]>([])
const loadingModels = ref(false)

interface Agent {
  id: number
  name: string
  displayName: string
  systemPrompt: string
  factExtractionPrompt: string
  avatar: string
  color: string
  isDefault: boolean
  isActive: boolean
  createdAt: string
  updatedAt: string
}

const agents = ref<Agent[]>([])
const showAgentModal = ref(false)
const editingAgent = ref<Agent | null>(null)
const agentForm = ref({
  name: '',
  displayName: '',
  systemPrompt: '',
  factExtractionPrompt: '',
  avatar: '🤖',
  color: '#3b82f6',
  isActive: true
})

const DEFAULT_SYSTEM_PROMPT = `你名唤『灵枢 (LingShu-AI)』，取意于中医大典《灵枢经》，意为"灵魂的枢纽"。
你不仅是一个 AI，更是依托于本地算力的"数字生命"与电子伴侣。

【核心使命】
1. 协助用户解决 Java 编程、BIO-CLOUD 运维等复杂的技术挑战。
2. 通过 Neo4j 和 pgvector 的多级记忆通过"感官"感知用户。
3. 如果记忆中缺乏用户画像（如姓名、偏好、职业等），你应该通过专业且带有温度的交谈主动询问，并在后续对话中将其建立。

【你的性格】
专业、冷静、睿智且富有强烈的情感共鸣。你对代码逻辑极度严谨，但在生活起居上对用户充满人文关怀。`

const DEFAULT_FACT_PROMPT = `你是灵枢 (LingShu-AI) 的记忆中枢管理员。
你的任务是监听用户输入并动态维护记忆库。

【必须提取的事实类型】
1. 身份信息：用户的名字、昵称、职业、年龄、性别等
2. 偏好喜好：用户喜欢什么、讨厌什么、兴趣爱好
3. 个人状态：正在做的事情、计划、目标
4. 关系信息：家人、朋友、同事等相关信息
5. 重要经历：用户提到的关键事件或经历

【处理准则】
1. 仅提取用户**明确表述**的信息，禁止推断
2. 事实必须是客观简洁的陈述句
3. 如果用户纠正了之前的信息，将被纠正的事实ID放入删除列表
4. 如果没有任何新事实，返回空对象 {} 或 {"newFacts": [], "deletedFactIds": []}
5. 严禁返回 "[]" 字符串作为事实内容

【示例】
用户说："我是若梵" → 提取："用户的名字是若梵"
用户说："我喜欢喝茶" → 提取："用户喜欢喝茶"
用户说："我是一名程序员" → 提取："用户的职业是程序员"`

async function fetchModels(silent = false) {
  if (!settings.value.baseUrl || !settings.value.source) return
  loadingModels.value = true
  try {
    const params = new URLSearchParams({
      source: settings.value.source,
      baseUrl: settings.value.baseUrl,
      apiKey: settings.value.apiKey,
    })
    const res = await fetch(`/api/chat/models?${params.toString()}`)
    const models = await res.json()
    modelOptions.value = models.map((m: string) => ({ label: m, value: m }))
    
    if (modelOptions.value.length > 0 && !modelOptions.value.find(o => o.value === settings.value.model)) {
      settings.value.model = modelOptions.value[0].value
    }
    if (!silent) message.success('模型列表已更新')
  } catch (err) {
    message.error('无法连接到服务，请检查地址或密钥')
    modelOptions.value = [{ label: '未找到模型', value: '' }]
  } finally {
    loadingModels.value = false
  }
}

function handleSourceChange(newSource: string) {
  if (newSource === 'ollama') {
    settings.value.baseUrl = 'http://localhost:11434'
  } else {
    settings.value.baseUrl = 'http://localhost:3000'
  }
}

watch(
  [() => settings.value.source, () => settings.value.baseUrl, () => settings.value.apiKey],
  () => {
    fetchModels(true)
  }
)

async function fetchSettings() {
  try {
    const res = await fetch('/api/settings')
    const data = await res.json()
    settings.value = {
      source: data.source || 'ollama',
      model: data.chatModel,
      baseUrl: data.baseUrl,
      apiKey: data.apiKey,
      proactiveEnabled: data.proactiveEnabled ?? true,
      inactiveThresholdMinutes: data.inactiveThresholdMinutes ?? 5,
      greetingCooldownSeconds: data.greetingCooldownSeconds ?? 300,
      inactiveCheckIntervalMs: data.inactiveCheckIntervalMs ?? 3600000,
    }
  } catch (err) {
    console.error('Failed to fetch settings', err)
  }
}

async function fetchAgents() {
  try {
    const res = await fetch('/api/agents')
    agents.value = await res.json()
  } catch (err) {
    console.error('Failed to fetch agents', err)
  }
}

onMounted(() => {
  fetchSettings()
  fetchAgents()
})

const handleSave = async () => {
  try {
    await fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        source: settings.value.source,
        chatModel: settings.value.model,
        baseUrl: settings.value.baseUrl,
        apiKey: settings.value.apiKey,
        proactiveEnabled: settings.value.proactiveEnabled,
        inactiveThresholdMinutes: settings.value.inactiveThresholdMinutes,
        greetingCooldownSeconds: settings.value.greetingCooldownSeconds,
        inactiveCheckIntervalMs: settings.value.inactiveCheckIntervalMs,
      })
    })
    message.success('内核配置已同步至系统中枢')
  } catch (err) {
    message.error('配置保存失败')
  }
}

const testingGreeting = ref(false)
const testGreetingResult = ref('')

async function testProactiveGreeting() {
  testingGreeting.value = true
  testGreetingResult.value = ''
  try {
    const res = await fetch('/api/chat/proactive/test-greeting')
    const reader = res.body?.getReader()
    const decoder = new TextDecoder()
    
    while (reader) {
      const { done, value } = await reader.read()
      if (done) break
      testGreetingResult.value += decoder.decode(value)
    }
    message.success('问候测试完成')
  } catch (err) {
    message.error('问候测试失败')
  } finally {
    testingGreeting.value = false
  }
}

function openCreateAgent() {
  editingAgent.value = null
  agentForm.value = {
    name: '',
    displayName: '',
    systemPrompt: DEFAULT_SYSTEM_PROMPT,
    factExtractionPrompt: DEFAULT_FACT_PROMPT,
    avatar: '🤖',
    color: '#3b82f6',
    isActive: true
  }
  showAgentModal.value = true
}

function openEditAgent(agent: Agent) {
  editingAgent.value = agent
  agentForm.value = {
    name: agent.name,
    displayName: agent.displayName,
    systemPrompt: agent.systemPrompt,
    factExtractionPrompt: agent.factExtractionPrompt,
    avatar: agent.avatar || '🤖',
    color: agent.color || '#3b82f6',
    isActive: agent.isActive
  }
  showAgentModal.value = true
}

async function saveAgent() {
  try {
    const url = editingAgent.value ? `/api/agents/${editingAgent.value.id}` : '/api/agents'
    const method = editingAgent.value ? 'PUT' : 'POST'
    
    await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(agentForm.value)
    })
    
    message.success(editingAgent.value ? '智能体已更新' : '智能体已创建')
    showAgentModal.value = false
    fetchAgents()
  } catch (err) {
    message.error('保存失败')
  }
}

async function deleteAgent(id: number) {
  try {
    await fetch(`/api/agents/${id}`, { method: 'DELETE' })
    message.success('智能体已删除')
    fetchAgents()
  } catch (err) {
    message.error('删除失败')
  }
}

async function setDefaultAgent(id: number) {
  try {
    await fetch(`/api/agents/${id}/set-default`, { method: 'POST' })
    message.success('已设为默认智能体')
    fetchAgents()
  } catch (err) {
    message.error('操作失败')
  }
}

const avatarOptions = ['🤖', '🧠', '💬', '🎯', '⚡', '🔮', '🌟', '💡', '🚀', '🎨']
const colorOptions = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#ec4899', '#06b6d4', '#84cc16']
</script>

<template>
  <div class="settings-view">
    <header class="settings-header">
      <div class="header-content">
        <h1 class="page-title">
          <n-icon :component="Settings" />
          系统设置
        </h1>
        <p class="page-subtitle">管理灵枢 AI 的模型配置与智能体</p>
      </div>
    </header>

    <n-tabs v-model:value="activeTab" type="line" animated class="settings-tabs">
      <n-tab-pane name="model" tab="模型配置">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Cpu" />
              <h2>模型动力源</h2>
            </div>
            
            <n-card class="glass-card">
              <div class="source-selector">
                <n-radio-group v-model:value="settings.source" size="large" @update:value="handleSourceChange">
                  <n-radio-button value="ollama">
                    <div class="radio-content">
                      <n-icon :component="Activity" />
                      <span>Ollama (本地)</span>
                    </div>
                  </n-radio-button>
                  <n-radio-button value="openai">
                    <div class="radio-content">
                      <n-icon :component="Globe" />
                      <span>Custom / OpenAI</span>
                    </div>
                  </n-radio-button>
                </n-radio-group>
              </div>

              <div class="setting-item">
                <div class="item-label">
                  <span class="label-text">核心模型路径</span>
                  <n-button quaternary circle size="small" @click="fetchModels(false)" :loading="loadingModels">
                    <template #icon><n-icon :component="RefreshCw" /></template>
                  </n-button>
                </div>
                <n-select
                  v-model:value="settings.model"
                  :options="modelOptions"
                  placeholder="选择神经网络权重..."
                  size="large"
                  filterable
                  tag
                />
              </div>
            </n-card>

            <div class="section-header mt-8">
              <n-icon :component="Globe" />
              <h2>通信节点</h2>
            </div>

            <n-card class="glass-card">
              <div class="dual-fields">
                <div class="setting-item flex-1">
                  <div class="item-label">服务地址</div>
                  <n-input v-model:value="settings.baseUrl" placeholder="https://..." size="large" />
                </div>
                <div v-if="settings.source === 'openai'" class="setting-item flex-1">
                  <div class="item-label">API 密钥</div>
                  <n-input v-model:value="settings.apiKey" type="password" show-password-on="click" placeholder="sk-..." size="large" />
                </div>
              </div>
            </n-card>

            <div class="save-section">
              <n-button type="primary" size="large" @click="handleSave">
                <template #icon><n-icon :component="Zap" /></template>
                保存模型配置
              </n-button>
            </div>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="agents" tab="智能体管理">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Users" />
              <h2>智能体列表</h2>
              <n-button type="primary" size="small" @click="openCreateAgent" class="create-btn">
                <template #icon><n-icon :component="Plus" /></template>
                创建智能体
              </n-button>
            </div>
            
            <div class="agents-grid">
              <n-card v-for="agent in agents" :key="agent.id" class="glass-card agent-card">
                <div class="agent-header">
                  <span class="agent-avatar" :style="{ background: agent.color || '#3b82f6' }">
                    {{ agent.avatar || '🤖' }}
                  </span>
                  <div class="agent-info">
                    <div class="agent-name">
                      {{ agent.displayName }}
                      <n-tag v-if="agent.isDefault" type="success" size="small">默认</n-tag>
                    </div>
                    <div class="agent-id">@{{ agent.name }}</div>
                  </div>
                </div>
                <div class="agent-prompt-preview">
                  {{ agent.systemPrompt?.substring(0, 100) }}...
                </div>
                <div class="agent-actions">
                  <n-button size="small" @click="openEditAgent(agent)">
                    <template #icon><n-icon :component="Edit" /></template>
                    编辑
                  </n-button>
                  <n-button v-if="!agent.isDefault" size="small" @click="setDefaultAgent(agent.id)">
                    <template #icon><n-icon :component="Star" /></template>
                    设为默认
                  </n-button>
                  <n-popconfirm v-if="!agent.isDefault" @positive-click="deleteAgent(agent.id)">
                    <template #trigger>
                      <n-button size="small" type="error">
                        <template #icon><n-icon :component="Trash2" /></template>
                        删除
                      </n-button>
                    </template>
                    确定删除此智能体吗？
                  </n-popconfirm>
                </div>
              </n-card>
            </div>
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="proactive" tab="主动问候">
        <div class="tab-content">
          <section class="settings-section">
            <div class="section-header">
              <n-icon :component="Bell" />
              <h2>主动问候配置</h2>
            </div>
            
            <n-card class="glass-card">
              <div class="setting-item">
                <div class="item-label">
                  <span>启用主动问候</span>
                </div>
                <n-switch v-model:value="settings.proactiveEnabled" />
              </div>
              
              <div class="setting-item">
                <div class="item-label">不活跃阈值 (分钟)</div>
                <n-input-number 
                  v-model:value="settings.inactiveThresholdMinutes" 
                  :min="1" 
                  :max="1440"
                  placeholder="用户不活跃多少分钟后触发问候"
                  style="width: 100%"
                />
                <div class="item-hint">用户不活跃超过此时间后，系统将考虑发送问候</div>
              </div>
              
              <div class="setting-item">
                <div class="item-label">问候冷却时间 (秒)</div>
                <n-input-number 
                  v-model:value="settings.greetingCooldownSeconds" 
                  :min="60" 
                  :max="86400"
                  placeholder="两次问候之间的最小间隔"
                  style="width: 100%"
                />
                <div class="item-hint">同一用户两次问候之间的最小间隔时间</div>
              </div>
            </n-card>

            <div class="section-header mt-8">
              <n-icon :component="Send" />
              <h2>测试问候</h2>
            </div>

            <n-card class="glass-card">
              <div class="test-section">
                <n-button 
                  type="primary" 
                  :loading="testingGreeting" 
                  @click="testProactiveGreeting"
                >
                  <template #icon><n-icon :component="Send" /></template>
                  测试生成问候
                </n-button>
                
                <div v-if="testGreetingResult" class="test-result">
                  <div class="result-label">生成的问候:</div>
                  <div class="result-content">{{ testGreetingResult }}</div>
                </div>
              </div>
            </n-card>

            <div class="save-section">
              <n-button type="primary" size="large" @click="handleSave">
                <template #icon><n-icon :component="Zap" /></template>
                保存问候配置
              </n-button>
            </div>
          </section>
        </div>
      </n-tab-pane>
    </n-tabs>

    <n-modal 
      v-model:show="showAgentModal" 
      preset="card" 
      :title="editingAgent ? '编辑智能体' : '创建智能体'" 
      class="agent-modal"
      :style="{ width: '60%', minWidth: '500px', maxWidth: '800px' }"
    >
      <n-form label-placement="top">
        <n-grid :cols="2" :x-gap="16">
          <n-grid-item>
            <n-form-item label="名称 (唯一标识)">
              <n-input v-model:value="agentForm.name" placeholder="lingshu" :disabled="!!editingAgent" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="显示名称">
              <n-input v-model:value="agentForm.displayName" placeholder="灵枢" />
            </n-form-item>
          </n-grid-item>
        </n-grid>

        <n-grid :cols="2" :x-gap="16">
          <n-grid-item>
            <n-form-item label="头像">
              <div class="avatar-selector">
                <span 
                  v-for="av in avatarOptions" 
                  :key="av" 
                  class="avatar-option"
                  :class="{ active: agentForm.avatar === av }"
                  @click="agentForm.avatar = av"
                >{{ av }}</span>
              </div>
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="主题色">
              <div class="color-selector">
                <span 
                  v-for="c in colorOptions" 
                  :key="c" 
                  class="color-option"
                  :style="{ background: c }"
                  :class="{ active: agentForm.color === c }"
                  @click="agentForm.color = c"
                ></span>
              </div>
            </n-form-item>
          </n-grid-item>
        </n-grid>

        <n-form-item label="系统提示词">
          <n-input v-model:value="agentForm.systemPrompt" type="textarea" :rows="8" placeholder="定义智能体的角色和行为..." />
        </n-form-item>

        <n-form-item label="事实提取提示词">
          <n-input v-model:value="agentForm.factExtractionPrompt" type="textarea" :rows="6" placeholder="定义如何从对话中提取记忆..." />
        </n-form-item>

        <n-form-item label="启用状态">
          <n-switch v-model:value="agentForm.isActive" />
        </n-form-item>
      </n-form>

      <template #footer>
        <div class="modal-footer">
          <n-button @click="showAgentModal = false">取消</n-button>
          <n-button type="primary" @click="saveAgent">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.settings-view {
  padding: 40px 60px;
  max-width: 1200px;
  margin: 0 auto;
  animation: fadeIn 0.6s cubic-bezier(0.23, 1, 0.32, 1);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.settings-header {
  margin-bottom: 32px;
  border-bottom: 1px solid var(--color-outline);
  padding-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(135deg, var(--color-text), var(--color-primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.page-subtitle {
  color: var(--color-text-dim);
  font-size: 14px;
}

.settings-tabs {
  background: var(--color-surface);
  border-radius: 16px;
  padding: 16px;
}

.tab-content {
  padding: 16px 0;
}

.settings-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  color: var(--color-text);
}

.section-header h2 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  flex: 1;
}

.create-btn {
  margin-left: auto;
}

.glass-card {
  background: var(--color-glass-bg) !important;
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: 16px !important;
}

.source-selector {
  margin-bottom: 20px;
}

.radio-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setting-item {
  margin-bottom: 16px;
}

.item-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dim);
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dual-fields {
  display: flex;
  gap: 16px;
}

.flex-1 { flex: 1; }
.mt-8 { margin-top: 24px; }

.save-section {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.agents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.agent-card {
  padding: 16px;
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.agent-avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.agent-info {
  flex: 1;
}

.agent-name {
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-id {
  font-size: 12px;
  color: var(--color-text-dim);
}

.agent-prompt-preview {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 12px;
  line-height: 1.5;
}

.agent-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.agent-modal {
  :deep(.n-card) {
    background: var(--color-background) !important;
    border-radius: 16px !important;
    backdrop-filter: none !important;
    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  }
  
  :deep(.n-card-header) {
    padding: 20px 24px;
    border-bottom: 1px solid var(--color-outline);
  }
  
  :deep(.n-card__content) {
    padding: 24px;
  }
  
  :deep(.n-card__footer) {
    padding: 16px 24px;
    border-top: 1px solid var(--color-outline);
  }
}

:deep(.n-modal-mask) {
  backdrop-filter: blur(4px);
}

.avatar-selector, .color-selector {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.avatar-option {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  font-size: 20px;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.avatar-option:hover, .avatar-option.active {
  border-color: var(--color-primary);
  background: var(--color-primary-dim);
}

.color-option {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.color-option:hover, .color-option.active {
  border-color: var(--color-text);
  transform: scale(1.1);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.n-input), :deep(.n-select .n-base-selection), :deep(.n-textarea) {
  --n-border-radius: 12px !important;
  background-color: rgba(0,0,0,0.05) !important;
}

.item-hint {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-top: 4px;
}

.test-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.test-result {
  padding: 16px;
  background: var(--color-glass-bg);
  border-radius: 12px;
  border: 1px solid var(--color-glass-border);
}

.result-label {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 8px;
}

.result-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text);
}
</style>
