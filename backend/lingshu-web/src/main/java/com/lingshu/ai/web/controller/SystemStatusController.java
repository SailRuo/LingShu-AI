package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final SettingService settingService;
    private final RestTemplate restTemplate;
    private final Driver neo4jDriver;

    public SystemStatusController(SettingService settingService,
                                  RestTemplate restTemplate,
                                  Driver neo4jDriver) {
        this.settingService = settingService;
        this.restTemplate = restTemplate;
        this.neo4jDriver = neo4jDriver;
    }

    @GetMapping("/status")
    @SuppressWarnings("unchecked")
    public SystemStatus getStatus() {
        SystemSetting setting = settingService.getSetting();
        String aiSource = setting.getSource();
        String chatStatus = "offline";
        String embedStatus = "offline";
        String neo4jStatus = "offline";
        String vram = "---";
        String latency = "---";

        long start = System.currentTimeMillis();

        // 1. Check Embedding Source / Model
        String embedSource = setting.getEmbedSource();
        String embedBaseUrl = setting.getEmbedBaseUrl();
        String embedModel = setting.getEmbedModel();

        if (embedSource == null || embedSource.isBlank()) embedSource = "ollama";

        if ("ollama".equalsIgnoreCase(embedSource)) {
            try {
                String ollamaEmbedBaseUrl = (embedBaseUrl != null && !embedBaseUrl.isBlank())
                        ? embedBaseUrl
                        : "http://localhost:11434";

                String tagsUrl = ollamaEmbedBaseUrl + "/api/tags";
                Map<String, Object> response = restTemplate.getForObject(tagsUrl, Map.class);
                if (response != null && response.containsKey("models")) {
                    java.util.List<Map<String, Object>> models = (java.util.List<Map<String, Object>>) response.get("models");

                    if (embedModel != null && !embedModel.isBlank()) {
                        boolean embeddingModelExists = models.stream().anyMatch(m -> embedModel.equals(m.get("name")));
                        embedStatus = embeddingModelExists ? "online" : "model_missing";
                    } else {
                        embedStatus = "model_missing";
                    }

                    if ("ollama".equalsIgnoreCase(aiSource)) {
                        boolean chatModelExists = models.stream().anyMatch(m -> m.get("name").equals(setting.getChatModel()));
                        chatStatus = chatModelExists ? "online" : "model_missing";

                        // Fetch VRAM from chat source ollama base
                        try {
                            String ollamaChatBaseUrl = setting.getBaseUrl();
                            if (ollamaChatBaseUrl == null || ollamaChatBaseUrl.isBlank()) {
                                ollamaChatBaseUrl = "http://localhost:11434";
                            }

                            String psUrl = ollamaChatBaseUrl + "/api/ps";
                            Map<String, Object> psResponse = restTemplate.getForObject(psUrl, Map.class);
                            if (psResponse != null && psResponse.containsKey("models")) {
                                java.util.List<Map<String, Object>> psModels = (java.util.List<Map<String, Object>>) psResponse.get("models");
                                long totalSize = psModels.stream().mapToLong(m -> ((Number) m.get("size")).longValue()).sum();
                                if (totalSize > 0) vram = String.format("%.1f GB", totalSize / (1024.0 * 1024.0 * 1024.0));
                                else vram = "Idle";
                            }
                        } catch (Exception e) {
                            vram = "Local";
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Embedding Ollama status check failed: {}", e.getMessage());
                embedStatus = "offline";
            }
        } else if ("openai".equalsIgnoreCase(embedSource)) {
            try {
                String base = embedBaseUrl;
                String embedApiKey = setting.getEmbedApiKey();
                if (base == null || base.isBlank()) {
                    embedStatus = "model_missing";
                } else {
                    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    String url = (base.contains("/v1") || base.endsWith("/v1")) ? base + "/models" : base + "/v1/models";
                    try {
                        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                        if (embedApiKey != null && !embedApiKey.isBlank()) {
                            headers.set("Authorization", "Bearer " + embedApiKey);
                        }
                        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                        restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
                        embedStatus = (embedModel != null && !embedModel.isBlank()) ? "online" : "model_missing";
                    } catch (Exception e) {
                        if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403") || e.getMessage().contains("400"))) {
                            embedStatus = (embedModel != null && !embedModel.isBlank()) ? "online" : "model_missing";
                        } else if (!base.contains("/v1")) {
                            String fallbackUrl = base + "/models";
                            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                            if (embedApiKey != null && !embedApiKey.isBlank()) {
                                headers.set("Authorization", "Bearer " + embedApiKey);
                            }
                            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                            restTemplate.exchange(fallbackUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
                            embedStatus = (embedModel != null && !embedModel.isBlank()) ? "online" : "model_missing";
                        } else {
                            throw e;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Embedding OpenAI status check failed: {}", e.getMessage());
                embedStatus = "offline";
            }
        }

        // 2. Check Chat Source (if OpenAI)
        if ("openai".equalsIgnoreCase(aiSource)) {
            vram = "Cloud";
            try {
                String base = setting.getBaseUrl();
                String apiKey = setting.getApiKey();
                if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                String url = (base.contains("/v1") || base.endsWith("/v1")) ? base + "/models" : base + "/v1/models";
                try {
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.set("Authorization", "Bearer " + apiKey);
                    }
                    org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                    restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
                    chatStatus = "online";
                } catch (Exception e) {
                    if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403") || e.getMessage().contains("400"))) {
                        chatStatus = "online";
                    } else if (!base.contains("/v1")) {
                        String fallbackUrl = base + "/models";
                        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                        if (apiKey != null && !apiKey.isBlank()) {
                            headers.set("Authorization", "Bearer " + apiKey);
                        }
                        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                        restTemplate.exchange(fallbackUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
                        chatStatus = "online";
                    } else {
                        throw e;
                    }
                }
            } catch (Exception e) {
                log.error("Chat OpenAI status check failed: {}", e.getMessage());
                chatStatus = "offline";
            }
        }

        latency = (System.currentTimeMillis() - start) + "ms";

        // 3. Check Neo4j
        try {
            neo4jDriver.verifyConnectivity();
            neo4jStatus = "online";
        } catch (Exception e) {
            log.warn("Neo4j status check failed: {}", e.getMessage());
        }

        return SystemStatus.builder()
                .aiSource(aiSource)
                .chatStatus(chatStatus)
                .embedStatus(embedStatus)
                .neo4j(neo4jStatus)
                .vram(vram)
                .latency(latency)
                .build();
    }

    @Data
    @Builder
    public static class SystemStatus {
        private String aiSource;
        private String chatStatus;
        private String embedStatus;
        private String neo4j;
        private String vram;
        private String latency;
    }
}
