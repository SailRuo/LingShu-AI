package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.TurnTimelineService;
import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.entity.ChatTurn;
import com.lingshu.ai.infrastructure.entity.ChatTurnArtifact;
import com.lingshu.ai.infrastructure.entity.ChatTurnEvent;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import com.lingshu.ai.infrastructure.repository.ChatTurnArtifactRepository;
import com.lingshu.ai.infrastructure.repository.ChatTurnEventRepository;
import com.lingshu.ai.infrastructure.repository.ChatTurnRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class TurnTimelineServiceImpl implements TurnTimelineService {

    private final ChatTurnRepository turnRepository;
    private final ChatTurnEventRepository eventRepository;
    private final ChatTurnArtifactRepository artifactRepository;
    private final ChatSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final Map<Long, AtomicInteger> turnSequences = new ConcurrentHashMap<>();

    public TurnTimelineServiceImpl(ChatTurnRepository turnRepository,
                                   ChatTurnEventRepository eventRepository,
                                   ChatTurnArtifactRepository artifactRepository,
                                   ChatSessionRepository sessionRepository,
                                   ObjectMapper objectMapper) {
        this.turnRepository = turnRepository;
        this.eventRepository = eventRepository;
        this.artifactRepository = artifactRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long startTurn(Long sessionId, String userMessage, List<String> userImages) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        String userImagesJson = toJson(userImages == null ? List.of() : userImages);
        ChatTurn turn = ChatTurn.builder()
                .session(session)
                .userMessage(userMessage != null ? userMessage : "")
                .userImagesJson(userImagesJson)
                .status("running")
                .createdAt(LocalDateTime.now())
                .build();
        turn = turnRepository.save(turn);
        turnSequences.put(turn.getId(), new AtomicInteger(0));
        return turn.getId();
    }

    @Override
    public void recordToolStart(Long turnId, String toolCallId, String toolName, String arguments) {
        ChatTurn turn = findTurn(turnId);
        saveEvent(turn, "tool_start", toolCallId, toolName, arguments, null, false);
    }

    @Override
    public void recordToolEnd(Long turnId, String toolCallId, String toolName, String arguments, String result, boolean isError,
                              List<ArtifactPayload> artifacts) {
        ChatTurn turn = findTurn(turnId);
        ChatTurnEvent event = saveEvent(turn, "tool_end", toolCallId, toolName, arguments, result, isError);
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        List<ChatTurnArtifact> rows = new ArrayList<>();
        for (ArtifactPayload artifact : artifacts) {
            rows.add(ChatTurnArtifact.builder()
                    .event(event)
                    .toolCallId(toolCallId)
                    .artifactType(artifact.artifactType())
                    .mimeType(artifact.mimeType())
                    .url(artifact.url())
                    .base64Data(artifact.base64Data())
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        artifactRepository.saveAll(rows);
    }

    @Override
    public void recordAssistantText(Long turnId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ChatTurn turn = findTurn(turnId);
        saveEvent(turn, "assistant_text", null, null, null, content, false);
    }

    @Override
    public void completeTurn(Long turnId, String assistantMessage) {
        ChatTurn turn = findTurn(turnId);
        turn.setAssistantMessage(assistantMessage != null ? assistantMessage : "");
        turn.setStatus("completed");
        turn.setCompletedAt(LocalDateTime.now());
        turnRepository.save(turn);
        turnSequences.remove(turnId);
    }

    @Override
    public void failTurn(Long turnId, String errorMessage) {
        ChatTurn turn = findTurn(turnId);
        turn.setStatus("failed");
        turn.setErrorMessage(errorMessage != null ? errorMessage : "");
        turn.setCompletedAt(LocalDateTime.now());
        turnRepository.save(turn);
        turnSequences.remove(turnId);
    }

    @Override
    public List<TurnView> getTurnHistory(Long sessionId, Long beforeId, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<ChatTurn> rows;
        PageRequest pageRequest = PageRequest.of(0, safeSize, Sort.by("id").descending());
        if (beforeId != null) {
            rows = turnRepository.findBySessionIdAndIdLessThanOrderByIdDesc(sessionId, beforeId, pageRequest);
        } else {
            rows = turnRepository.findBySessionIdOrderByIdDesc(sessionId, pageRequest);
        }
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> turnIds = rows.stream().map(ChatTurn::getId).toList();
        List<ChatTurnEvent> events = eventRepository.findByTurnIdInOrderByTurnIdAscSequenceNoAsc(turnIds);
        Map<Long, List<ChatTurnEvent>> eventsByTurnId = events.stream()
                .collect(Collectors.groupingBy(e -> e.getTurn().getId(), LinkedHashMap::new, Collectors.toList()));

        List<Long> eventIds = events.stream().map(ChatTurnEvent::getId).toList();
        Map<Long, List<ChatTurnArtifact>> artifactsByEventId = eventIds.isEmpty()
                ? Map.of()
                : artifactRepository.findByEventIdInOrderByIdAsc(eventIds).stream()
                .collect(Collectors.groupingBy(a -> a.getEvent().getId(), LinkedHashMap::new, Collectors.toList()));

        List<TurnView> result = new ArrayList<>();
        for (ChatTurn row : rows) {
            List<String> userImages = parseImages(row.getUserImagesJson());
            List<ChatTurnEvent> turnEvents = eventsByTurnId.getOrDefault(row.getId(), List.of());
            List<ToolStepView> toolSteps = buildToolSteps(turnEvents, artifactsByEventId);
            List<SegmentView> segments = buildSegments(turnEvents, artifactsByEventId);
            result.add(new TurnView(
                    row.getId(),
                    row.getCreatedAt() != null ? row.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : System.currentTimeMillis(),
                    row.getStatus(),
                    safe(row.getUserMessage()),
                    userImages,
                    safe(row.getAssistantMessage()),
                    safe(row.getErrorMessage()),
                    toolSteps,
                    segments
            ));
        }
        return result;
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public void clearTurnHistory(Long sessionId) {
        artifactRepository.deleteBySessionId(sessionId);
        eventRepository.deleteBySessionId(sessionId);
        turnRepository.deleteBySessionId(sessionId);
    }

    private ChatTurn findTurn(Long turnId) {
        return turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("Turn not found: " + turnId));
    }

    private ChatTurnEvent saveEvent(ChatTurn turn,
                                    String eventType,
                                    String toolCallId,
                                    String toolName,
                                    String arguments,
                                    String content,
                                    boolean isError) {
        int seq = turnSequences.computeIfAbsent(turn.getId(), k -> new AtomicInteger(0)).incrementAndGet();
        ChatTurnEvent event = ChatTurnEvent.builder()
                .turn(turn)
                .sequenceNo(seq)
                .eventType(eventType)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .arguments(arguments)
                .content(content)
                .isError(isError)
                .createdAt(LocalDateTime.now())
                .build();
        return eventRepository.save(event);
    }

    private List<ToolStepView> buildToolSteps(List<ChatTurnEvent> events,
                                              Map<Long, List<ChatTurnArtifact>> artifactsByEventId) {
        Map<String, ToolStepAccumulator> byCallId = new LinkedHashMap<>();
        int fallbackIndex = 0;
        for (ChatTurnEvent event : events) {
            if (!"tool_start".equals(event.getEventType()) && !"tool_end".equals(event.getEventType())) {
                continue;
            }
            String key = (event.getToolCallId() != null && !event.getToolCallId().isBlank())
                    ? event.getToolCallId()
                    : "fallback-" + (fallbackIndex++);
            ToolStepAccumulator acc = byCallId.computeIfAbsent(key, unused -> new ToolStepAccumulator());
            if (event.getToolCallId() != null) {
                acc.toolCallId = event.getToolCallId();
            }
            if (event.getToolName() != null) {
                acc.toolName = event.getToolName();
            }
            if (event.getArguments() != null) {
                acc.arguments = event.getArguments();
            }
            if ("tool_end".equals(event.getEventType())) {
                acc.result = safe(event.getContent());
                acc.isError = Boolean.TRUE.equals(event.getIsError());
                List<ArtifactPayload> artifacts = artifactsByEventId.getOrDefault(event.getId(), List.of()).stream()
                        .map(a -> new ArtifactPayload(
                                safe(a.getArtifactType()),
                                safe(a.getMimeType()),
                                safe(a.getUrl()),
                                safe(a.getBase64Data())
                        ))
                        .toList();
                acc.artifacts = artifacts;
            }
        }

        return byCallId.values().stream()
                .map(acc -> new ToolStepView(
                        safe(acc.toolCallId),
                        safe(acc.toolName),
                        safe(acc.arguments),
                        safe(acc.result),
                        acc.isError,
                        acc.artifacts == null ? List.of() : acc.artifacts
                ))
                .toList();
    }

    private List<SegmentView> buildSegments(List<ChatTurnEvent> events,
                                            Map<Long, List<ChatTurnArtifact>> artifactsByEventId) {
        List<SegmentView> segments = new ArrayList<>();
        for (ChatTurnEvent event : events) {
            if ("assistant_text".equals(event.getEventType())) {
                segments.add(new SegmentView(
                        "text",
                        "",
                        "",
                        "",
                        "",
                        false,
                        safe(event.getContent()),
                        List.of()
                ));
                continue;
            }

            if ("tool_end".equals(event.getEventType())) {
                List<ArtifactPayload> artifacts = artifactsByEventId.getOrDefault(event.getId(), List.of()).stream()
                        .map(a -> new ArtifactPayload(
                                safe(a.getArtifactType()),
                                safe(a.getMimeType()),
                                safe(a.getUrl()),
                                safe(a.getBase64Data())
                        ))
                        .toList();
                segments.add(new SegmentView(
                        "tool",
                        safe(event.getToolCallId()),
                        safe(event.getToolName()),
                        safe(event.getArguments()),
                        safe(event.getContent()),
                        Boolean.TRUE.equals(event.getIsError()),
                        "",
                        artifacts
                ));
            }
        }
        return segments;
    }

    private List<String> parseImages(String userImagesJson) {
        if (userImagesJson == null || userImagesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(userImagesJson, new TypeReference<List<String>>() {});
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ToolStepAccumulator {
        String toolCallId;
        String toolName;
        String arguments;
        String result;
        boolean isError;
        List<ArtifactPayload> artifacts;
    }
}
