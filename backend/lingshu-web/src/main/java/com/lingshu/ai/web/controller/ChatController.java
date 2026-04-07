package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.ProactiveService;
import com.lingshu.ai.core.service.TurnTimelineService;
import com.lingshu.ai.infrastructure.entity.UserState;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ProactiveService proactiveService;
    private final ChatSessionRepository chatSessionRepository;
    private final TurnTimelineService turnTimelineService;

    public ChatController(ChatService chatService,
                          ProactiveService proactiveService,
                          ChatSessionRepository chatSessionRepository,
                          TurnTimelineService turnTimelineService) {
        this.chatService = chatService;
        this.proactiveService = proactiveService;
        this.chatSessionRepository = chatSessionRepository;
        this.turnTimelineService = turnTimelineService;
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

    @DeleteMapping("/turns")
    public void clearHistory(@RequestParam(name = "userId", defaultValue = "User") String userId,
                             @RequestParam(name = "sessionId", required = false) Long sessionId) {
        Long effectiveSessionId = resolveSessionId(userId, sessionId);
        chatService.clearHistory(effectiveSessionId);
    }

    public record ChatRequest(String message, Long agentId, String model, String apiKey, String baseUrl, String userId) {
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

        return chatService.streamChat(request.message(), request.agentId(), userId, finalModel, finalApiKey, finalBaseUrl);
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

        return chatService.streamChat(request.message(), request.agentId(), userId, finalModel, finalApiKey, finalBaseUrl)
                .reduce("", String::concat)
                .map(fullResponse -> {
                    String cleanResponse = fullResponse.replaceAll("\\u0001REASONING\\u0001.*?\\u0001/REASONING\\u0001", "");
                    return Map.of("reply", cleanResponse);
                });
    }

    private Long resolveSessionId(String userId, Long sessionId) {
        if (sessionId != null) {
            return sessionId;
        }
        String normalizedUserId = userId == null || userId.isBlank() ? "User" : userId.trim();
        Long byUser = chatSessionRepository.findFirstByUserIdOrderByIdAsc(normalizedUserId)
                .map(com.lingshu.ai.infrastructure.entity.ChatSession::getId)
                .orElse(null);
        if (byUser != null) {
            return byUser;
        }
        if ("User".equals(normalizedUserId)) {
            Long legacyWs = chatSessionRepository.findFirstByUserIdOrderByIdAsc("web-ws:User")
                    .map(com.lingshu.ai.infrastructure.entity.ChatSession::getId)
                    .orElse(null);
            if (legacyWs != null) {
                return legacyWs;
            }
            Long legacyHttp = chatSessionRepository.findFirstByUserIdOrderByIdAsc("web-http:User")
                    .map(com.lingshu.ai.infrastructure.entity.ChatSession::getId)
                    .orElse(null);
            if (legacyHttp != null) {
                return legacyHttp;
            }
        }
        return chatSessionRepository.findAll()
                .stream()
                .findFirst()
                .map(com.lingshu.ai.infrastructure.entity.ChatSession::getId)
                .orElse(null);
    }

}
