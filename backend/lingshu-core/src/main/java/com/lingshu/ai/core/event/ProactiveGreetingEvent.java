package com.lingshu.ai.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProactiveGreetingEvent extends ApplicationEvent {
    private final String userId;
    private final String greeting;

    public ProactiveGreetingEvent(Object source, String userId, String greeting) {
        super(source);
        this.userId = userId;
        this.greeting = greeting;
    }
}
