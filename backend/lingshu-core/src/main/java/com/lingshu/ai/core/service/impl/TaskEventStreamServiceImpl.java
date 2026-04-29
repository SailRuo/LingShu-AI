package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.dto.task.TaskEventView;
import com.lingshu.ai.core.event.TaskEventAppendedEvent;
import com.lingshu.ai.core.service.TaskEventStreamService;
import com.lingshu.ai.infrastructure.entity.TaskEvent;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskEventRepository;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

@Service
public class TaskEventStreamServiceImpl implements TaskEventStreamService {

    private final TaskEventRepository taskEventRepository;
    private final TaskRunRepository taskRunRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TaskEventStreamServiceImpl(TaskEventRepository taskEventRepository,
                                      TaskRunRepository taskRunRepository,
                                      ObjectMapper objectMapper,
                                      ApplicationEventPublisher eventPublisher) {
        this.taskEventRepository = taskEventRepository;
        this.taskRunRepository = taskRunRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public TaskEventStreamServiceImpl(TaskEventRepository taskEventRepository,
                                      TaskRunRepository taskRunRepository,
                                      ObjectMapper objectMapper) {
        this(taskEventRepository, taskRunRepository, objectMapper, event -> {
        });
    }

    @Override
    @Transactional("transactionManager")
    public void appendEvent(TaskRun run, TaskEventType eventType, Object payload) {
        TaskRun lockedRun = taskRunRepository.findByIdForUpdate(run.getId())
                .orElseThrow(() -> new IllegalArgumentException("Task run not found: " + run.getId()));
        String payloadJson = serializePayload(payload);
        int nextSequenceNo = taskEventRepository.findTopByTaskRunIdOrderBySequenceNoDesc(run.getId())
                .map(event -> event.getSequenceNo() + 1)
                .orElse(1);
        TaskEvent event = TaskEvent.builder()
                .taskRun(lockedRun)
                .sequenceNo(nextSequenceNo)
                .eventType(eventType)
                .payloadJson(payloadJson)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        taskEventRepository.save(event);
        eventPublisher.publishEvent(new TaskEventAppendedEvent(
                this,
                lockedRun.getUserId(),
                lockedRun.getId(),
                eventType,
                payload == null ? java.util.Map.of() : payload
        ));
    }

    @Override
    public List<TaskEventView> getEvents(Long taskRunId) {
        return taskEventRepository.findByTaskRunIdOrderBySequenceNoAsc(taskRunId).stream()
                .map(this::toView)
                .toList();
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? java.util.Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize task event payload", e);
        }
    }

    private TaskEventView toView(TaskEvent event) {
        long timestamp = event.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return new TaskEventView(
                event.getId(),
                event.getSequenceNo(),
                event.getEventType().name(),
                event.getPayloadJson(),
                timestamp
        );
    }
}
