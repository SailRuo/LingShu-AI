package com.lingshu.ai.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 会话标题更新事件
 */
@Getter
public class SessionTitleUpdatedEvent extends ApplicationEvent {
    private final String userId;
    private final Long sessionId;
    private final String title;

    public SessionTitleUpdatedEvent(Object source, String userId, Long sessionId, String title) {
        super(source);
        this.userId = userId;
        this.sessionId = sessionId;
        this.title = title;
    }
}
