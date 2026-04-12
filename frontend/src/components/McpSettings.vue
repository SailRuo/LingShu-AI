<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { getFullUrl } from '@/utils/request'
import {
  NButton, NIcon, NTag, NSwitch, NPopconfirm, NModal,
  NForm, NFormItem, NInput, NSelect, useMessage, NSpace, NEmpty,
  NSpin, NDynamicInput, NDataTable, NTooltip
} from 'naive-ui'
import { Plus, Trash2, Edit, Plug, FileJson, RefreshCw } from 'lucide-vue-next'

const message = useMessage()
const loading = ref(false)
const servers = ref<any[]>([])
const showModal = ref(false)
const showImportModal = ref(false)
const importJsonText = ref('')
const editingServer = ref<any>(null)
const togglingServer = ref<Record<number, boolean>>({})

const formModel = ref({
  name: '',
  transportType: 'STDIO',
  command: '',
  args: [] as string[],
  envVars: [] as { key: string, value: string }[],
  headers: [] as { key: string, value: string }[],
  url: '',
  isActive: true
})

const columns = computed(() => [
  {
    title: '服务名称',
    key: 'name',
    width: 240,
    ellipsis: { tooltip: true },
    render: (row: any) => {
      return h('span', { style: { fontWeight: 600, fontSize: '14px' } }, row.name)
    }
  },
  {
    title: '类型',
    key: 'transportType',
    width: 160,
    align: 'center' as const,
    render: (row: any) => {
      const type = (row.transportType || '').toLowerCase()
      return h(NTag, { 
        type: type === 'stdio' ? 'info' : 'success', 
        size: 'small', 
        bordered: false,
        round: true
      }, {
        default: () => type.toUpperCase()
      })
    }
  },
  {
    title: '工具信息',
    key: 'tools',
    minWidth: 300,
    ellipsis: { tooltip: true },
    render: (row: any) => {
      if (row.tools && row.tools.length > 0) {
        return h(NSpace, { size: 4 }, {
          default: () => row.tools.map((t: any) => 
            h(NTooltip, { 
              trigger: 'hover', 
              placement: 'top',
              style: { maxWidth: '320px' }
            }, {
              trigger: () => h(NTag, { 
                size: 'small', 
                round: true, 
                bordered: true,
                style: { 
                  background: 'rgba(var(--color-primary-rgb), 0.05)',
                  color: 'var(--color-primary)',
                  borderColor: 'rgba(var(--color-primary-rgb), 0.2)',
                  fontSize: '11px',
                  cursor: 'help'
                }
              }, { default: () => t.name }),
              default: () => t.description || '无描述'
            })
          )
        })
      }
      const detail = row.transportType === 'stdio' 
        ? `${row.command || ''} ${row.args ? JSON.parse(row.args).join(' ') : ''}`
        : row.url || ''
      return h('div', {
        style: { 
          fontSize: '13px', 
          color: 'var(--color-text-dim)',
          fontFamily: 'monospace'
        }
      }, detail)
    }
  },
  {
    title: '状态',
    key: 'isActive',
    width: 80,
    align: 'center' as const,
    render: (row: any) => {
      return h(NSwitch, {
        size: 'small',
        value: row.isActive,
        loading: togglingServer.value[row.id] || false,
        onUpdateValue: () => toggleActive(row)
      })
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    align: 'center' as const,
    render: (row: any) => {
      return h(NSpace, { justify: 'center' }, {
        default: () => [
          h(NButton, {
            size: 'small',
            onClick: () => exportServer(row),
            secondary: true
          }, {
            icon: () => h(NIcon, { component: FileJson, size: 16 })
          }),
          h(NButton, {
            size: 'small',
            onClick: () => openEdit(row),
            secondary: true
          }, {
            icon: () => h(NIcon, { component: Edit, size: 16 })
          }),
          h(NPopconfirm, {
            onPositiveClick: () => deleteServer(row.id)
          }, {
            trigger: () => h(NButton, {
              size: 'small',
              type: 'error',
              secondary: true
            }, {
              icon: () => h(NIcon, { component: Trash2, size: 16 })
            }),
            default: () => '确定删除该 MCP 配置吗？这不会删除本地文件。'
          })
        ]
      })
    }
  }
])

const transportOptions = [
  { label: 'STDIO (本地子进程)', value: 'STDIO' },
  { label: 'SSE (HTTP 长链接)', value: 'SSE' },
  { label: 'Streamable HTTP (可流式 HTTP)', value: 'STREAMABLE_HTTP' }
]

async function fetchServers() {
  loading.value = true
  try {
    const res = await fetch(getFullUrl('/api/mcp'))
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
    headers: [],
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
  let headers: {key: string, value: string}[] = []
  if (server.headers) {
    try {
      const parsed = JSON.parse(server.headers)
      headers = Object.keys(parsed).map(k => ({ key: k, value: parsed[k] }))
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
    headers: headers,
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

  const headersObj: Record<string, string> = {}
  formModel.value.headers.forEach(item => {
    if (item.key && item.value) {
      headersObj[item.key] = item.value
    }
  })
  
  const payload = {
    name: formModel.value.name,
    transportType: formModel.value.transportType,
    command: formModel.value.transportType?.toUpperCase() === 'STDIO' ? formModel.value.command : null,
    args: formModel.value.transportType?.toUpperCase() === 'STDIO' ? JSON.stringify(formModel.value.args) : null,
    env: formModel.value.transportType?.toUpperCase() === 'STDIO' ? JSON.stringify(envObj) : null,
    headers: ['SSE', 'STREAMABLE_HTTP'].includes(formModel.value.transportType?.toUpperCase() || '') ? JSON.stringify(headersObj) : null,
    url: ['SSE', 'STREAMABLE_HTTP'].includes(formModel.value.transportType?.toUpperCase() || '') ? formModel.value.url : null,
    isActive: formModel.value.isActive
  }

  const url = editingServer.value ? getFullUrl(`/api/mcp/${editingServer.value.id}`) : getFullUrl('/api/mcp')
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
  const serverId = server.id
  togglingServer.value[serverId] = true
  
  try {
    const res = await fetch(getFullUrl(`/api/mcp/${serverId}/toggle`), { method: 'POST' })
    if (res.ok) {
      server.isActive = !server.isActive
      message.success(server.isActive ? '服务已启用' : '服务已停用')
    } else {
      message.error('状态切换失败')
    }
  } catch (e) {
    message.error('状态切换失败')
  } finally {
    togglingServer.value[serverId] = false
  }
}

async function deleteServer(id: number) {
  try {
    const res = await fetch(getFullUrl(`/api/mcp/${id}`), { method: 'DELETE' })
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

function exportServer(server: any) {
  const mcpConfig: any = {}
  const serverName = server.name || 'mcp-server'
  
  if (['SSE', 'STREAMABLE_HTTP'].includes(server.transportType?.toUpperCase() || '')) {
    mcpConfig[serverName] = {
      url: server.url,
      transportType: server.transportType,
      headers: server.headers ? JSON.parse(server.headers) : {}
    }
  } else {
    try {
      mcpConfig[serverName] = {
        command: server.command,
        args: server.args ? JSON.parse(server.args) : [],
        env: server.env ? JSON.parse(server.env) : {}
      }
    } catch (e) {
      mcpConfig[serverName] = {
        command: server.command,
        args: [],
        env: {}
      }
    }
  }
  
  const blob = new Blob([JSON.stringify({ mcpServers: mcpConfig }, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${serverName}.json`
  a.click()
  URL.revokeObjectURL(url)
  message.success('配置已导出')
}


async function handleImport() {
  if (!importJsonText.value.trim()) {
    message.warning('请输入 JSON 配置')
    return
  }
  try {
    const res = await fetch(getFullUrl('/api/mcp/import'), {
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
  <div class="mcp-settings-view">
    <header class="view-header">
      <div class="header-title">
        <n-icon :size="24" color="var(--color-primary)"><Plug /></n-icon>
        <h2>MCP 插件管理</h2>
      </div>
      <p class="header-desc">通过 Model Context Protocol (MCP) 扩展 AI 能力。您可以集成 StdIO 子进程或远程 SSE 服务，为模型提供实时工具调用能力。</p>
    </header>

    <div class="toolbar glass">
      <div class="toolbar-left">
        <n-button secondary @click="fetchServers">
          <template #icon><n-icon><RefreshCw /></n-icon></template>
          刷新列表
        </n-button>
      </div>
      <div class="toolbar-right">
        <n-space>
          <n-button secondary @click="showImportModal = true">
            <template #icon><n-icon :component="FileJson" /></template>
            从 JSON 导入
          </n-button>
          <n-button type="primary" @click="openCreate">
            <template #icon><n-icon :component="Plus" /></template>
            添加 MCP 服务
          </n-button>
        </n-space>
      </div>
    </div>

    <div class="table-container glass">
      <n-spin :show="loading">
        <div v-if="servers.length === 0" class="empty-state">
          <n-empty description="暂未配置任何 MCP 服务" />
        </div>
        <n-data-table
          v-else
          :columns="columns"
          :data="servers"
          :row-key="(row) => row.id"
          class="custom-table"
        />
      </n-spin>
    </div>

    <!-- Modals remain mostly the same but ensure consistent style -->
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
        
        <template v-if="['SSE', 'STREAMABLE_HTTP'].includes(formModel.transportType?.toUpperCase() || '')">
          <n-form-item label="SSE 订阅 URL">
            <n-input v-model:value="formModel.url" placeholder="http://localhost:8080/mcp/sse" />
          </n-form-item>
          
          <n-form-item label="请求头 (Headers)">
            <n-dynamic-input v-model:value="formModel.headers" :on-create="renderEnvInput">
              <template #default="{ index }">
                <div style="display: flex; gap: 8px; width: 100%;">
                  <n-input v-model:value="formModel.headers[index].key" placeholder="Key" style="flex: 1" />
                  <n-input v-model:value="formModel.headers[index].value" placeholder="Value" style="flex: 2" />
                </div>
              </template>
            </n-dynamic-input>
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
  </div>
</template>

<style scoped>
.mcp-settings-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  gap: 16px;
}

.view-header {
  flex-shrink: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.header-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.5px;
}

.header-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-dim);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(var(--color-surface-rgb), 0.3);
  border: 1px solid var(--color-outline);
}

.toolbar-left, .toolbar-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.table-container {
  flex: 1;
  min-height: 0;
  border-radius: 12px;
  background: rgba(var(--color-surface-rgb), 0.3);
  border: 1px solid var(--color-outline);
  overflow: hidden;
  padding: 16px;
}

.custom-table {
  height: 100%;
}

.empty-state {
  padding: 40px;
  text-align: center;
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


/* Glass effect fallback if not globally defined */
.glass {
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}
</style>

