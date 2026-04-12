package com.lingshu.ai.infrastructure.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.infrastructure.entity.ChatTurn;
import com.lingshu.ai.infrastructure.entity.ChatTurnEvent;
import com.lingshu.ai.infrastructure.repository.ChatTurnEventRepository;
import com.lingshu.ai.infrastructure.repository.ChatTurnRepository;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * ChatMemoryStore migrated to turn/event persistence.
 * It now reads model context from chat_turns + chat_turn_events
 * and no longer persists to chat_messages.
 */
@Slf4j
@Component("databaseChatMemoryStore")
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private static final int MAX_CONTEXT_TURNS = 8;

    private final ChatTurnRepository turnRepository;
    private final ChatTurnEventRepository eventRepository;
    private final ConcurrentMap<Long, SystemMessage> sessionSystemMessages = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseChatMemoryStore(ChatTurnRepository turnRepository,
                                   ChatTurnEventRepository eventRepository) {
        this.turnRepository = turnRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        Long sessionId = parseId(memoryId);
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        SystemMessage systemMessage = sessionSystemMessages.get(sessionId);
        if (systemMessage != null) {
            messages.add(systemMessage);
        }

        PageRequest pageable = PageRequest.of(0, MAX_CONTEXT_TURNS, Sort.by("id").descending());
        List<ChatTurn> turnsDesc = turnRepository.findBySessionIdAndStatusInOrderByIdDesc(
                sessionId,
                List.of("running", "completed", "failed"),
                pageable
        );
        if (turnsDesc == null || turnsDesc.isEmpty()) {
            return messages;
        }

        List<ChatTurn> turns = new ArrayList<>(turnsDesc);
        Collections.reverse(turns);

        List<Long> turnIds = turns.stream().map(ChatTurn::getId).toList();
        List<ChatTurnEvent> events = eventRepository.findByTurnIdInOrderByTurnIdAscSequenceNoAsc(turnIds);
        Map<Long, List<ChatTurnEvent>> eventsByTurnId = events.stream()
                .collect(Collectors.groupingBy(e -> e.getTurn().getId(), LinkedHashMap::new, Collectors.toList()));

        for (ChatTurn turn : turns) {
            UserMessage userMessage = toUserMessage(turn);
            List<ChatTurnEvent> turnEvents = eventsByTurnId.getOrDefault(turn.getId(), List.of());

            // Keep model context aligned to complete user-driven turns.
            // Assistant-only records such as welcome messages are still stored for UI history,
            // but should not become the leading conversational message sent back to the LLM.
            if (userMessage == null) {
                if (!turnEvents.isEmpty() || !safe(turn.getAssistantMessage()).isBlank()) {
                    log.debug("Skip assistant-only turn from chat memory context, turnId={}", turn.getId());
                }
                continue;
            }

            messages.add(userMessage);
            boolean hasAssistantTextEvent = false;

            for (ChatTurnEvent event : turnEvents) {
                String eventType = safe(event.getEventType());
                if ("tool_start".equals(eventType)) {
                    ToolExecutionRequest req = ToolExecutionRequest.builder()
                            .id(safe(event.getToolCallId()))
                            .name(safe(event.getToolName()))
                            .arguments(safe(event.getArguments()))
                            .build();
                    messages.add(AiMessage.from(List.of(req)));
                    continue;
                }
                if ("tool_end".equals(eventType)) {
                    messages.add(ToolExecutionResultMessage.from(
                            safe(event.getToolCallId()),
                            safe(event.getToolName()),
                            safe(event.getContent())
                    ));
                    continue;
                }
                if ("assistant_text".equals(eventType)) {
                    String text = safe(event.getContent());
                    if (!text.isBlank()) {
                        messages.add(AiMessage.from(text));
                        hasAssistantTextEvent = true;
                    }
                }
            }

            // Compatibility fallback for turns created before assistant_text event migration.
            if (!hasAssistantTextEvent) {
                String assistant = safe(turn.getAssistantMessage());
                if (!assistant.isBlank()) {
                    messages.add(AiMessage.from(assistant));
                }
            }
        }

        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        Long sessionId = parseId(memoryId);
        Optional<SystemMessage> systemMessage = messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .reduce((first, second) -> second);

        if (systemMessage.isPresent()) {
            sessionSystemMessages.put(sessionId, systemMessage.get());
        } else {
            sessionSystemMessages.remove(sessionId);
        }

        // Persistence migrated to chat_turns/chat_turn_events by TurnTimelineService.
        // Keep this method side-effect free for non-system messages to avoid duplicate writes.
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Long sessionId = parseId(memoryId);
        sessionSystemMessages.remove(sessionId);
    }

    private UserMessage toUserMessage(ChatTurn turn) {
        String text = safe(turn.getUserMessage());
        List<String> images = parseImages(turn.getUserImagesJson());

        if ((text == null || text.isBlank()) && images.isEmpty()) {
            return null;
        }

        List<Content> contents = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            contents.add(TextContent.from(text));
        }
        for (String image : images) {
            addImageContent(contents, image);
        }

        if (contents.isEmpty()) {
            return null;
        }
        return UserMessage.from(contents);
    }

    private void addImageContent(List<Content> contents, String image) {
        if (image == null || image.isBlank()) {
            return;
        }
        String trimmed = image.trim();
        try {
            if (trimmed.startsWith("data:") && trimmed.contains(",")) {
                int sep = trimmed.indexOf(',');
                String header = trimmed.substring(5, sep);
                String mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : "image/jpeg";
                String base64 = trimmed.substring(sep + 1);
                contents.add(ImageContent.from(base64, mime));
                return;
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                contents.add(ImageContent.from(trimmed));
                return;
            }
            // Treat as raw base64.
            contents.add(ImageContent.from(trimmed, "image/jpeg"));
        } catch (Exception e) {
            log.debug("Skip invalid image payload in turn context: {}", e.getMessage());
        }
    }

    private List<String> parseImages(String userImagesJson) {
        if (userImagesJson == null || userImagesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(userImagesJson, new TypeReference<List<String>>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Long parseId(Object memoryId) {
        if (memoryId instanceof Long) return (Long) memoryId;
        if (memoryId instanceof String) return Long.parseLong((String) memoryId);
        return 1L;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
