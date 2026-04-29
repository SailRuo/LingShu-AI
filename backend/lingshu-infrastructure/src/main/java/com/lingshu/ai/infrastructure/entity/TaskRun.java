package com.lingshu.ai.infrastructure.entity;

import com.lingshu.ai.infrastructure.task.TaskRunState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "task_runs",
        indexes = {
                @Index(name = "idx_task_runs_user_updated_id", columnList = "user_id, updated_at, id")
        }
)
@Getter
@Setter
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskRunState state;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
