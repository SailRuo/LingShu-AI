# Task Agent Runtime 架构设计

## 目标

在保留现有陪伴对话链的前提下，新增一条独立的 `Task Agent Runtime` 路线，用于处理面向任意本地目录的复杂任务执行，包括：

- 长任务状态跟踪
- 首次审批后的长期授权
- 本地目录读写与命令类别权限
- 任务事件流推送
- 暂停、恢复、停止

现有 `ChatServiceImpl` 继续负责陪伴对话、长期记忆和普通工具调用；任务执行不再塞回聊天主链。

## 当前落地范围

截至 `2026-04-29`，后端已完成的能力有：

- 独立持久化模型：`task_runs`、`task_events`、`permission_grants`
- 任务权限服务：目录读写授权、命令类别授权
- 任务运行时骨架：`start/get/approve/pause/resume/stop`
- 执行引擎 phase 1 桩：最小 `STEP_STARTED/LOG` 事件产出
- REST 接口：`/api/tasks/*`
- WebSocket 任务事件广播：`type=taskEvent`
- 聊天侧轻量路由判断：`POST /api/chat/task-intent`

## 模块边界

### 对话链

- 入口：`ChatController`
- 主服务：`ChatServiceImpl`
- 职责：陪伴式聊天、记忆检索、普通工具调用、对话流式输出

### 任务链

- 入口：`TaskController`
- 路由判断：`TaskSessionRouterService`
- 主服务：`TaskRuntimeServiceImpl`
- 执行：`TaskExecutionEngineImpl`
- 事件：`TaskEventStreamServiceImpl`
- 权限：`TaskPermissionServiceImpl`

## 核心数据模型

### TaskRun

代表一个任务实例，核心字段：

- `userId`
- `chatSessionId`
- `title`
- `workspacePath`
- `commandCategory`
- `requestText`
- `state`
- `runtimeSnapshotJson`
- `createdAt/updatedAt/completedAt`

### TaskEvent

代表任务事件流，核心字段：

- `taskRunId`
- `sequenceNo`
- `eventType`
- `payloadJson`
- `createdAt`

事件序号按任务内递增，并通过任务行锁 + 最新事件查询避免并发冲突。

### PermissionGrant

代表长期授权，核心字段：

- `userId`
- `scope`
- `grantValue`
- `isActive`
- `createdAt/updatedAt`

授权范围：

- `WORKSPACE_READWRITE`
- `COMMAND_CATEGORY`

## 状态机

当前后端采用如下状态：

- `WAITING_APPROVAL`
- `RUNNING`
- `PAUSED`
- `STOPPED`
- `COMPLETED`
- `FAILED`

当前 phase 1 的关键规则：

1. 启动任务时，如果缺少目录或命令类别授权，进入 `WAITING_APPROVAL`
2. 启动任务时，如果授权已齐全，直接进入 `RUNNING`
3. 审批不完整时，进入 `STOPPED`，并写入 `APPROVAL_REJECTED`
4. 审批通过时，进入 `RUNNING`，并调度执行引擎
5. `RUNNING -> PAUSED -> RUNNING` 支持人工暂停与恢复
6. `PENDING/RUNNING/WAITING_APPROVAL/PAUSED` 可人工停止到 `STOPPED`

## 权限模型

用户审批被拆成两类长期授权：

### 命令类别

例如：

- `git`
- `npm`
- `pnpm`
- `python`
- `mvn`
- `gradle`

### 目录读写

例如：

- `E:\Project\LingShu-AI`
- `D:\work\demo`

运行时会先评估是否缺少授权，再决定进入 `WAITING_APPROVAL` 还是直接执行。

## 事件广播

任务事件写入后，会发布 `TaskEventAppendedEvent`，由 web 层监听器在事务提交后广播给已注册的 WebSocket 会话。

消息结构：

```json
{
  "type": "taskEvent",
  "taskRunId": 101,
  "eventType": "APPROVAL_REQUIRED",
  "payload": {
    "workspacePath": "D:\\work\\demo",
    "commandCategory": "npm"
  }
}
```

这样可以避免 `web` 反向依赖 `core` 的执行细节，同时保证广播不早于事务提交。

## 聊天与任务解耦

当前采用“聊天侧判断，任务侧执行”的模式：

1. 前端或上层通过 `POST /api/chat/task-intent` 判断一条消息是否更像任务请求
2. 普通陪伴聊天继续走 `/api/chat/stream` 或 `/api/chat/sync`
3. 明显任务请求走 `/api/tasks/*`

这保证：

- `ChatServiceImpl` 不承担任务状态机职责
- `TaskRuntimeServiceImpl` 不承担陪伴对话职责
- 前端可以在同一会话中做“聊天内发起，进入任务态”

## 恢复策略

### 目标策略

推荐的安全恢复策略如下：

1. `TaskRun.runtime_snapshot_json` 存当前步骤、目标目录、命令类别、最近一次等待点
2. 应用启动时扫描 `RUNNING`、`WAITING_APPROVAL`、`PAUSED` 任务
3. `WAITING_APPROVAL` 原样恢复
4. `PAUSED` 原样恢复
5. `RUNNING` 在重启后转为 `PAUSED`，避免误继续执行危险操作

### 当前实现现状

当前 phase 1 还没有完整实现上面的安全恢复语义。

当前代码只具备：

- `TaskExecutionEngine.restorePendingTasks()` 的最小恢复入口
- 基于状态查询 `RUNNING` 任务并重新调度的骨架

因此，**当前实现适合作为开发期骨架，不应把“进程重启后安全恢复”视为已完全完成**。下一阶段需要把 `runtimeSnapshotJson`、启动扫描和 `RUNNING -> PAUSED` 转换正式落地。

## 后续建议

下一阶段建议优先补齐：

1. 启动恢复策略正式实现
2. 执行引擎与真实命令/工具链集成
3. 权限设置页查询与撤销接口
4. GUI 任务卡片与 WebSocket 事件流联调
5. 任务结果摘要与失败重试策略
