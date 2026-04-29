# Task Agent Runtime 进度报告

更新时间：`2026-04-30`

## 结论

`Task Agent Runtime` 当前已经完成了“运行时框架、权限链路、事件广播、前端任务态接入”的第一阶段工作，但**还没有完成真实任务执行能力**。

换句话说：

- 已完成：任务可以创建、授权、暂停、恢复、停止，前端也能显示任务卡片和实时事件
- 未完成：任务还不能真正深入分析项目、调用本地工具链执行复杂步骤、汇总结果并完成闭环

当前系统更准确的定位是：

- **已具备任务运行时骨架**
- **未具备完整任务执行智能**

---

## 已完成内容

### 1. 后端任务运行时基础设施

已完成独立于陪伴对话链的任务子系统，核心包括：

- `TaskRun / TaskEvent / PermissionGrant` 持久化模型
- `TaskRuntimeService` 任务生命周期管理
- `TaskPermissionService` 权限判断与授权写入
- `TaskEventStreamService` 任务事件落库与广播
- `TaskExecutionEngine` 骨架
- `TaskController` REST 接口
- `taskEvent` WebSocket 广播

这部分已经把任务和普通聊天做了职责分离，没有继续把复杂任务塞进 `ChatServiceImpl` 主链。

### 2. 权限模型

已经落地两类长期授权：

- 目录读写权限
- 命令类别权限

已支持：

- 首次触发审批
- 审批通过后长期授权
- 审批拒绝后停止任务
- 后续任务复用已有授权

### 3. 任务前端基础交互

`lingshu-gui` 已接入任务态基础体验：

- 聊天会话内显示任务卡片
- 任务卡片可展示状态、步骤、日志、摘要
- 支持审批、暂停、恢复、终止
- 支持会话级任务模式显式开关
- 支持任务消息按会话重建

### 4. 消息与主题相关改造

已完成：

- 文本消息实时 Markdown 渲染
- 任务卡片改为遵循现有主题变量体系

但这部分前端消息气泡样式仍有残余问题，尚未完全收口。

### 5. 工具失败污染上下文问题修复

已修复两个关键问题：

- MCP 工具失败结果现在会正确标记 `isError=true`
- 历史回放时会保留 `tool_end.is_error`，避免失败工具结果被当成成功上下文继续带入后续对话

同时补了空异常文案兜底：

- 不再只看到 `null`
- 至少会落成 `UnresolvedModelServerException: unknown error`

---

## 当前未完成内容

### 1. 真实任务执行能力

这是当前最关键的缺口。

目前的 [TaskExecutionEngineImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskExecutionEngineImpl.java) 只是骨架，实现上只会：

- 发 `STEP_STARTED`
- 发一条 `LOG`

然后结束。

当前**没有真正执行**以下能力：

- 扫描项目目录结构
- 识别关键文件
- 调用 `read_file`
- 调用 `execute_command`
- 调用 MCP 工具链
- 按步骤推进任务
- 产出完成态摘要

因此当前任务卡片虽然“看起来在运行”，但本质上仍是运行时骨架事件，不是真正的本地项目执行代理。

### 2. 任务规划能力

当前没有真正的 planner。

任务请求进入 runtime 后，并不会自动拆成：

- 目录分析
- 环境检查
- 命令执行
- 结果验证
- 汇总输出

现在只有一个固定的骨架步骤 `analyze_workspace`，远不足以支撑编程任务级别的复杂执行。

### 3. 前端任务气泡布局收口

由于实时 Markdown 渲染引入了块级布局变化，当前文本气泡布局仍不稳定，尚未完全恢复到原有会话视觉体验。

这部分属于前端展示问题，不影响后端任务运行时本身，但影响 GUI 质量。

### 4. 上游模型服务异常根因未抓透

虽然已经修复了：

- 工具失败协议错误
- 错误结果污染后续上下文
- 空异常文案

但 `OpenAiStreamingChatModel` 抛出的：

- `UnresolvedModelServerException: unknown error`

仍然只是“上层包装后的表现”，还没有抓到上游模型服务返回的**原始 HTTP / SSE 错误响应**。

这部分需要进一步补原始响应诊断日志。

---

## 当前实际状态判断

### 已经可以认为完成的部分

- 任务运行时基础设施
- 权限模型
- 任务事件链路
- 前端任务态入口
- 会话内任务卡片重建

### 还不能对外宣称完成的部分

- 任意本地项目的真实任务执行
- 编程任务级别的问题定位与修复
- 本地工具链编排执行
- 长任务的真正闭环产出

---

## 当前问题清单

### P0

- `TaskExecutionEngine` 仍是空心骨架，任务不会真正执行

### P1

- `UnresolvedModelServerException: unknown error` 还没抓到原始上游响应
- GUI 文本气泡在 Markdown 实时渲染后布局仍未完全修好

### P2

- 任务模式下缺少更明确的 workspace/权限辅助信息
- 任务结果摘要仍偏简单

---

## 下一步建议

### 第一优先级

直接实现 `TaskExecutionEngine v1`，不要再扩外围壳子。

建议第一版最小可行能力是：

1. 扫描 workspace 目录结构
2. 识别关键文件
3. 真实读取文件内容
4. 按命令类别执行一条真实命令
5. 记录多步日志
6. 正常产出 `TASK_COMPLETED / TASK_FAILED`
7. 回写任务摘要

### 第二优先级

补 `Task Planner v1`：

- 把用户任务请求拆解成最小步骤序列
- 不再只发固定 `analyze_workspace`

### 第三优先级

补模型服务诊断：

- 记录 OpenAI 兼容模型的原始 HTTP / SSE 错误响应
- 查清 `UnresolvedModelServerException(null)` 的真实上游原因

### 第四优先级

收口 GUI 文本气泡与任务卡片布局，让任务态真正融入现有会话体验。

---

## 真实评价

当前阶段的工作不是无效，但它主要解决的是：

- 任务系统怎么存在
- 怎么授权
- 怎么广播
- 怎么展示

而不是：

- 任务怎么真正干活

所以当前最重要的不是继续做 UI，也不是继续补外围，而是尽快把 `TaskExecutionEngine` 从“运行时骨架”升级成“真实执行引擎”。

如果只看用户体感，当前系统已经具备“任务代理的外形”，但还没有具备“任务代理的执行内核”。
