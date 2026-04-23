package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.ChatSessionService;
import com.lingshu.ai.core.service.ProactiveService;
import com.lingshu.ai.core.service.TurnTimelineService;
import com.lingshu.ai.infrastructure.entity.UserState;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;
    private final ProactiveService proactiveService;
    private final TurnTimelineService turnTimelineService;

    public ChatController(ChatService chatService,
                          ChatSessionService chatSessionService,
                          ProactiveService proactiveService,
                          TurnTimelineService turnTimelineService) {
        this.chatService = chatService;
        this.chatSessionService = chatSessionService;
        this.proactiveService = proactiveService;
        this.turnTimelineService = turnTimelineService;
    }

    @GetMapping(value = "/welcome", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getWelcome(@RequestParam(name = "userId", defaultValue = "User") String userId) {
        return chatService.streamWelcome(userId);
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

    @GetMapping("/turns")
    public List<TurnTimelineService.TurnView> getTurnHistory(
            @RequestParam(name = "userId", defaultValue = "User") String userId,
            @RequestParam(name = "sessionId", required = false) Long sessionId,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        Long effectiveSessionId = resolveSessionId(userId, sessionId);
        if (effectiveSessionId == null) {
            return List.of();
        }

        return turnTimelineService.getTurnHistory(effectiveSessionId, beforeId, size);
    }

    @GetMapping("/sessions")
    public List<ChatSessionService.ChatSessionView> getSessions(
            @RequestParam(name = "userId", defaultValue = "User") String userId) {
        return chatSessionService.listSessions(userId);
    }

    public record CreateSessionRequest(String userId, String title) {
    }

    @PostMapping("/sessions")
    public ChatSessionService.ChatSessionView createSession(@RequestBody(required = false) CreateSessionRequest request) {
        String userId = request != null && request.userId() != null ? request.userId() : "User";
        String title = request != null ? request.title() : null;
        return chatSessionService.createSession(userId, title);
    }

    @DeleteMapping("/sessions/{id}")
    public void deleteSession(@PathVariable(name = "id") Long id) {
        chatSessionService.deleteSession(id);
    }

    @DeleteMapping("/turns")
    public void clearHistory(@RequestParam(name = "userId", defaultValue = "User") String userId,
                             @RequestParam(name = "sessionId", required = false) Long sessionId) {
        Long effectiveSessionId = resolveSessionId(userId, sessionId);
        chatService.clearHistory(effectiveSessionId);
    }

    public record ChatRequest(String message, List<String> images, Long sessionId, Long agentId, String model, String apiKey, String baseUrl, String userId) {
    }

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
        Long effectiveSessionId = resolveSessionId(userId, request.sessionId());

        return chatService.streamChat(request.message(), request.images(), effectiveSessionId, request.agentId(), userId, finalModel, finalApiKey, finalBaseUrl, false, null);
    }

    @PostMapping(value = "/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    public reactor.core.publisher.Mono<Map<String, String>> syncChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-LS-BaseUrl", required = false) String baseUrl,
            @RequestHeader(value = "X-LS-ApiKey", required = false) String apiKey,
            @RequestHeader(value = "X-LS-Model", required = false) String model) {

        String finalBaseUrl = request.baseUrl() != null ? request.baseUrl() : baseUrl;
        String finalApiKey = request.apiKey() != null ? request.apiKey() : apiKey;
        String finalModel = request.model() != null ? request.model() : model;
        String userId = request.userId() != null ? request.userId() : "User";
        Long effectiveSessionId = resolveSessionId(userId, request.sessionId());

        return chatService.streamChat(request.message(), request.images(), effectiveSessionId, request.agentId(), userId, finalModel, finalApiKey, finalBaseUrl, false, null)
                .reduce("", String::concat)
                .map(fullResponse -> {
                    String cleanResponse = fullResponse.replaceAll("\\u0001REASONING\\u0001.*?\\u0001/REASONING\\u0001", "");
                    return Map.of("reply", cleanResponse);
                });
    }

    private Long resolveSessionId(String userId, Long sessionId) {
        return chatSessionService.resolveSessionId(userId, sessionId);
    }

}
