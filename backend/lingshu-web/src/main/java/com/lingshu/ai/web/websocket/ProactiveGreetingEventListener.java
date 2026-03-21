package com.lingshu.ai.web.websocket;

import com.lingshu.ai.core.event.ProactiveGreetingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProactiveGreetingEventListener {

    private final ChatWebSocketHandler chatWebSocketHandler;

    @Async
    @EventListener
    public void handleProactiveGreeting(ProactiveGreetingEvent event) {
        log.info("收到主动问候事件，用户: {}, 问候: {}", event.getUserId(), 
                event.getGreeting().length() > 50 ? event.getGreeting().substring(0, 50) + "..." : event.getGreeting());
        
        chatWebSocketHandler.broadcastProactiveGreeting(event.getUserId(), event.getGreeting());
    }
}
