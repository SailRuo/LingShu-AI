package com.lingshu.ai.core.event;

import com.lingshu.ai.infrastructure.task.TaskEventType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskEventAppendedEvent extends ApplicationEvent {

    private final String userId;
    private final Long taskRunId;
    private final TaskEventType eventType;
    private final Object payload;

    public TaskEventAppendedEvent(Object source,
                                  String userId,
                                  Long taskRunId,
                                  TaskEventType eventType,
                                  Object payload) {
        super(source);
        this.userId = userId;
        this.taskRunId = taskRunId;
        this.eventType = eventType;
        this.payload = payload;
    }
}
