package com.lingshu.ai.web.websocket;

import com.lingshu.ai.core.event.TaskEventAppendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventBroadcastListener {

    private final ChatWebSocketHandler chatWebSocketHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskEventAppended(TaskEventAppendedEvent event) {
        Map<String, Object> message = Map.of(
                "type", "taskEvent",
                "taskRunId", event.getTaskRunId(),
                "eventType", event.getEventType().name(),
                "payload", event.getPayload() == null ? Map.of() : event.getPayload()
        );
        log.info("广播任务事件: userId={}, taskRunId={}, eventType={}",
                event.getUserId(), event.getTaskRunId(), event.getEventType().name());
        chatWebSocketHandler.broadcastTaskEvent(event.getUserId(), message);
    }
}
