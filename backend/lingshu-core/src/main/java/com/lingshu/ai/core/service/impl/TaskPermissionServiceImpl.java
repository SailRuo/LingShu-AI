package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.TaskPermissionService;
import com.lingshu.ai.infrastructure.entity.PermissionGrant;
import com.lingshu.ai.infrastructure.repository.PermissionGrantRepository;
import com.lingshu.ai.infrastructure.task.TaskApprovalScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskPermissionServiceImpl implements TaskPermissionService {

    private final PermissionGrantRepository permissionGrantRepository;

    public TaskPermissionServiceImpl(PermissionGrantRepository permissionGrantRepository) {
        this.permissionGrantRepository = permissionGrantRepository;
    }

    @Override
    public TaskPermissionDecision evaluate(String userId, String workspacePath, String commandCategory) {
        String normalizedUserId = requireValue(userId, "userId");
        String normalizedWorkspacePath = requireValue(workspacePath, "workspacePath");
        String normalizedCommandCategory = requireValue(commandCategory, "commandCategory");
        boolean workspaceMissing = permissionGrantRepository
                .findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                        normalizedUserId,
                        TaskApprovalScope.WORKSPACE_READWRITE,
                        normalizedWorkspacePath
                )
                .isEmpty();
        boolean commandMissing = permissionGrantRepository
                .findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                        normalizedUserId,
                        TaskApprovalScope.COMMAND_CATEGORY,
                        normalizedCommandCategory
                )
                .isEmpty();
        return new TaskPermissionDecision(workspaceMissing, commandMissing);
    }

    @Override
    @Transactional("transactionManager")
    public PermissionGrant grantWorkspace(String userId, String workspacePath) {
        return grant(userId, TaskApprovalScope.WORKSPACE_READWRITE, workspacePath);
    }

    @Override
    @Transactional("transactionManager")
    public PermissionGrant grantCommandCategory(String userId, String commandCategory) {
        return grant(userId, TaskApprovalScope.COMMAND_CATEGORY, commandCategory);
    }

    @Override
    public List<PermissionGrant> listActiveGrants(String userId) {
        return permissionGrantRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(requireValue(userId, "userId"));
    }

    @Override
    @Transactional("transactionManager")
    public void revokeGrant(Long grantId, String userId) {
        if (grantId == null) {
            throw new IllegalArgumentException("grantId must not be blank");
        }
        String normalizedUserId = requireValue(userId, "userId");
        PermissionGrant grant = permissionGrantRepository.findById(grantId)
                .orElseThrow(() -> new IllegalArgumentException("Grant not found: " + grantId));
        if (!normalizedUserId.equals(grant.getUserId())) {
            throw new IllegalArgumentException("Grant does not belong to user: " + grantId);
        }
        if (!Boolean.TRUE.equals(grant.getIsActive())) {
            return;
        }
        grant.setIsActive(false);
        grant.setUpdatedAt(LocalDateTime.now());
        permissionGrantRepository.save(grant);
    }

    private PermissionGrant grant(String userId, TaskApprovalScope scope, String grantValue) {
        String normalizedUserId = requireValue(userId, "userId");
        String normalizedGrantValue = requireValue(grantValue, "grantValue");
        return permissionGrantRepository.findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
                        normalizedUserId,
                        scope,
                        normalizedGrantValue
                )
                .orElseGet(() -> permissionGrantRepository.save(PermissionGrant.builder()
                        .userId(normalizedUserId)
                        .scope(scope)
                        .grantValue(normalizedGrantValue)
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
