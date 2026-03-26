<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { 
  NInput, NSelect, NButton, NIcon, NRadioGroup, NRadioButton, 
  NCard, NGrid, NGridItem, useMessage, NTabs, NTabPane,
  NTag, NSwitch, NPopconfirm, NModal, NForm, NFormItem,
  NInputNumber, NDivider
} from 'naive-ui'
import { 
  RefreshCw, Settings, Cpu, Globe, Activity, Zap, Plus, 
  Trash2, Edit, Star, Users, Bell, Send, Brain
} from 'lucide-vue-next'
import {
  RobotOutlined,
  BulbOutlined,
  RocketOutlined,
  StarOutlined,
  HeartOutlined,
  SmileOutlined,
  FireOutlined,
  ThunderboltOutlined,
  CrownOutlined,
  TrophyOutlined,
  GiftOutlined,
  LikeOutlined
} from '@vicons/antd'
import McpSettings from '@/components/McpSettings.vue'
import { useSettings } from '@/stores/settingsStore'
import { useAgents, type Agent } from '@/stores/agentsStore'

const props = defineProps<{
  activeMenu?: string
}>()

const emit = defineEmits<{
  (e: 'update:activeMenu', key: string): void
}>()

const message = useMessage()

const {
  settings,
  chatModelOptions,
  embedModelOptions,
  loadingChatModels,
  loadingEmbedModels,
  fetchSettings,
  saveSettings,
  fetchChatModels,
  fetchEmbedModels,
  debouncedFetchChatModels,
  debouncedFetchEmbedModels,
  handleSourceChange,
  handleEmbedSourceChange
} = useSettings()

const {
  agents,
  fetchAgents,
  createAgent,
  updateAgent,
  deleteAgent,
  setDefaultAgent,
  getAgentDefaults
} = useAgents()

const tabMapping: Record<string, string> = {
  'settings-model': 'model',
  'settings-agents': 'agents',
  'settings-proactive': 'proactive',
  'settings-mcp': 'mcp',
  'settings': 'model'
}

const reverseTabMapping: Record<string, string> = {
  'model': 'settings-model',
  'agents': 'settings-agents',
  'proactive': 'settings-proactive',
  'mcp': 'settings-mcp'
}

const activeTab = computed({
  get: () => {
    const menuKey = props.activeMenu || 'settings-model'
    return tabMapping[menuKey] || 'model'
  },
  set: (tabKey: string) => {
    const menuKey = reverseTabMapping[tabKey] || 'settings-model'
    emit('update:activeMenu', menuKey)
  }
})

const modelSubTab = ref('llm')

watch(modelSubTab, (newTab) => {
  if (newTab === 'llm') {
    fetchChatModels(true)
  } else if (newTab === 'embedding') {
    fetchEmbedModels(true)
  }
})

const showAgentModal = ref(false)
const editingAgent = ref<Agent | null>(null)
const agentForm = ref({
  name: '',
  displayName: '',
  systemPrompt: '',
  factExtractionPrompt: '',
  behaviorPrinciples: '',
  decisionMechanism: '',
  toolCallRules: '',
  emotionalStrategy: '',
  greetingTriggers: '',
  hiddenRules: '',
  avatar: '机器人',
  color: '#3b82f6',
  isActive: true
})

onMounted(() => {
  fetchSettings()
  fetchAgents()
  fetchChatModels(true)
})

const handleSave = async () => {
  try {
    await saveSettings()
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
    let buffer = ''
    
    while (reader) {
      const { done, value } = await reader.read()
      if (done) break
      
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const content = trimmed.replace(/^data:\s?/, '')
          testGreetingResult.value += content
        }
      }
    }
    
    if (buffer.trim().startsWith('data:')) {
      testGreetingResult.value += buffer.trim().replace(/^data:\s?/, '')
    }
    message.success('问候测试完成')
  } catch (err) {
    message.error('问候测试失败')
  } finally {
    testingGreeting.value = false
  }
}

async function openCreateAgent() {
  editingAgent.value = null
  try {
    const res = await getAgentDefaults()
    const defaults = await res.json()
    agentForm.value = {
      name: '',
      displayName: '',
      systemPrompt: defaults.systemPrompt,
      factExtractionPrompt: defaults.factExtractionPrompt,
      behaviorPrinciples: defaults.behaviorPrinciples,
      decisionMechanism: defaults.decisionMechanism,
      toolCallRules: defaults.toolCallRules,
      emotionalStrategy: defaults.emotionalStrategy,
      greetingTriggers: defaults.greetingTriggers,
      hiddenRules: defaults.hiddenRules,
      avatar: defaults.avatar || '机器人',
      color: defaults.color || '#3b82f6',
      isActive: true
    }
    showAgentModal.value = true
  } catch (err) {
    message.error('无法获取默认提示词配置')
  }
}

function openEditAgent(agent: Agent) {
  editingAgent.value = agent
  agentForm.value = {
    name: agent.name,
    displayName: agent.displayName,
    systemPrompt: agent.systemPrompt,
    factExtractionPrompt: agent.factExtractionPrompt,
    behaviorPrinciples: agent.behaviorPrinciples || '',
    decisionMechanism: agent.decisionMechanism || '',
    toolCallRules: agent.toolCallRules || '',
    emotionalStrategy: agent.emotionalStrategy || '',
    greetingTriggers: agent.greetingTriggers || '',
    hiddenRules: agent.hiddenRules || '',
    avatar: agent.avatar || '机器人',
    color: agent.color || '#3b82f6',
    isActive: agent.isActive
  }
  showAgentModal.value = true
}

async function saveAgent() {
  try {
    let res
    if (editingAgent.value) {
      res = await updateAgent(editingAgent.value.id, agentForm.value)
    } else {
      res = await createAgent(agentForm.value)
    }
    
    if (res.ok) {
      message.success(editingAgent.value ? '智能体已更新' : '智能体已创建')
      showAgentModal.value = false
    } else {
      message.error('保存失败')
    }
  } catch (err) {
    message.error('保存失败')
  }
}

async function handleDeleteAgent(id: number) {
  try {
    const res = await deleteAgent(id)
    if (res.ok) {
      message.success('智能体已删除')
    } else {
      message.error('删除失败')
    }
  } catch (err) {
    message.error('删除失败')
  }
}

async function handleSetDefaultAgent(id: number) {
  try {
    const res = await setDefaultAgent(id)
    if (res.ok) {
      message.success('已设为默认智能体')
    } else {
      message.error('操作失败')
    }
  } catch (err) {
    message.error('操作失败')
  }
}

const avatarOptions = [
  { icon: RobotOutlined, label: '机器人' },
  { icon: BulbOutlined, label: '智慧' },
  { icon: RocketOutlined, label: '效率' },
  { icon: StarOutlined, label: '明星' },
  { icon: HeartOutlined, label: '温暖' },
  { icon: SmileOutlined, label: '友好' },
  { icon: FireOutlined, label: '热情' },
  { icon: ThunderboltOutlined, label: '速度' },
  { icon: CrownOutlined, label: '尊贵' },
  { icon: TrophyOutlined, label: '成就' },
  { icon: LikeOutlined, label: '点赞' },
  { icon: GiftOutlined, label: '惊喜' }
]
const colorOptions = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#ec4899', '#06b6d4', '#84cc16']
</script>

<template>
  <div class="settings-view">
    <div class="settings-header">
      <div class="header-content">
        <h1 class="page-title">
          <n-icon :component="Settings" />
          系统设置
        </h1>
        <p class="page-subtitle">管理灵枢 AI 的模型配置与智能体</p>
      </div>
    </div>

    <div class="settings-content-wrapper">
      <n-tabs v-model:value="activeTab" type="line" class="settings-tabs">
        <n-tab-pane name="model" tab="模型配置">
          <div class="tab-content">
            <n-tabs type="segment">
              <n-tab-pane name="llm" tab="对话模型 (LLM)">
                <section class="settings-section pt-4">
                  <div class="section-header">
                    <n-icon :component="Cpu" />
                    <h2>神经网络源</h2>
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
                        <n-button quaternary circle size="small" @click="fetchChatModels(false)" :loading="loadingChatModels">
                          <template #icon><n-icon :component="RefreshCw" /></template>
                        </n-button>
                      </div>
                      <n-select
                        v-model:value="settings.model"
                        :options="chatModelOptions"
                        placeholder="选择神经网络权重..."
                        size="large"
                        filterable
                        tag
                      />
                    </div>
                    
                    <div class="dual-fields mt-4">
                      <div class="setting-item flex-1">
                        <div class="item-label">服务地址</div>
                        <n-input v-model:value="settings.baseUrl" placeholder="https://..." size="large" @update:value="debouncedFetchChatModels" />
                      </div>
                      <div v-if="settings.source === 'openai'" class="setting-item flex-1">
                        <div class="item-label">API 密钥</div>
                        <n-input v-model:value="settings.apiKey" type="password" show-password-on="click" placeholder="sk-..." size="large" autocomplete="off" @update:value="debouncedFetchChatModels" />
                      </div>
                    </div>
                  </n-card>
                </section>
              </n-tab-pane>

              <n-tab-pane name="embedding" tab="向量模型 (Embedding)">
                <section class="settings-section pt-4">
                  <div class="section-header">
                    <n-icon :component="Brain" />
                    <h2>语义编码源</h2>
                  </div>
                  
                  <n-card class="glass-card">
                    <div class="source-selector">
                      <n-radio-group v-model:value="settings.embedSource" size="large" @update:value="handleEmbedSourceChange">
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
                        <span class="label-text">向量化模型</span>
                        <n-button quaternary circle size="small" @click="fetchEmbedModels(false)" :loading="loadingEmbedModels">
                          <template #icon><n-icon :component="RefreshCw" /></template>
                        </n-button>
                      </div>
                      <n-select
                        v-model:value="settings.embedModel"
                        :options="embedModelOptions"
                        placeholder="选择向量化模型..."
                        size="large"
                        filterable
                        tag
                      />
                    </div>

                    <div class="dual-fields mt-4">
                      <div class="setting-item flex-1">
                        <div class="item-label">服务地址</div>
                        <n-input v-model:value="settings.embedBaseUrl" placeholder="https://..." size="large" @update:value="debouncedFetchEmbedModels" />
                      </div>
                      <div v-if="settings.embedSource === 'openai'" class="setting-item flex-1">
                        <div class="item-label">API 密钥</div>
                        <n-input v-model:value="settings.embedApiKey" type="password" show-password-on="click" placeholder="sk-..." size="large" autocomplete="off" @update:value="debouncedFetchEmbedModels" />
                      </div>
                    </div>
                  </n-card>
                </section>
              </n-tab-pane>
            </n-tabs>

            <div class="save-section">
              <n-button type="primary" size="large" @click="handleSave">
                <template #icon><n-icon :component="Zap" /></template>
                保存所有模型配置
              </n-button>
            </div>
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
                      <template v-if="agent.avatar === '机器人'"><n-icon :component="RobotOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '智慧'"><n-icon :component="BulbOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '效率'"><n-icon :component="RocketOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '明星'"><n-icon :component="StarOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '温暖'"><n-icon :component="HeartOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '友好'"><n-icon :component="SmileOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '热情'"><n-icon :component="FireOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '速度'"><n-icon :component="ThunderboltOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '尊贵'"><n-icon :component="CrownOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '成就'"><n-icon :component="TrophyOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '点赞'"><n-icon :component="LikeOutlined" size="24" /></template>
                      <template v-else-if="agent.avatar === '惊喜'"><n-icon :component="GiftOutlined" size="24" /></template>
                      <template v-else><n-icon :component="RobotOutlined" size="24" /></template>
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
                    <n-button v-if="!agent.isDefault" size="small" @click="handleSetDefaultAgent(agent.id)">
                      <template #icon><n-icon :component="Star" /></template>
                      设为默认
                    </n-button>
                    <n-popconfirm v-if="!agent.isDefault" @positive-click="handleDeleteAgent(agent.id)">
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

        <n-tab-pane name="mcp" tab="MCP 插件">
          <div class="tab-content">
            <McpSettings />
          </div>
        </n-tab-pane>
      </n-tabs>
    </div>

    <n-modal 
      v-model:show="showAgentModal" 
      preset="card" 
      :title="editingAgent ? '编辑智能体' : '创建智能体'" 
      class="agent-modal"
      :style="{ width: '70%', minWidth: '600px', maxWidth: '900px' }"
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
                  :key="av.label" 
                  class="avatar-option"
                  :class="{ active: agentForm.avatar === av.label }"
                  @click="agentForm.avatar = av.label"
                >
                  <n-icon :component="av.icon" size="24" />
                </span>
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

        <n-divider style="margin: 8px 0 16px;">提示词配置</n-divider>

        <n-tabs type="line" class="prompt-tabs">
          <n-tab-pane name="system" tab="系统提示词">
            <div class="prompt-hint">定义智能体的核心身份、使命和性格特征</div>
            <n-input v-model:value="agentForm.systemPrompt" type="textarea" :rows="8" placeholder="定义智能体的角色和行为..." />
          </n-tab-pane>
          
          <n-tab-pane name="fact" tab="事实提取">
            <div class="prompt-hint">定义如何从对话中提取和更新用户记忆</div>
            <n-input v-model:value="agentForm.factExtractionPrompt" type="textarea" :rows="8" placeholder="定义如何从对话中提取记忆..." />
          </n-tab-pane>
          
          <n-tab-pane name="behavior" tab="行为原则">
            <div class="prompt-hint">定义智能体的行为规范和交互风格</div>
            <n-input v-model:value="agentForm.behaviorPrinciples" type="textarea" :rows="8" placeholder="定义行为原则..." />
          </n-tab-pane>
          
          <n-tab-pane name="decision" tab="决策机制">
            <div class="prompt-hint">定义智能体的自主决策逻辑和意识循环</div>
            <n-input v-model:value="agentForm.decisionMechanism" type="textarea" :rows="8" placeholder="定义决策机制..." />
          </n-tab-pane>
          
          <n-tab-pane name="tool" tab="工具调用">
            <div class="prompt-hint">定义智能体如何感知和操作外部工具</div>
            <n-input v-model:value="agentForm.toolCallRules" type="textarea" :rows="8" placeholder="定义工具调用规则..." />
          </n-tab-pane>
          
          <n-tab-pane name="emotional" tab="情感策略">
            <div class="prompt-hint">定义智能体的情感陪伴和共情逻辑</div>
            <n-input v-model:value="agentForm.emotionalStrategy" type="textarea" :rows="8" placeholder="定义情感陪伴策略..." />
          </n-tab-pane>
          
          <n-tab-pane name="greeting" tab="问候触发">
            <div class="prompt-hint">定义主动问候的触发条件和时机</div>
            <n-input v-model:value="agentForm.greetingTriggers" type="textarea" :rows="8" placeholder="定义问候触发条件..." />
          </n-tab-pane>
          
          <n-tab-pane name="hidden" tab="隐性规则">
            <div class="prompt-hint">定义智能体必须遵守的隐性边界规则</div>
            <n-input v-model:value="agentForm.hiddenRules" type="textarea" :rows="8" placeholder="定义隐性规则..." />
          </n-tab-pane>
        </n-tabs>

        <n-form-item label="启用状态" style="margin-top: 16px;">
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
  padding: 24px 32px;
  height: 100%;
  overflow: hidden;
  animation: fadeIn 0.5s cubic-bezier(0.23, 1, 0.32, 1);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.settings-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline);
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  background: linear-gradient(135deg, var(--color-text), var(--color-primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.page-subtitle {
  color: var(--color-text-dim);
  font-size: 13px;
  margin: 0;
  line-height: 1.4;
}

.settings-content-wrapper {
  background: var(--color-surface);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid var(--color-outline);
  height: calc(100vh - 140px);
  overflow: hidden;
}

.settings-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.n-tabs-tab-wrapper) {
  flex-shrink: 0;
}

:deep(.n-tabs-pane-wrapper) {
  flex: 1;
  overflow-y: auto;
  padding: 16px 4px;
}

.tab-content {
  height: 100%;
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
  padding: 16px 0;
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
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  cursor: pointer;
  background: rgba(0, 0, 0, 0.03);
  border: 2px solid transparent;
  transition: all 0.2s;
  color: var(--color-text);
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

.prompt-tabs {
  background: var(--color-glass-bg);
  border-radius: 12px;
  padding: 12px;
  border: 1px solid var(--color-glass-border);
}

.prompt-tabs :deep(.n-tabs-nav) {
  padding: 0 8px;
}

.prompt-tabs :deep(.n-tab-pane) {
  padding: 12px 0 0;
}

.prompt-hint {
  font-size: 12px;
  color: var(--color-text-dim);
  margin-bottom: 8px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 8px;
  border-left: 3px solid var(--color-primary);
}

@media (max-width: 1024px) {
  .settings-layout {
    flex-direction: column;
  }
  
  .settings-sidebar {
    width: 100%;
  }
  
  .sidebar-nav {
    flex-direction: row;
    overflow-x: auto;
  }
  
  .nav-item {
    flex-shrink: 0;
  }
}
</style>
