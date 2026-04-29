package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.PermissionGrant;

import java.util.List;

public interface TaskPermissionService {

    record TaskPermissionDecision(
            boolean requiresWorkspaceApproval,
            boolean requiresCommandApproval
    ) {
    }

    TaskPermissionDecision evaluate(String userId, String workspacePath, String commandCategory);

    PermissionGrant grantWorkspace(String userId, String workspacePath);

    PermissionGrant grantCommandCategory(String userId, String commandCategory);

    List<PermissionGrant> listActiveGrants(String userId);

    void revokeGrant(Long grantId, String userId);
}
