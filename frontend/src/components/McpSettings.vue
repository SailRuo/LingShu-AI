<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NButton, NIcon, NCard, NTag, NSwitch, NPopconfirm, NModal,
  NForm, NFormItem, NInput, NSelect, useMessage, NSpace, NEmpty,
  NSpin, NDynamicInput
} from 'naive-ui'
import { Plus, Trash2, Edit, Plug, Activity, Server, FileJson } from 'lucide-vue-next'

const message = useMessage()
const loading = ref(false)
const servers = ref<any[]>([])
const showModal = ref(false)
const showImportModal = ref(false)
const importJsonText = ref('')
const editingServer = ref<any>(null)
const testingPing = ref<Record<number, boolean>>({})
const showToolsModal = ref(false)
const currentTools = ref<any[]>([])
const currentServerName = ref('')

const formModel = ref({
  name: '',
  transportType: 'STDIO',
  command: '',
  args: [] as string[],
  envVars: [] as { key: string, value: string }[],
  url: '',
  isActive: true
})

const transportOptions = [
  { label: 'STDIO (本地子进程)', value: 'STDIO' },
  { label: 'SSE (HTTP 长链接)', value: 'SSE' }
]

async function fetchServers() {
  loading.value = true
  try {
    const res = await fetch('/api/mcp')
    if (res.ok) {
      servers.value = await res.json()
    } else {
      message.error('获取 MCP 列表失败')
    }
  } catch (err) {
    message.error('网络请求失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchServers()
})

function openCreate() {
  editingServer.value = null
  formModel.value = {
    name: '',
    transportType: 'STDIO',
    command: '',
    args: [],
    envVars: [],
    url: '',
    isActive: true
  }
  showModal.value = true
}

function openEdit(server: any) {
  editingServer.value = server
  let envs: {key: string, value: string}[] = []
  if (server.env) {
    try {
      const parsed = JSON.parse(server.env)
      envs = Object.keys(parsed).map(k => ({ key: k, value: parsed[k] }))
    } catch(e) {}
  }
  let args: string[] = []
  if (server.args) {
    try { args = JSON.parse(server.args) } catch(e) {}
  }
  
  formModel.value = {
    name: server.name || '',
    transportType: server.transportType || 'STDIO',
    command: server.command || '',
    args: args,
    envVars: envs,
    url: server.url || '',
    isActive: server.isActive
  }
  showModal.value = true
}

async function saveServer() {
  if (!formModel.value.name) {
    message.warning('名称是必填项')
    return
  }
  
  const envObj: Record<string, string> = {}
  formModel.value.envVars.forEach(item => {
    if (item.key && item.value) {
      envObj[item.key] = item.value
    }
  })
  
  const payload = {
    name: formModel.value.name,
    transportType: formModel.value.transportType,
    command: formModel.value.transportType?.toUpperCase() === 'STDIO' ? formModel.value.command : null,
    args: formModel.value.transportType?.toUpperCase() === 'STDIO' ? JSON.stringify(formModel.value.args) : null,
    env: formModel.value.transportType?.toUpperCase() === 'STDIO' ? JSON.stringify(envObj) : null,
    url: formModel.value.transportType?.toUpperCase() === 'SSE' ? formModel.value.url : null,
    isActive: formModel.value.isActive
  }

  const url = editingServer.value ? `/api/mcp/${editingServer.value.id}` : '/api/mcp'
  const method = editingServer.value ? 'PUT' : 'POST'
  
  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (res.ok) {
      message.success(editingServer.value ? '已更新 MCP 服务' : '成功添加 MCP 服务')
      showModal.value = false
      fetchServers()
    } else {
      const err = await res.text()
      message.error('保存失败: ' + err)
    }
  } catch (err) {
    message.error('保存网络请求失败')
  }
}

async function toggleActive(server: any) {
  try {
    const res = await fetch(`/api/mcp/${server.id}/toggle`, { method: 'PUT' })
    if (res.ok) {
      server.isActive = !server.isActive
      message.success(server.isActive ? '服务已启用' : '服务已停用')
    } else {
      message.error('状态切换失败')
    }
  } catch (e) {
    message.error('状态切换失败')
  }
}

async function deleteServer(id: number) {
  try {
    const res = await fetch(`/api/mcp/${id}`, { method: 'DELETE' })
    if (res.ok) {
      message.success('已删除')
      fetchServers()
    } else {
      message.error('删除失败')
    }
  } catch (e) {
    message.error('删除失败')
  }
}

async function pingServer(id: number) {
  testingPing.value[id] = true
  try {
    const res = await fetch(`/api/mcp/${id}/ping`, { method: 'POST' })
    if (res.ok) {
      const data = await res.json()
      if (data.status === 'success') {
        currentTools.value = data.tools || []
        currentServerName.value = servers.value.find(s => s.id === id)?.name || ''
        showToolsModal.value = true
        message.success(`测通成功 (发现 ${currentTools.value.length} 个工具)`)
      } else {
        message.warning('通信异常: ' + (data.message || '未知错误'))
      }
    } else {
      message.error('测通请求失败')
    }
  } catch (e) {
    message.error('测通验证出错')
  } finally {
    testingPing.value[id] = false
  }
}

async function handleImport() {
  if (!importJsonText.value.trim()) {
    message.warning('请输入 JSON 配置')
    return
  }
  try {
    const res = await fetch('/api/mcp/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: importJsonText.value
    })
    if (res.ok) {
      message.success('导入成功')
      showImportModal.value = false
      importJsonText.value = ''
      fetchServers()
    } else {
      const err = await res.text()
      message.error('导入失败: ' + err)
    }
  } catch (err) {
    message.error('导入网络请求失败')
  }
}

function renderArgInput() {
  return {
    value: ''
  }
}

function renderEnvInput() {
  return {
    key: '',
    value: ''
  }
}
</script>

<template>
  <div class="mcp-settings-container">
    <div class="section-header">
      <n-icon :component="Plug" />
      <h2>MCP 插件列表</h2>
      <n-space class="create-btn">
        <n-button quaternary size="small" @click="showImportModal = true">
          <template #icon><n-icon :component="FileJson" /></template>
          从 JSON 导入
        </n-button>
        <n-button type="primary" size="small" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          添加服务
        </n-button>
      </n-space>
    </div>

    <n-spin :show="loading">
      <div v-if="servers.length === 0" class="empty-state">
        <n-empty description="暂未配置任何 MCP 服务" />
      </div>
      
      <div v-else class="servers-grid">
        <n-card v-for="s in servers" :key="s.id" class="glass-card server-card">
          <div class="server-header">
            <div class="server-title-group">
              <span class="server-icon" :class="{ 'active': s.isActive }">
                <n-icon :component="Server" />
              </span>
              <div class="server-info">
                <div class="server-name">
                  {{ s.name }}
                  <n-tag :type="s.transportType === 'stdio' ? 'info' : 'success'" size="small">
                    {{ s.transportType.toUpperCase() }}
                  </n-tag>
                </div>
                <div class="server-detail" v-if="s.transportType === 'stdio'">
                  {{ s.command }} {{ s.args ? JSON.parse(s.args).join(' ') : '' }}
                </div>
                <div class="server-detail" v-else>
                  {{ s.url }}
                </div>
              </div>
            </div>
            <div class="server-switch">
              <n-switch :value="s.isActive" @update:value="toggleActive(s)" />
            </div>
          </div>
          
          <div class="server-actions mt-4">
            <n-space>
              <n-button size="small" :loading="testingPing[s.id]" @click="pingServer(s.id)" :disabled="!s.isActive">
                <template #icon><n-icon :component="Activity" /></template>
                测通
              </n-button>
              <n-button size="small" @click="openEdit(s)">
                <template #icon><n-icon :component="Edit" /></template>
                编辑
              </n-button>
              <n-popconfirm @positive-click="deleteServer(s.id)">
                <template #trigger>
                  <n-button size="small" type="error" ghost>
                    <template #icon><n-icon :component="Trash2" /></template>
                  </n-button>
                </template>
                确定删除该 MCP 配置吗？这不会删除本地文件。
              </n-popconfirm>
            </n-space>
          </div>
        </n-card>
      </div>
    </n-spin>

    <n-modal 
      v-model:show="showModal" 
      preset="card" 
      :title="editingServer ? '编辑 MCP 服务' : '添加 MCP 服务'" 
      class="mcp-modal"
      :style="{ width: '600px' }"
    >
      <n-form label-placement="top">
        <n-form-item label="服务名称 (唯一)">
          <n-input v-model:value="formModel.name" placeholder="例如: mysql-client" />
        </n-form-item>
        
        <n-form-item label="传输通道 (Transport Integration)">
          <n-select v-model:value="formModel.transportType" :options="transportOptions" />
        </n-form-item>
        
        <template v-if="formModel.transportType?.toUpperCase() === 'STDIO'">
          <n-form-item label="启动指令 (Command)">
            <n-input v-model:value="formModel.command" placeholder="例如: npx, python, docker..." />
          </n-form-item>
          
          <n-form-item label="启动参数 (Arguments)">
            <n-dynamic-input v-model:value="formModel.args" placeholder="参数值" :on-create="renderArgInput">
              <template #default="{ index }">
                 <n-input v-model:value="formModel.args[index]" placeholder="参数" />
              </template>
            </n-dynamic-input>
          </n-form-item>

          <n-form-item label="环境变量 (Environment Variables)">
            <n-dynamic-input v-model:value="formModel.envVars" :on-create="renderEnvInput">
              <template #default="{ index }">
                <div style="display: flex; gap: 8px; width: 100%;">
                  <n-input v-model:value="formModel.envVars[index].key" placeholder="Key" style="flex: 1" />
                  <n-input v-model:value="formModel.envVars[index].value" placeholder="Value" style="flex: 2" />
                </div>
              </template>
            </n-dynamic-input>
          </n-form-item>
        </template>
        
        <template v-if="formModel.transportType?.toUpperCase() === 'SSE'">
          <n-form-item label="SSE 订阅 URL">
            <n-input v-model:value="formModel.url" placeholder="http://localhost:8080/mcp/sse" />
          </n-form-item>
        </template>

        <n-form-item label="启用状态">
          <n-switch v-model:value="formModel.isActive" />
        </n-form-item>
      </n-form>
      
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 12px;">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" @click="saveServer">保存配置</n-button>
        </div>
      </template>
    </n-modal>

    <n-modal 
      v-model:show="showImportModal" 
      preset="card" 
      title="从 JSON 导入配置" 
      class="mcp-modal"
      :style="{ width: '500px' }"
    >
      <div class="import-hint mb-4">
        支持标准 MCP 格式（包含 "mcpServers" 对象）。
      </div>
      <n-input
        v-model:value="importJsonText"
        type="textarea"
        :rows="12"
        placeholder='{ "mcpServers": { ... } }'
        style="font-family: monospace;"
      />
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 12px;">
          <n-button @click="showImportModal = false">取消</n-button>
          <n-button type="primary" @click="handleImport">执行导入</n-button>
        </div>
      </template>
    </n-modal>

    <n-modal 
      v-model:show="showToolsModal" 
      preset="card" 
      :title="'工具详情: ' + currentServerName" 
      class="mcp-modal"
      :style="{ width: '600px' }"
    >
      <div v-if="currentTools.length === 0" style="padding: 20px; text-align: center;">
        <n-empty description="该服务未发现任何可用工具" />
      </div>
      <div v-else class="tools-list">
        <div v-for="t in currentTools" :key="t.name" class="tool-item">
          <div class="tool-name">{{ t.name }}</div>
          <div class="tool-desc">{{ t.description || '无描述' }}</div>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.mcp-settings-container {
  padding: 16px 0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
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

.empty-state {
  padding: 40px;
  background: var(--color-glass-bg);
  border-radius: 16px;
  border: 1px dashed var(--color-outline);
}

.servers-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
}

.glass-card {
  background: var(--color-glass-bg) !important;
  backdrop-filter: blur(20px);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: 16px !important;
  transition: all 0.2s ease;
}

.glass-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px -10px rgba(0,0,0,0.15);
}

.server-card {
  padding: 16px;
}

.server-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.server-title-group {
  display: flex;
  gap: 12px;
  flex: 1;
}

.server-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,0.05);
  color: var(--color-text-dim);
  transition: all 0.3s ease;
}

.server-icon.active {
  background: var(--color-primary-dim);
  color: var(--color-primary);
}

.server-info {
  flex: 1;
  overflow: hidden;
}

.server-name {
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.server-detail {
  font-size: 12px;
  color: var(--color-text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: monospace;
  background: rgba(0,0,0,0.03);
  padding: 2px 6px;
  border-radius: 4px;
}

.mt-4 {
  margin-top: 16px;
}

.server-actions {
  display: flex;
  justify-content: flex-end;
  border-top: 1px dashed var(--color-outline);
  padding-top: 12px;
}

.mcp-modal {
  :deep(.n-card) {
    background: var(--color-background) !important;
    border-radius: 16px !important;
  }
}

.import-hint {
  font-size: 13px;
  color: var(--color-text-dim);
  margin-bottom: 12px;
  padding: 8px 12px;
  background: rgba(0,0,0,0.05);
  border-radius: 8px;
  border-left: 3px solid var(--color-primary);
}

.mb-4 { margin-bottom: 16px; }

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
  padding-right: 4px;
}

.tool-item {
  padding: 12px;
  background: rgba(0,0,0,0.03);
  border-radius: 8px;
  border-left: 4px solid var(--color-primary);
}

.tool-name {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
  color: var(--color-primary);
  font-family: monospace;
}

.tool-desc {
  font-size: 13px;
  color: var(--color-text-dim);
  line-height: 1.5;
}
</style>
