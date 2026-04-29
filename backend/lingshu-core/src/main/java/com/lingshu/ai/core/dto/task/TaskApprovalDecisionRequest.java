package com.lingshu.ai.core.dto.task;

public record TaskApprovalDecisionRequest(
        Boolean grantWorkspace,
        Boolean grantCommandCategory
) {
}
