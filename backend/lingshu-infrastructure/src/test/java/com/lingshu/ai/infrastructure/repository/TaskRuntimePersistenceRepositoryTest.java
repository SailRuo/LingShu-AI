package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.PermissionGrant;
import com.lingshu.ai.infrastructure.entity.TaskEvent;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.task.TaskApprovalScope;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TaskRuntimePersistenceRepositoryTest.TestJpaConfiguration.class)
class TaskRuntimePersistenceRepositoryTest {

    @Autowired
    private TaskRunRepository taskRunRepository;

    @Autowired
    private TaskEventRepository taskEventRepository;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Test
    void saveTaskRun_shouldPersistEnumState() {
        TaskRun run = TaskRun.builder()
                .userId("web:test-user")
                .chatSessionId(12L)
                .title("Fix demo tests")
                .workspacePath("D:\\work\\demo")
                .commandCategory("npm")
                .requestText("Please fix failing tests in the workspace")
                .state(TaskRunState.PENDING)
                .createdAt(LocalDateTime.of(2026, 4, 29, 21, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 21, 5))
                .build();

        TaskRun saved = taskRunRepository.saveAndFlush(run);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getState()).isEqualTo(TaskRunState.PENDING);
    }

    @Test
    void findByTaskRunIdOrderBySequenceNoAsc_shouldReturnEventsInReplayOrder() {
        TaskRun run = taskRunRepository.saveAndFlush(TaskRun.builder()
                .userId("web:test-user")
                .chatSessionId(13L)
                .title("Replay task events")
                .workspacePath("D:\\work\\demo")
                .commandCategory("npm")
                .requestText("Collect task events")
                .state(TaskRunState.RUNNING)
                .createdAt(LocalDateTime.of(2026, 4, 29, 21, 10))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 21, 11))
                .build());

        taskEventRepository.save(TaskEvent.builder()
                .taskRun(run)
                .sequenceNo(2)
                .eventType(TaskEventType.STEP_COMPLETED)
                .payloadJson("{\"step\":\"analyze\"}")
                .createdAt(LocalDateTime.of(2026, 4, 29, 21, 12))
                .build());
        taskEventRepository.saveAndFlush(TaskEvent.builder()
                .taskRun(run)
                .sequenceNo(1)
                .eventType(TaskEventType.TASK_CREATED)
                .payloadJson("{\"step\":\"create\"}")
                .createdAt(LocalDateTime.of(2026, 4, 29, 21, 11))
                .build());

        List<TaskEvent> events = taskEventRepository.findByTaskRunIdOrderBySequenceNoAsc(run.getId());

        assertThat(events).hasSize(2);
        assertThat(events).extracting(TaskEvent::getSequenceNo).containsExactly(1, 2);
        assertThat(events).extracting(TaskEvent::getEventType)
                .containsExactly(TaskEventType.TASK_CREATED, TaskEventType.STEP_COMPLETED);
    }

    @Test
    void findByUserIdAndScopeAndGrantValueAndIsActiveTrue_shouldReturnMatchingActiveGrant() {
        permissionGrantRepository.saveAndFlush(PermissionGrant.builder()
                .userId("web:test-user")
                .scope(TaskApprovalScope.COMMAND_CATEGORY)
                .grantValue("npm")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 4, 29, 21, 20))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 21, 21))
                .build());

        PermissionGrant grant = permissionGrantRepository
                .findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                        "web:test-user",
                        TaskApprovalScope.COMMAND_CATEGORY,
                        "npm"
                )
                .orElse(null);

        assertThat(grant).isNotNull();
        assertThat(grant.getScope()).isEqualTo(TaskApprovalScope.COMMAND_CATEGORY);
        assertThat(grant.getGrantValue()).isEqualTo("npm");
        assertThat(grant.getIsActive()).isTrue();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {TaskRun.class, TaskEvent.class, PermissionGrant.class})
    @EnableJpaRepositories(basePackageClasses = {
            TaskRunRepository.class,
            TaskEventRepository.class,
            PermissionGrantRepository.class
    })
    static class TestJpaConfiguration {
    }
}
