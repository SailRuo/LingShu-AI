package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.task.TaskEventView;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.task.TaskEventType;

import java.util.List;

public interface TaskEventStreamService {

    void appendEvent(TaskRun run, TaskEventType eventType, Object payload);

    List<TaskEventView> getEvents(Long taskRunId);
}
