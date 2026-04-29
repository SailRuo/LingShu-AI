package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.PermissionGrant;
import com.lingshu.ai.infrastructure.task.TaskApprovalScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionGrantRepository extends JpaRepository<PermissionGrant, Long> {

    Optional<PermissionGrant> findByUserIdAndScopeAndGrantValueAndIsActiveTrue(
            String userId,
            TaskApprovalScope scope,
            String grantValue
    );

    List<PermissionGrant> findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(String userId);
}
