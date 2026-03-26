<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NButton,
  NDataTable,
  NTag,
  NProgress,
  NIcon,
  useMessage,
  NPopconfirm,
  NSpace,
  NSelect
} from 'naive-ui'
import { DatabaseBackup, Archive, RefreshCw, Trash2 } from 'lucide-vue-next'

const message = useMessage()

interface FactRecord {
  id: number
  content: string
  clusterKey: string
  subType: string
  status: string
  confidence: number
  activityScore: number
  lastActivatedAt: string
  classificationSource: string
}

const tableData = ref<FactRecord[]>([])
const isLoading = ref(false)
const pagination = ref({ page: 1, pageSize: 50, itemCount: 0 })
const filterStatus = ref('all')

const statusOptions = [
  { label: '全部状态', value: 'all' },
  { label: '活跃 (active)', value: 'active' },
  { label: '稳定 (stable)', value: 'stable' },
  { label: '冷却 (cool)', value: 'cool' },
  { label: '已被替代 (superseded)', value: 'superseded' },
  { label: '冲突 (conflicted)', value: 'conflicted' },
  { label: '已归档 (archived)', value: 'archived' }
]

const columns = [
  { title: 'ID', key: 'id', width: 60, align: 'center' as const },
  { title: '记忆内容', key: 'content', ellipsis: { tooltip: true } },
  {
    title: '主题域',
    key: 'clusterKey',
    width: 120,
    render: (row: FactRecord) => row.clusterKey || '无'
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row: FactRecord) => {
      let type: 'default' | 'error' | 'info' | 'success' | 'warning' = 'default'
      if (row.status === 'active') type = 'success'
      else if (row.status === 'stable') type = 'info'
      else if (row.status === 'archived') type = 'default'
      else if (row.status === 'superseded') type = 'warning'
      else if (row.status === 'conflicted') type = 'error'

      return h(NTag, { type, size: 'small', bordered: false }, { default: () => row.status })
    }
  },
  {
    title: '活跃度',
    key: 'activityScore',
    width: 120,
    render: (row: FactRecord) => {
      const p = (row.activityScore || 0) * 100
      let color = 'var(--color-primary)'
      if (p < 30) color = 'var(--color-text-dim)'
      else if (p > 75) color = 'var(--color-success)'
      return h(NProgress, {
        type: 'line',
        percentage: p,
        showIndicator: false,
        height: 6,
        color
      })
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    align: 'center' as const,
    render(row: FactRecord) {
      const isArchived = row.status === 'archived'
      return h(NSpace, { justify: 'center' }, {
        default: () => [
          isArchived
            ? h(
                NButton,
                { size: 'small', secondary: true, type: 'primary', onClick: () => handleRestore(row.id) },
                { default: () => '恢复', icon: () => h(NIcon, null, { default: () => h(RefreshCw) }) }
              )
            : h(
                NPopconfirm,
                { onPositiveClick: () => handleArchive(row.id) },
                {
                  default: () => '确定要将此记忆移入冷库吗？图谱将不再渲染。',
                  trigger: () => h(NButton, { size: 'small', secondary: true, type: 'warning' }, { default: () => '归档', icon: () => h(NIcon, null, { default: () => h(Archive) }) })
                }
              ),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row.id) },
            {
              default: () => '物理删除将不可恢复，确认删除？',
              trigger: () => h(NButton, { size: 'small', secondary: true, type: 'error' }, { icon: () => h(NIcon, null, { default: () => h(Trash2) }) })
            }
          )
        ]
      })
    }
  }
]

import { h } from 'vue'

const loadData = async () => {
  isLoading.value = true
  try {
    const res = await fetch(`http://localhost:8080/api/memory/governance/list?page=${pagination.value.page - 1}&size=${pagination.value.pageSize}&status=${filterStatus.value}`)
    if (!res.ok) throw new Error('网络请求失败')
    const data = await res.json()
    tableData.value = data.content || []
    pagination.value.itemCount = data.totalElements || 0
  } catch (err) {
    message.error('加载列表失败')
    console.error(err)
  } finally {
    isLoading.value = false
  }
}

const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadData()
}

const handleArchive = async (id: number) => {
  try {
    const res = await fetch(`http://localhost:8080/api/memory/fact/${id}/archive`, { method: 'PUT' })
    if (res.ok) {
      message.success('已归档至冷库')
      loadData()
    } else throw new Error()
  } catch (e) {
    message.error('归档失败')
  }
}

const handleRestore = async (id: number) => {
  try {
    const res = await fetch(`http://localhost:8080/api/memory/fact/${id}/restore`, { method: 'PUT' })
    if (res.ok) {
      message.success('记忆已恢复激活')
      loadData()
    } else throw new Error()
  } catch (e) {
    message.error('恢复失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    const res = await fetch(`http://localhost:8080/api/memory/fact/${id}`, { method: 'DELETE' })
    if (res.ok) {
      message.success('已永久删除')
      loadData()
    } else throw new Error()
  } catch (e) {
    message.error('删除失败')
  }
}

const handleRunMaintenance = async () => {
  try {
    message.info('正在执行生命周期维护...')
    const res = await fetch(`http://localhost:8080/api/memory/maintenance/run`, { method: 'POST' })
    if (res.ok) {
      message.success('全局维护任务执行完成')
      loadData()
    } else {
      throw new Error()
    }
  } catch (e) {
    message.error('维护任务执行失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="governance-view">
    <header class="view-header">
      <div class="header-title">
        <NIcon :size="24" color="var(--color-primary)"><DatabaseBackup /></NIcon>
        <h2>后台记忆治理</h2>
      </div>
      <p class="header-desc">统一管理系统中的记忆实体。您可在此将低价值事实手动打入冷库（Archived），或触发全局维护进程。</p>
    </header>

    <div class="toolbar glass">
      <div class="toolbar-left">
        <NSelect
          v-model:value="filterStatus"
          :options="statusOptions"
          style="width: 180px"
          @update:value="() => { pagination.page = 1; loadData() }"
        />
        <NButton secondary @click="loadData">
          <template #icon><NIcon><RefreshCw /></NIcon></template>
          刷新
        </NButton>
      </div>
      <div class="toolbar-right">
        <NButton type="primary" @click="handleRunMaintenance">
          <template #icon><NIcon><Zap /></NIcon></template>
          立即执行全局维护清理
        </NButton>
      </div>
    </div>

    <div class="table-container glass">
      <NDataTable
        remote
        :columns="columns"
        :data="tableData"
        :loading="isLoading"
        :pagination="pagination"
        :row-key="(row) => row.id"
        @update:page="handlePageChange"
        class="custom-table"
      />
    </div>
  </div>
</template>

<style scoped>
.governance-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
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
</style>
