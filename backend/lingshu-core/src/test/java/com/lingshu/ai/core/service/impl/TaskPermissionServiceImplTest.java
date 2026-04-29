package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.TaskPermissionService;
import com.lingshu.ai.infrastructure.entity.PermissionGrant;
import com.lingshu.ai.infrastructure.repository.PermissionGrantRepository;
import com.lingshu.ai.infrastructure.task.TaskApprovalScope;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskPermissionServiceImplTest {

    @Test
    void evaluate_shouldRequireApprovalWhenWorkspaceOrCommandGrantMissing() {
        PermissionGrantRepository repository = mock(PermissionGrantRepository.class);
        TaskPermissionService service = new TaskPermissionServiceImpl(repository);

        when(repository.findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                "web:test-user",
                TaskApprovalScope.WORKSPACE_READWRITE,
                "D:\\work\\demo"
        )).thenReturn(Optional.empty());
        when(repository.findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                "web:test-user",
                TaskApprovalScope.COMMAND_CATEGORY,
                "npm"
        )).thenReturn(Optional.empty());

        TaskPermissionService.TaskPermissionDecision decision = service.evaluate(
                "web:test-user",
                "D:\\work\\demo",
                "npm"
        );

        assertTrue(decision.requiresWorkspaceApproval());
        assertTrue(decision.requiresCommandApproval());
    }

    @Test
    void grantMethods_shouldPersistActiveGrantsAndReuseExistingOnes() {
        PermissionGrantRepository repository = mock(PermissionGrantRepository.class);
        TaskPermissionService service = new TaskPermissionServiceImpl(repository);

        PermissionGrant existingWorkspaceGrant = PermissionGrant.builder()
                .id(11L)
                .userId("web:test-user")
                .scope(TaskApprovalScope.WORKSPACE_READWRITE)
                .grantValue("D:\\work\\demo")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 4, 29, 22, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 22, 5))
                .build();

        PermissionGrant savedCommandGrant = PermissionGrant.builder()
                .id(12L)
                .userId("web:test-user")
                .scope(TaskApprovalScope.COMMAND_CATEGORY)
                .grantValue("npm")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 4, 29, 22, 6))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 22, 6))
                .build();

        when(repository.findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                "web:test-user",
                TaskApprovalScope.WORKSPACE_READWRITE,
                "D:\\work\\demo"
        )).thenReturn(Optional.of(existingWorkspaceGrant));
        when(repository.findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                "web:test-user",
                TaskApprovalScope.COMMAND_CATEGORY,
                "npm"
        )).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(PermissionGrant.class))).thenReturn(savedCommandGrant);

        PermissionGrant workspaceGrant = service.grantWorkspace("web:test-user", "D:\\work\\demo");
        PermissionGrant commandGrant = service.grantCommandCategory("web:test-user", "npm");

        assertSame(existingWorkspaceGrant, workspaceGrant);
        assertEquals(savedCommandGrant, commandGrant);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(grant ->
                "web:test-user".equals(grant.getUserId())
                        && TaskApprovalScope.COMMAND_CATEGORY == grant.getScope()
                        && "npm".equals(grant.getGrantValue())
                        && Boolean.TRUE.equals(grant.getIsActive())
                        && grant.getCreatedAt() != null
                        && grant.getUpdatedAt() != null
        ));
    }

    @Test
    void listAndRevoke_shouldReturnActiveGrantsAndDeactivateOwnedGrant() {
        PermissionGrantRepository repository = mock(PermissionGrantRepository.class);
        TaskPermissionService service = new TaskPermissionServiceImpl(repository);

        PermissionGrant grant = PermissionGrant.builder()
                .id(22L)
                .userId("web:test-user")
                .scope(TaskApprovalScope.COMMAND_CATEGORY)
                .grantValue("npm")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 4, 29, 22, 10))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 22, 11))
                .build();

        when(repository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc("web:test-user"))
                .thenReturn(List.of(grant));
        when(repository.findById(22L)).thenReturn(Optional.of(grant));

        List<PermissionGrant> activeGrants = service.listActiveGrants("web:test-user");
        service.revokeGrant(22L, "web:test-user");

        assertEquals(1, activeGrants.size());
        assertSame(grant, activeGrants.getFirst());
        assertFalse(grant.getIsActive());
        verify(repository).save(grant);
    }

    @Test
    void revokeGrant_shouldRejectGrantOwnedByAnotherUser() {
        PermissionGrantRepository repository = mock(PermissionGrantRepository.class);
        TaskPermissionService service = new TaskPermissionServiceImpl(repository);

        PermissionGrant grant = PermissionGrant.builder()
                .id(23L)
                .userId("web:other-user")
                .scope(TaskApprovalScope.COMMAND_CATEGORY)
                .grantValue("npm")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 4, 29, 22, 12))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 22, 13))
                .build();

        when(repository.findById(23L)).thenReturn(Optional.of(grant));

        assertThrows(IllegalArgumentException.class, () -> service.revokeGrant(23L, "web:test-user"));
    }
}
