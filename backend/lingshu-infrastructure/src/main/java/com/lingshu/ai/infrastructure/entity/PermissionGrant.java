package com.lingshu.ai.infrastructure.entity;

import com.lingshu.ai.infrastructure.task.TaskApprovalScope;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "permission_grants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_permission_grants_user_scope_value_active",
                columnNames = {"user_id", "scope", "grant_value", "is_active"}
        ),
        indexes = {
                @Index(name = "idx_permission_grants_lookup", columnList = "user_id, scope, is_active"),
                @Index(name = "idx_permission_grants_user_active_updated", columnList = "user_id, is_active, updated_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskApprovalScope scope;

    @Column(name = "grant_value", columnDefinition = "TEXT", nullable = false)
    private String grantValue;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
