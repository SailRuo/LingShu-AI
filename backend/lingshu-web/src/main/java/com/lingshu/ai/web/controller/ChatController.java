package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.ProactiveService;
import com.lingshu.ai.infrastructure.entity.ChatMessage;
import com.lingshu.ai.infrastructure.entity.UserState;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final ProactiveService proactiveService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

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
        // 已禁用欢迎语接口调用
        return Flux.empty();
    }

    @GetMapping("/models")
    public java.util.List<String> getModels(
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
        
        List<ChatMessage> messages;
        if (beforeId != null) {
            PageRequest pageRequest = PageRequest.of(0, size, Sort.by("createdAt").descending());
            messages = chatMessageRepository.findBySessionIdAndIdLessThanOrderByCreatedAtDesc(
                    effectiveSessionId, beforeId, pageRequest);
        } else {
            PageRequest pageRequest = PageRequest.of(0, size, Sort.by("createdAt").descending());
            messages = chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                    effectiveSessionId, pageRequest).getContent();
        }
        
        return messages.stream()
                .map(m -> new ChatMessageResponse(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt() != null ? m.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());
    }

    public record ChatMessageResponse(Long id, String role, String content, String timestamp) {}

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
