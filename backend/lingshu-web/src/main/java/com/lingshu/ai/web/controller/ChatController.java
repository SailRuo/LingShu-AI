package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // For development
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/send")
    public String chat(@RequestBody ChatRequest request) {
        return chatService.chat(request.message());
    }

    @GetMapping(value = "/welcome", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getWelcome() {
        return chatService.streamWelcome();
    }

    @GetMapping("/models")
    public java.util.List<String> getModels(
            @RequestParam(name = "source", defaultValue = "ollama") String source,
            @RequestParam(name = "baseUrl", defaultValue = "http://localhost:11434") String baseUrl,
            @RequestParam(name = "apiKey", required = false) String apiKey) {
        return chatService.getModels(source, baseUrl, apiKey);
    }

    public record ChatRequest(String message, String model, String apiKey, String baseUrl) {}

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-LS-BaseUrl", required = false) String baseUrl,
            @RequestHeader(value = "X-LS-ApiKey", required = false) String apiKey,
            @RequestHeader(value = "X-LS-Model", required = false) String model) {
        
        // Priority: Request Body > Headers
        String finalBaseUrl = request.baseUrl() != null ? request.baseUrl() : baseUrl;
        String finalApiKey = request.apiKey() != null ? request.apiKey() : apiKey;
        String finalModel = request.model() != null ? request.model() : model;

        return chatService.streamChat(request.message(), finalModel, finalApiKey, finalBaseUrl);
    }
}
