package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.task.TaskApprovalDecisionRequest;
import com.lingshu.ai.core.dto.task.TaskRunView;
import com.lingshu.ai.core.dto.task.TaskStartRequest;

public interface TaskRuntimeService {

    TaskRunView start(TaskStartRequest request);

    TaskRunView get(Long taskRunId, String userId);

    java.util.List<TaskRunView> listBySession(Long chatSessionId, String userId);

    TaskRunView approve(Long taskRunId, String userId, TaskApprovalDecisionRequest request);

    TaskRunView pause(Long taskRunId, String userId);

    TaskRunView resume(Long taskRunId, String userId);

    TaskRunView stop(Long taskRunId, String userId);
}
