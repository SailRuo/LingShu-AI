package com.lingshu.ai.web.websocket;

import com.lingshu.ai.core.event.SessionTitleUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 会话标题更新事件监听器，通过 WebSocket 将更新推送到前端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionTitleUpdatedEventListener {

    private final ChatWebSocketHandler chatWebSocketHandler;

    @Async
    @EventListener
    public void handleSessionTitleUpdated(SessionTitleUpdatedEvent event) {
        log.info("收到会话标题更新事件，用户: {}, 会话ID: {}, 标题: {}", 
                event.getUserId(), event.getSessionId(), event.getTitle());
        
        chatWebSocketHandler.broadcastToUser(event.getUserId(), Map.of(
            "type", "sessionTitleUpdate",
            "sessionId", event.getSessionId(),
            "title", event.getTitle()
        ));
    }
}
