package com.lingshu.ai.infrastructure.memory;

import com.lingshu.ai.infrastructure.entity.ChatTurn;
import com.lingshu.ai.infrastructure.entity.ChatTurnEvent;
import com.lingshu.ai.infrastructure.repository.ChatTurnEventRepository;
import com.lingshu.ai.infrastructure.repository.ChatTurnRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final List<String> CONTEXT_STATUSES = List.of("completed", "running");

    private final ChatTurnRepository turnRepository;
    private final ChatTurnEventRepository eventRepository;
    private final ConcurrentMap<Long, SystemMessage> sessionSystemMessages = new ConcurrentHashMap<>();
    private final ChatContextAssembler contextAssembler = new ChatContextAssembler();

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
                CONTEXT_STATUSES,
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

        List<ChatTurn> replayableTurns = turns.stream()
                .filter(turn -> shouldReplayTurn(turn, eventsByTurnId.getOrDefault(turn.getId(), List.of())))
                .toList();

        ChatContextAssembler.AssemblyResult assemblyResult = contextAssembler.assemble(replayableTurns, eventsByTurnId);
        messages.addAll(assemblyResult.messages());

        if (!assemblyResult.diagnostics().isEmpty()) {
            log.info("ChatContext diagnostics sessionId={} => {}", sessionId, assemblyResult.diagnostics());
        }

        if (!containsUserMessage(messages) && containsAnyUserContent(replayableTurns)) {
            log.warn("ChatContext assembled without USER message while turns contain user content, sessionId={}", sessionId);
        }

        log.debug("ChatContext message types sessionId={} => {}", sessionId, summarizeTypes(messages));
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

    private Long parseId(Object memoryId) {
        if (memoryId instanceof Long) return (Long) memoryId;
        if (memoryId instanceof String) return Long.parseLong((String) memoryId);
        return 1L;
    }

    private boolean containsUserMessage(List<ChatMessage> messages) {
        return messages.stream().anyMatch(m -> m.type() == ChatMessageType.USER);
    }

    private boolean shouldReplayTurn(ChatTurn turn, List<ChatTurnEvent> events) {
        if (!"running".equals(safe(turn.getStatus()))) {
            return true;
        }

        boolean hasEvents = events != null && !events.isEmpty();
        boolean hasAssistantMessage = !safe(turn.getAssistantMessage()).isBlank();
        if (!hasEvents && !hasAssistantMessage) {
            log.debug("Skip empty running turn from ChatMemory replay, turnId={}", turn.getId());
            return false;
        }
        return true;
    }

    private boolean containsAnyUserContent(List<ChatTurn> turns) {
        return turns.stream().anyMatch(turn ->
                (turn.getUserMessage() != null && !turn.getUserMessage().isBlank())
                        || (turn.getUserImagesJson() != null && !turn.getUserImagesJson().isBlank() && !"[]".equals(turn.getUserImagesJson().trim()))
        );
    }

    private String summarizeTypes(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> m.type().name())
                .collect(Collectors.joining(" -> "));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
