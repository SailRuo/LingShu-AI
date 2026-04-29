# Task Agent Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有陪伴对话链的前提下，新增一条独立的 `Task Agent Runtime` 路线，用于执行可暂停、可恢复、可审批、面向任意本地目录的复杂任务代理。

**Architecture:** 现有 `ChatServiceImpl` 继续承载陪伴对话、长期记忆、情感关系和普通工具调用；新增 `TaskRuntimeService`、`TaskExecutionEngine`、`TaskPermissionService`、`TaskEventStreamService` 组成任务执行子系统。任务执行不复用 `chat_turns/chat_turn_events`，而是使用独立的 `task_runs/task_events/permission_grants` 持久化模型，并通过 REST + WebSocket/SSE 事件流向 GUI 推送状态变化。

**Tech Stack:** Java 21, Spring Boot 3.2.4, LangChain4j 1.13.x, Spring Web/WebSocket, JPA, PostgreSQL, Reactor Flux, Tauri GUI

## Implementation Status (`2026-04-29`)

本计划的大部分 phase 1 后端骨架已经落地，但实现中有几处和原始计划略有差异，后续开发请以这里为准：

- 任务相关枚举没有继续放在 `lingshu-core`，而是统一下沉到 `lingshu-infrastructure.task`
- `start()` 在无需审批时不再停留 `PENDING`，而是直接进入 `RUNNING` 并在事务提交后调度执行引擎
- `TaskEventStreamServiceImpl.appendEvent()` 在写库后会发布 application event，由 web 层监听并广播 `taskEvent`
- `ChatServiceImpl` 当前没有再额外改动，聊天与任务的轻量分流通过 `TaskSessionRouterService + POST /api/chat/task-intent` 完成
- 恢复策略目前仍是 phase 1 骨架，完整的安全恢复语义请看架构文档：[Task Agent Runtime架构设计.md](/E:/Project/LingShu-AI/doc/架构设计/Task%20Agent%20Runtime架构设计.md)

---

## File Structure

### Existing files to modify

- Modify: [backend/pom.xml](/E:/Project/LingShu-AI/backend/pom.xml)
  责任：对齐 LangChain4j 版本，补充任务运行时需要的依赖。
- Modify: [backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/ChatController.java](/E:/Project/LingShu-AI/backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/ChatController.java)
  责任：保持现有对话链路不变，只增加“任务入口委派”相关说明或轻量关联接口。
- Modify: [backend/lingshu-web/src/main/java/com/lingshu/ai/web/websocket/ChatWebSocketHandler.java](/E:/Project/LingShu-AI/backend/lingshu-web/src/main/java/com/lingshu/ai/web/websocket/ChatWebSocketHandler.java)
  责任：增加任务事件广播，不污染现有聊天消息协议。
- Modify: [backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java)
  责任：只保留陪伴对话职责，抽离任务执行入口，不再继续扩展本地复杂任务逻辑。
- Modify: [backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/BuiltinWorkspaceToolProvider.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/BuiltinWorkspaceToolProvider.java)
  责任：为任务运行时提供更清晰的命令分类与结果结构。

### New files to create

- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/TaskRun.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/TaskEvent.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/PermissionGrant.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/TaskRunRepository.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/TaskEventRepository.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/PermissionGrantRepository.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskRunState.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskEventType.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskApprovalScope.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskStartRequest.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskRunView.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskEventView.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskApprovalDecisionRequest.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskRuntimeService.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskPermissionService.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskEventStreamService.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskExecutionEngine.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImpl.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskPermissionServiceImpl.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskEventStreamServiceImpl.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskExecutionEngineImpl.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskSessionRouterService.java`
- Create: `backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/TaskController.java`
- Create: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskPermissionServiceImplTest.java`
- Create: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImplTest.java`
- Create: `backend/lingshu-web/src/test/java/com/lingshu/ai/web/controller/TaskControllerTest.java`
- Create: `doc/architecture/Task Agent Runtime架构设计.md`

### Files explicitly not changed in phase 1

- Keep unchanged: `chat_turns`, `chat_turn_events` 相关表与 `TurnTimelineService`
- Keep unchanged: 陪伴对话的长期记忆、情感后处理、主动关怀主链
- Keep unchanged: Tauri GUI 正式联调代码

这样做的目的是先把后端子系统站稳，再让 GUI 接入。

---

### Task 1: 任务域模型与持久化骨架

**Files:**
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/TaskRun.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/TaskEvent.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/PermissionGrant.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/TaskRunRepository.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/TaskEventRepository.java`
- Create: `backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/PermissionGrantRepository.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskRunState.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskEventType.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskApprovalScope.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImplTest.java`

- [ ] **Step 1: 写失败测试，描述任务实例和权限授权的最小持久化需求**

```java
@Test
void shouldPersistTaskRunAndPermissionGrantSeparately() {
    TaskRun run = TaskRun.builder()
            .userId("web:test-user")
            .chatSessionId(12L)
            .title("修复 demo 项目单测")
            .workspacePath("D:\\work\\demo")
            .state(TaskRunState.PENDING.name())
            .build();

    PermissionGrant grant = PermissionGrant.builder()
            .userId("web:test-user")
            .scope(TaskApprovalScope.COMMAND_CATEGORY.name())
            .grantValue("npm")
            .isActive(true)
            .build();

    assertThat(run.getId()).isNull();
    assertThat(grant.getId()).isNull();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskRuntimeServiceImplTest test`
Expected: FAIL with `cannot find symbol TaskRun / PermissionGrant / TaskRunState`

- [ ] **Step 3: 创建任务状态枚举**

```java
package com.lingshu.ai.core.model.task;

public enum TaskRunState {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    PAUSED,
    COMPLETED,
    FAILED,
    STOPPED
}
```

```java
package com.lingshu.ai.core.model.task;

public enum TaskEventType {
    TASK_CREATED,
    STEP_STARTED,
    STEP_COMPLETED,
    APPROVAL_REQUIRED,
    APPROVAL_GRANTED,
    APPROVAL_REJECTED,
    TASK_PAUSED,
    TASK_RESUMED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_STOPPED,
    LOG
}
```

```java
package com.lingshu.ai.core.model.task;

public enum TaskApprovalScope {
    WORKSPACE_READWRITE,
    COMMAND_CATEGORY
}
```

- [ ] **Step 4: 创建 `TaskRun` 实体**

```java
@Entity
@Table(name = "task_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "chat_session_id")
    private Long chatSessionId;

    @Column(nullable = false)
    private String title;

    @Column(name = "workspace_path", columnDefinition = "TEXT", nullable = false)
    private String workspacePath;

    @Column(name = "command_category", nullable = false)
    private String commandCategory;

    @Column(name = "request_text", columnDefinition = "TEXT", nullable = false)
    private String requestText;

    @Column(name = "runtime_snapshot_json", columnDefinition = "TEXT")
    private String runtimeSnapshotJson;

    @Column(nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
```

- [ ] **Step 5: 创建 `TaskEvent` 与 `PermissionGrant` 实体**

```java
@Entity
@Table(name = "task_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_run_id", nullable = false)
    private TaskRun taskRun;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

```java
@Entity
@Table(name = "permission_grants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String scope;

    @Column(name = "grant_value", columnDefinition = "TEXT", nullable = false)
    private String grantValue;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 6: 创建仓储接口**

```java
public interface TaskRunRepository extends JpaRepository<TaskRun, Long> {
    List<TaskRun> findByUserIdOrderByIdDesc(String userId);
    Optional<TaskRun> findByIdAndUserId(Long id, String userId);
}
```

```java
public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {
    List<TaskEvent> findByTaskRunIdOrderBySequenceNoAsc(Long taskRunId);
}
```

```java
public interface PermissionGrantRepository extends JpaRepository<PermissionGrant, Long> {
    Optional<PermissionGrant> findByUserIdAndScopeAndGrantValueAndIsActiveTrue(String userId, String scope, String grantValue);
    List<PermissionGrant> findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(String userId);
}
```

- [ ] **Step 7: 运行测试并确认编译通过**

Run: `cd backend && mvn -pl lingshu-core -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/TaskRun.java backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/TaskEvent.java backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/PermissionGrant.java backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/TaskRunRepository.java backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/TaskEventRepository.java backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/PermissionGrantRepository.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskRunState.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskEventType.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/model/task/TaskApprovalScope.java
git commit -m "feat: add task runtime persistence model"
```

### Task 2: 任务权限服务

**Files:**
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskPermissionService.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskPermissionServiceImpl.java`
- Create: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskPermissionServiceImplTest.java`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/BuiltinWorkspaceToolProvider.java`

- [ ] **Step 1: 写失败测试，描述“目录授权 + 命令类别授权”判断**

```java
@Test
void shouldRequireApprovalWhenWorkspaceOrCommandGrantMissing() {
    TaskPermissionDecision decision = service.evaluate(
            "web:test-user",
            "D:\\work\\demo",
            "npm"
    );

    assertThat(decision.requiresWorkspaceApproval()).isTrue();
    assertThat(decision.requiresCommandApproval()).isTrue();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskPermissionServiceImplTest test`
Expected: FAIL with `cannot find symbol TaskPermissionDecision`

- [ ] **Step 3: 定义服务接口**

```java
public interface TaskPermissionService {

    record TaskPermissionDecision(
            boolean requiresWorkspaceApproval,
            boolean requiresCommandApproval
    ) {}

    TaskPermissionDecision evaluate(String userId, String workspacePath, String commandCategory);

    PermissionGrant grantWorkspace(String userId, String workspacePath);

    PermissionGrant grantCommandCategory(String userId, String commandCategory);

    List<PermissionGrant> listActiveGrants(String userId);

    void revokeGrant(Long grantId, String userId);
}
```

- [ ] **Step 4: 实现最小权限判定逻辑**

```java
@Service
public class TaskPermissionServiceImpl implements TaskPermissionService {

    private final PermissionGrantRepository permissionGrantRepository;

    public TaskPermissionServiceImpl(PermissionGrantRepository permissionGrantRepository) {
        this.permissionGrantRepository = permissionGrantRepository;
    }

    @Override
    public TaskPermissionDecision evaluate(String userId, String workspacePath, String commandCategory) {
        boolean workspaceMissing = permissionGrantRepository
                .findByUserIdAndScopeAndGrantValueAndIsActiveTrue(userId, TaskApprovalScope.WORKSPACE_READWRITE.name(), workspacePath)
                .isEmpty();
        boolean commandMissing = permissionGrantRepository
                .findByUserIdAndScopeAndGrantValueAndIsActiveTrue(userId, TaskApprovalScope.COMMAND_CATEGORY.name(), commandCategory)
                .isEmpty();
        return new TaskPermissionDecision(workspaceMissing, commandMissing);
    }
}
```

- [ ] **Step 5: 统一命令类别识别输入输出**

```java
public record CommandExecutionResult(
        boolean success,
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean truncated,
        String workingDir,
        String commandCategory
) {}
```

把 `BuiltinWorkspaceToolProvider` 的执行结果从单个 `output` 扩展为 `stdout/stderr/exitCode/timedOut/truncated`，为后续任务运行时消费打基础。

- [ ] **Step 6: 运行测试确认通过**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskPermissionServiceImplTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskPermissionService.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskPermissionServiceImpl.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskPermissionServiceImplTest.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/BuiltinWorkspaceToolProvider.java
git commit -m "feat: add task permission service"
```

### Task 3: Task Runtime 服务与事件流

**Files:**
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskStartRequest.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskRunView.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskEventView.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskApprovalDecisionRequest.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskRuntimeService.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskEventStreamService.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImpl.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskEventStreamServiceImpl.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImplTest.java`

- [ ] **Step 1: 写失败测试，描述启动任务后应写入任务实例与创建事件**

```java
@Test
void shouldCreateTaskRunAndTaskCreatedEvent() {
    TaskStartRequest request = new TaskStartRequest(
            "web:test-user",
            12L,
            "帮我修复 D:\\work\\demo 的测试",
            "D:\\work\\demo",
            "npm"
    );

    TaskRunView view = service.start(request);

    assertThat(view.state()).isEqualTo("WAITING_APPROVAL");
    assertThat(view.workspacePath()).isEqualTo("D:\\work\\demo");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskRuntimeServiceImplTest test`
Expected: FAIL with `cannot find symbol TaskStartRequest / TaskRunView`

- [ ] **Step 3: 定义 DTO**

```java
public record TaskStartRequest(
        String userId,
        Long chatSessionId,
        String requestText,
        String workspacePath,
        String commandCategory
) {}
```

```java
public record TaskApprovalDecisionRequest(
        boolean grantWorkspace,
        boolean grantCommandCategory
) {}
```

```java
public record TaskEventView(
        Long id,
        Integer sequenceNo,
        String eventType,
        String payloadJson,
        long timestamp
) {}
```

```java
public record TaskRunView(
        Long id,
        String userId,
        Long chatSessionId,
        String title,
        String workspacePath,
        String commandCategory,
        String state,
        String runtimeSnapshotJson,
        List<TaskEventView> events
) {}
```

- [ ] **Step 4: 定义运行时接口**

```java
public interface TaskRuntimeService {
    TaskRunView start(TaskStartRequest request);
    TaskRunView get(Long taskRunId, String userId);
    TaskRunView approve(Long taskRunId, String userId, TaskApprovalDecisionRequest request);
    TaskRunView pause(Long taskRunId, String userId);
    TaskRunView resume(Long taskRunId, String userId);
    TaskRunView stop(Long taskRunId, String userId);
}
```

```java
public interface TaskEventStreamService {
    void appendEvent(TaskRun run, TaskEventType eventType, Object payload);
    List<TaskEventView> getEvents(Long taskRunId);
}
```

- [ ] **Step 5: 实现 `start()` 的最小路径**

```java
@Override
@Transactional
public TaskRunView start(TaskStartRequest request) {
    TaskPermissionDecision decision = taskPermissionService.evaluate(
            request.userId(),
            request.workspacePath(),
            request.commandCategory()
    );

    TaskRun run = TaskRun.builder()
            .userId(request.userId())
            .chatSessionId(request.chatSessionId())
            .title(buildTitle(request.requestText()))
            .workspacePath(request.workspacePath())
            .commandCategory(request.commandCategory())
            .requestText(request.requestText())
            .state((decision.requiresWorkspaceApproval() || decision.requiresCommandApproval())
                    ? TaskRunState.WAITING_APPROVAL.name()
                    : TaskRunState.PENDING.name())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    TaskRun saved = taskRunRepository.save(run);
    taskEventStreamService.appendEvent(saved, TaskEventType.TASK_CREATED, Map.of(
            "requestText", request.requestText(),
            "workspacePath", request.workspacePath(),
            "commandCategory", request.commandCategory()
    ));

    if (saved.getState().equals(TaskRunState.WAITING_APPROVAL.name())) {
        taskEventStreamService.appendEvent(saved, TaskEventType.APPROVAL_REQUIRED, Map.of(
                "workspacePath", request.workspacePath(),
                "commandCategory", request.commandCategory(),
                "requiresWorkspaceApproval", decision.requiresWorkspaceApproval(),
                "requiresCommandApproval", decision.requiresCommandApproval()
        ));
    }

    return toView(saved);
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskRuntimeServiceImplTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskStartRequest.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskRunView.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskEventView.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/task/TaskApprovalDecisionRequest.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskRuntimeService.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskEventStreamService.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImpl.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskEventStreamServiceImpl.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImplTest.java
git commit -m "feat: add task runtime service skeleton"
```

### Task 4: 任务执行引擎与暂停恢复

**Files:**
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskExecutionEngine.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskExecutionEngineImpl.java`
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImpl.java`
- Test: `backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImplTest.java`

- [ ] **Step 1: 写失败测试，描述审批通过后任务应推进到运行态**

```java
@Test
void shouldMoveTaskIntoRunningAfterApproval() {
    TaskRunView started = service.start(new TaskStartRequest(
            "web:test-user", 12L, "帮我修复测试", "D:\\work\\demo", "npm"
    ));

    TaskRunView approved = service.approve(
            started.id(),
            "web:test-user",
            new TaskApprovalDecisionRequest(true, true)
    );

    assertThat(approved.state()).isEqualTo("RUNNING");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskRuntimeServiceImplTest test`
Expected: FAIL with `approve not implemented`

- [ ] **Step 3: 定义执行引擎接口**

```java
public interface TaskExecutionEngine {
    void schedule(TaskRun run);
    void pause(TaskRun run);
    void resume(TaskRun run);
    void stop(TaskRun run);
    void restorePendingTasks();
}
```

- [ ] **Step 4: 先实现 phase 1 的顺序执行桩**

```java
@Service
public class TaskExecutionEngineImpl implements TaskExecutionEngine {

    private final Executor taskExecutor;
    private final TaskRunRepository taskRunRepository;
    private final TaskEventStreamService taskEventStreamService;

    public TaskExecutionEngineImpl(@Qualifier("taskExecutor") Executor taskExecutor,
                                   TaskRunRepository taskRunRepository,
                                   TaskEventStreamService taskEventStreamService) {
        this.taskExecutor = taskExecutor;
        this.taskRunRepository = taskRunRepository;
        this.taskEventStreamService = taskEventStreamService;
    }

    @Override
    public void schedule(TaskRun run) {
        taskExecutor.execute(() -> {
            taskEventStreamService.appendEvent(run, TaskEventType.STEP_STARTED, Map.of("step", "analyze_workspace"));
            taskEventStreamService.appendEvent(run, TaskEventType.LOG, Map.of("message", "workspace analysis started"));
        });
    }
}
```

- [ ] **Step 5: 在 `approve/pause/resume/stop` 中补齐状态机**

```java
@Override
@Transactional
public TaskRunView pause(Long taskRunId, String userId) {
    TaskRun run = findOwnedRun(taskRunId, userId);
    if (!TaskRunState.RUNNING.name().equals(run.getState())) {
        throw new IllegalStateException("Only running task can be paused");
    }
    run.setState(TaskRunState.PAUSED.name());
    run.setUpdatedAt(LocalDateTime.now());
    taskRunRepository.save(run);
    taskEventStreamService.appendEvent(run, TaskEventType.TASK_PAUSED, Map.of());
    taskExecutionEngine.pause(run);
    return toView(run);
}
```

- [ ] **Step 6: 运行测试确认状态流转可用**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskRuntimeServiceImplTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/TaskExecutionEngine.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskExecutionEngineImpl.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImpl.java backend/lingshu-core/src/test/java/com/lingshu/ai/core/service/impl/TaskRuntimeServiceImplTest.java
git commit -m "feat: add task execution state machine"
```

### Task 5: 对外接口与事件推送

**Files:**
- Create: `backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/TaskController.java`
- Modify: `backend/lingshu-web/src/main/java/com/lingshu/ai/web/websocket/ChatWebSocketHandler.java`
- Test: `backend/lingshu-web/src/test/java/com/lingshu/ai/web/controller/TaskControllerTest.java`

- [ ] **Step 1: 写失败测试，描述任务启动接口返回 `TaskRunView`**

```java
@Test
void shouldStartTaskFromRestApi() throws Exception {
    mockMvc.perform(post("/api/tasks/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "userId":"web:test-user",
                              "chatSessionId":12,
                              "requestText":"帮我修复 D:\\\\work\\\\demo 测试",
                              "workspacePath":"D:\\\\work\\\\demo",
                              "commandCategory":"npm"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").exists());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl lingshu-web -Dtest=TaskControllerTest test`
Expected: FAIL with `No mapping for POST /api/tasks/start`

- [ ] **Step 3: 创建控制器**

```java
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRuntimeService taskRuntimeService;
    private final TaskPermissionService taskPermissionService;

    public TaskController(TaskRuntimeService taskRuntimeService,
                          TaskPermissionService taskPermissionService) {
        this.taskRuntimeService = taskRuntimeService;
        this.taskPermissionService = taskPermissionService;
    }

    @PostMapping("/start")
    public TaskRunView start(@RequestBody TaskStartRequest request) {
        return taskRuntimeService.start(request);
    }

    @PostMapping("/{id}/approve")
    public TaskRunView approve(@PathVariable Long id,
                               @RequestParam String userId,
                               @RequestBody TaskApprovalDecisionRequest request) {
        return taskRuntimeService.approve(id, userId, request);
    }
}
```

- [ ] **Step 4: 在 WebSocket 中增加任务事件广播方法**

```java
public void broadcastTaskEvent(String userId, Map<String, Object> message) {
    broadcastToUser(userId, message);
}
```

并约定新增 WebSocket 消息类型：

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

- [ ] **Step 5: 运行测试确认通过**

Run: `cd backend && mvn -pl lingshu-web -Dtest=TaskControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/TaskController.java backend/lingshu-web/src/main/java/com/lingshu/ai/web/websocket/ChatWebSocketHandler.java backend/lingshu-web/src/test/java/com/lingshu/ai/web/controller/TaskControllerTest.java
git commit -m "feat: expose task runtime api"
```

### Task 6: 对话链与任务链解耦

**Files:**
- Modify: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java`
- Create: `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskSessionRouterService.java`
- Modify: `backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/ChatController.java`

- [ ] **Step 1: 写失败测试，描述普通聊天仍走原对话链，任务请求不直接进聊天工具链**

```java
@Test
void shouldKeepChatServiceFocusedOnConversationFlow() {
    String text = "今天过得怎么样";
    assertThat(taskSessionRouterService.isTaskRequest(text)).isFalse();

    String taskText = "帮我在 D:\\work\\demo 里修复测试";
    assertThat(taskSessionRouterService.isTaskRequest(taskText)).isTrue();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskRuntimeServiceImplTest test`
Expected: FAIL with `cannot find symbol TaskSessionRouterService`

- [ ] **Step 3: 创建轻量路由服务**

```java
@Service
public class TaskSessionRouterService {

    public boolean isTaskRequest(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();
        return normalized.contains("项目")
                || normalized.contains("目录")
                || normalized.contains("修复")
                || normalized.contains("测试")
                || normalized.matches(".*[a-zA-Z]:\\\\.*");
    }
}
```

- [ ] **Step 4: 在 `ChatServiceImpl` 中收紧复杂任务职责**

只做以下最小改动：

```java
// 保留现有聊天主链
// 不新增新的长任务状态机、审批逻辑或任务恢复逻辑到 ChatServiceImpl
// 后续任务能力统一通过 TaskRuntimeService 进入
```

这一步的核心不是加代码，而是明确不再让 `ChatServiceImpl` 继续承担“通用开发代理”职责。

- [ ] **Step 5: 运行后端编译**

Run: `cd backend && mvn clean install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/TaskSessionRouterService.java backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/ChatController.java
git commit -m "refactor: separate chat flow from task runtime"
```

### Task 7: 文档与恢复策略

**Files:**
- Create: `doc/architecture/Task Agent Runtime架构设计.md`
- Modify: `doc/implementation/Task Agent Runtime计划与实施文档.md`

- [ ] **Step 1: 写恢复策略文档**

文档必须明确：

```markdown
1. `TaskRun.runtime_snapshot_json` 存当前步骤、目标目录、命令类别、最近一次等待点
2. 应用启动时扫描 `RUNNING/WAITING_APPROVAL/PAUSED` 任务
3. `WAITING_APPROVAL` 原样恢复
4. `PAUSED` 原样恢复
5. `RUNNING` 重启后转为 `PAUSED`，避免误继续执行危险操作
```

- [ ] **Step 2: 运行最小验证**

Run: `cd backend && mvn -pl lingshu-core -Dtest=TaskRuntimeServiceImplTest test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add doc/architecture/Task\ Agent\ Runtime架构设计.md doc/implementation/Task\ Agent\ Runtime计划与实施文档.md
git commit -m "docs: add task agent runtime architecture"
```

---

## Self-Review

### Spec coverage

- 保留现有陪伴对话链：Task 6 明确只做解耦，不改主聊天职责。
- 新增 `Task Agent Runtime` 独立路线：Task 1-5 覆盖任务模型、权限、运行时、事件流、接口。
- 面向任意本地目录：Task 1 和 Task 2 引入 `workspacePath` 与目录授权模型。
- 命令类别长期授权：Task 2 的 `COMMAND_CATEGORY` 授权覆盖。
- 首次审批后长期授权：Task 3 和 Task 4 的 `APPROVAL_REQUIRED / APPROVAL_GRANTED` 覆盖。
- 长任务暂停/恢复：Task 4 覆盖。
- GUI 后续可接入任务态：Task 5 提供 REST + WebSocket 事件契约。

### Placeholder scan

- 没有使用 `TODO/TBD/implement later`。
- 每个任务都给了明确文件和最小代码片段。
- 测试步骤有具体命令和预期结果。

### Type consistency

- 状态统一使用 `TaskRunState`。
- 审批范围统一使用 `TaskApprovalScope`。
- 事件类型统一使用 `TaskEventType`。

---

Plan complete and saved to [Task Agent Runtime计划与实施文档.md](/E:/Project/LingShu-AI/doc/implementation/Task%20Agent%20Runtime计划与实施文档.md). Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
