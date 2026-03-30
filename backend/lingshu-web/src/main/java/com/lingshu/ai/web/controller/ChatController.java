package com.lingshu.ai.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.ProactiveService;
import com.lingshu.ai.infrastructure.entity.ChatMessage;
import com.lingshu.ai.infrastructure.entity.UserState;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ProactiveService proactiveService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(ChatService chatService,
                          ProactiveService proactiveService,
                          ChatMessageRepository chatMessageRepository,
                          ChatSessionRepository chatSessionRepository) {
        this.chatService = chatService;
        this.proactiveService = proactiveService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    @PostMapping("/send")
    public String chat(@RequestBody ChatRequest request) {
        String userId = request.userId() != null ? request.userId() : "User";
        return chatService.chat(request.message(), request.agentId(), userId);
    }

    @GetMapping(value = "/welcome", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getWelcome(@RequestParam(name = "userId", defaultValue = "User") String userId) {
        return Flux.empty();
    }

    @GetMapping("/models")
    public List<String> getModels(
            @RequestParam(name = "source", defaultValue = "ollama") String source,
            @RequestParam(name = "baseUrl", defaultValue = "http://localhost:11434") String baseUrl,
            @RequestParam(name = "apiKey", required = false) String apiKey) {
        return chatService.getModels(source, baseUrl, apiKey);
    }

    @GetMapping(value = "/proactive/greeting", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getProactiveGreeting(@RequestParam(name = "userId", defaultValue = "User") String userId) {
        return proactiveService.generateGreeting(userId);
    }

    @GetMapping(value = "/proactive/comfort", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getProactiveComfort(@RequestParam(name = "userId", defaultValue = "User") String userId) {
        return proactiveService.generateComfortMessage(userId);
    }

    @GetMapping("/proactive/attention")
    public List<UserState> getUsersNeedingAttention() {
        return proactiveService.getUsersNeedingAttention();
    }

    @PostMapping("/proactive/mark")
    public void markUserForGreeting(@RequestParam(name = "userId") String userId) {
        proactiveService.markUserForGreeting(userId);
    }

    @PostMapping("/proactive/trigger")
    public void triggerProactiveGreeting(@RequestParam(name = "userId", defaultValue = "User") String userId) {
        proactiveService.markUserForGreeting(userId);
    }

    @GetMapping(value = "/proactive/test-greeting", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testProactiveGreeting(@RequestParam(name = "userId", defaultValue = "User") String userId) {
        return proactiveService.generateGreeting(userId);
    }

    @GetMapping("/history")
    public List<ChatMessageResponse> getChatHistory(
            @RequestParam(name = "sessionId", required = false) Long sessionId,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        Long effectiveSessionId = sessionId;
        if (effectiveSessionId == null) {
            effectiveSessionId = chatSessionRepository.findAll()
                    .stream()
                    .findFirst()
                    .map(com.lingshu.ai.infrastructure.entity.ChatSession::getId)
                    .orElse(null);
        }

        if (effectiveSessionId == null) {
            return List.of();
        }

        int rawFetchSize = Math.max(size * 4, 80);
        List<ChatMessage> rawMessages;
        if (beforeId != null) {
            PageRequest pageRequest = PageRequest.of(0, rawFetchSize, Sort.by("id").descending());
            rawMessages = chatMessageRepository.findBySessionIdAndIdLessThanOrderByIdDesc(
                    effectiveSessionId, beforeId, pageRequest);
        } else {
            PageRequest pageRequest = PageRequest.of(0, rawFetchSize, Sort.by("id").descending());
            rawMessages = chatMessageRepository.findBySessionIdOrderByIdDesc(
                    effectiveSessionId, pageRequest).getContent();
        }

        if (rawMessages.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> chronological = new ArrayList<>(rawMessages);
        Collections.reverse(chronological);

        List<ChatMessageResponse> aggregated = aggregateMessages(chronological);

        if (aggregated.size() > size) {
            aggregated = aggregated.subList(Math.max(0, aggregated.size() - size), aggregated.size());
        }

        Collections.reverse(aggregated);
        return aggregated;
    }

    @DeleteMapping("/history")
    public void clearHistory(@RequestParam(name = "sessionId", required = false) Long sessionId) {
        chatService.clearHistory(sessionId);
    }

    private List<ChatMessageResponse> aggregateMessages(List<ChatMessage> messages) {
        List<ChatMessageResponse> result = new ArrayList<>();

        int i = 0;
        while (i < messages.size()) {
            ChatMessage current = messages.get(i);
            String role = safe(current.getRole());

            if ("system".equalsIgnoreCase(role)) {
                i++;
                continue;
            }

            if ("user".equalsIgnoreCase(role)) {
                result.add(new ChatMessageResponse(
                        current.getId(),
                        "user",
                        safe(current.getContent()),
                        toTimestamp(current),
                        null
                ));
                i++;
                continue;
            }

            if ("assistant".equalsIgnoreCase(role)) {
                AggregatedAssistant aggregatedAssistant = aggregateAssistantChain(messages, i);
                result.add(new ChatMessageResponse(
                        aggregatedAssistant.id(),
                        "assistant",
                        aggregatedAssistant.content(),
                        aggregatedAssistant.timestamp(),
                        aggregatedAssistant.toolSteps()
                ));
                i = aggregatedAssistant.nextIndex();
                continue;
            }

            if ("tool".equalsIgnoreCase(role)) {
                List<ToolStepResponse> orphanSteps = new ArrayList<>();
                while (i < messages.size() && "tool".equalsIgnoreCase(safe(messages.get(i).getRole()))) {
                    ChatMessage toolMsg = messages.get(i);
                    orphanSteps.add(new ToolStepResponse(
                            safe(toolMsg.getToolCallId()),
                            safe(toolMsg.getToolName()),
                            null,
                            safe(toolMsg.getContent())
                    ));
                    i++;
                }

                result.add(new ChatMessageResponse(
                        current.getId(),
                        "assistant",
                        "",
                        toTimestamp(current),
                        orphanSteps.isEmpty() ? null : orphanSteps
                ));
                continue;
            }

            i++;
        }

        return result;
    }

    private AggregatedAssistant aggregateAssistantChain(List<ChatMessage> messages, int startIndex) {
        ChatMessage firstAssistant = messages.get(startIndex);
        long id = firstAssistant.getId();
        String timestamp = toTimestamp(firstAssistant);

        StringBuilder contentBuilder = new StringBuilder();
        List<ToolStepResponse> toolSteps = new ArrayList<>();

        int i = startIndex;
        boolean consumedAny = false;

        while (i < messages.size()) {
            ChatMessage msg = messages.get(i);
            String role = safe(msg.getRole());

            if (!"assistant".equalsIgnoreCase(role) && !"tool".equalsIgnoreCase(role)) {
                break;
            }

            if ("assistant".equalsIgnoreCase(role)) {
                appendContent(contentBuilder, safe(msg.getContent()));
                toolSteps.addAll(buildToolRequests(msg.getToolCalls()));
                consumedAny = true;
                i++;
                continue;
            }

            if ("tool".equalsIgnoreCase(role)) {
                attachToolResult(toolSteps, msg);
                consumedAny = true;
                i++;
            }
        }

        if (!consumedAny) {
            i = startIndex + 1;
        }

        List<ToolStepResponse> normalizedSteps = toolSteps.isEmpty() ? null : toolSteps;
        return new AggregatedAssistant(
                id,
                contentBuilder.toString().trim(),
                timestamp,
                normalizedSteps,
                i
        );
    }

    private List<ToolStepResponse> buildToolRequests(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isBlank()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> toolCalls = objectMapper.readValue(
                    toolCallsJson,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            return toolCalls.stream()
                    .map(call -> new ToolStepResponse(
                            stringValue(call.get("id")),
                            stringValue(call.get("name")),
                            normalizeArguments(stringValue(call.get("arguments"))),
                            null
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of(new ToolStepResponse(
                    "",
                    "unknown",
                    toolCallsJson,
                    null
            ));
        }
    }

    private void attachToolResult(List<ToolStepResponse> toolSteps, ChatMessage toolMsg) {
        String toolCallId = safe(toolMsg.getToolCallId());
        String toolName = safe(toolMsg.getToolName());
        String toolResult = safe(toolMsg.getContent());

        for (int j = toolSteps.size() - 1; j >= 0; j--) {
            ToolStepResponse step = toolSteps.get(j);
            if (toolCallId.equals(step.id())) {
                toolSteps.set(j, new ToolStepResponse(
                        step.id(),
                        step.name(),
                        step.arguments(),
                        toolResult
                ));
                return;
            }
        }

        toolSteps.add(new ToolStepResponse(
                toolCallId,
                toolName,
                null,
                toolResult
        ));
    }

    private String normalizeArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return arguments;
        }
        return arguments.trim();
    }

    private void appendContent(StringBuilder builder, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(content.trim());
    }

    private String toTimestamp(ChatMessage msg) {
        return msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record AggregatedAssistant(
            Long id,
            String content,
            String timestamp,
            List<ToolStepResponse> toolSteps,
            int nextIndex
    ) {}

    public record ToolStepResponse(
            String id,
            String name,
            String arguments,
            String result
    ) {}

    public record ChatMessageResponse(
            Long id,
            String role,
            String content,
            String timestamp,
            List<ToolStepResponse> toolSteps
    ) {}

    public record ChatRequest(String message, Long agentId, String model, String apiKey, String baseUrl, String userId) {}

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-LS-BaseUrl", required = false) String baseUrl,
            @RequestHeader(value = "X-LS-ApiKey", required = false) String apiKey,
            @RequestHeader(value = "X-LS-Model", required = false) String model) {

        String finalBaseUrl = request.baseUrl() != null ? request.baseUrl() : baseUrl;
        String finalApiKey = request.apiKey() != null ? request.apiKey() : apiKey;
        String finalModel = request.model() != null ? request.model() : model;
        String userId = request.userId() != null ? request.userId() : "User";

        return chatService.streamChat(request.message(), request.agentId(), userId, finalModel, finalApiKey, finalBaseUrl);
    }
}
