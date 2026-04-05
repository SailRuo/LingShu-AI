package com.lingshu.ai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.dto.TtsSpeakRequest;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private final SettingService settingService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TtsController(SettingService settingService) {
        this.settingService = settingService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @RequestMapping(value = "/speak", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> speak(
            @RequestBody(required = false) TtsSpeakRequest postRequest,
            @RequestParam(required = false) String text) {

        String finalText = text;
        if (postRequest != null && postRequest.getText() != null) {
            finalText = postRequest.getText();
        }

        if (finalText == null || finalText.isBlank()) {
            return ResponseEntity.ok().build();
        }

        SystemSetting setting = settingService.getSetting();
        Map<String, Object> ttsConfig = setting.getTtsConfig();

        String baseUrl = (String) ttsConfig.getOrDefault("baseUrl", "http://localhost:5050");
        String apiKey = (String) ttsConfig.getOrDefault("apiKey", "");
        String voice = (String) ttsConfig.getOrDefault("defaultVoice", "alloy");
        Double speed = (Double) ttsConfig.getOrDefault("defaultSpeed", 1.0);
        String format = (String) ttsConfig.getOrDefault("defaultFormat", "mp3");

        log.info("=== [TTS] Preparing text-to-speech request ===");
        log.info("[TTS] Target text length: {}", finalText.length());
        log.info("[TTS] Voice: {}, Speed: {}, Format: {}", voice, speed, format);

        final String requestText = finalText;

        StreamingResponseBody stream = outputStream -> {
            try {
                Map<String, Object> requestBody = Map.of(
                        "model", "tts-1",
                        "input", requestText,
                        "voice", voice,
                        "speed", speed,
                        "response_format", format
                );

                String jsonBody = objectMapper.writeValueAsString(requestBody);
                String targetUrl = baseUrl + "/v1/audio/speech";

                log.info("[TTS] Target URL: {}", targetUrl);
                log.info("[TTS] Request Payload: {}", jsonBody);

                HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

                if (apiKey != null && !apiKey.isBlank()) {
                    httpRequestBuilder.header("Authorization", "Bearer " + apiKey);
                    log.info("[TTS] Authorization header included. Token length: {}", apiKey.length());
                } else {
                    log.info("[TTS] No Authorization token provided.");
                }

                log.info("[TTS] Sending HTTP POST request...");
                HttpResponse<java.io.InputStream> response = httpClient.send(httpRequestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

                log.info("[TTS] Received response. HTTP Status: {}", response.statusCode());

                if (response.statusCode() == 200 && response.body() != null) {
                    try (java.io.InputStream is = response.body()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalSize = 0;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                            totalSize += bytesRead;
                        }
                        log.info("[TTS] Audio stream write completed. Total size: {} bytes", totalSize);
                    }
                } else {
                    String errBody = "null";
                    if (response.body() != null) {
                        try (java.io.InputStream is = response.body()) {
                            errBody = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                    log.error("[TTS] HTTP request failed. Status: {}, Body: {}", response.statusCode(), errBody);
                }
            } catch (Exception e) {
                log.error("[TTS] Exception occurred during TTS request: {}", e.getMessage(), e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    @GetMapping(value = "/voices", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getVoices(
            @RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey
    ) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null) {
            SystemSetting setting = settingService.getSetting();
            Map<String, Object> ttsConfig = setting.getTtsConfig();

            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = (String) ttsConfig.getOrDefault("baseUrl", "http://localhost:5050");
            }
            if (apiKey == null) {
                apiKey = (String) ttsConfig.getOrDefault("apiKey", "");
            }
        }

        try {
            HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/voices"))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            if (apiKey != null && !apiKey.isBlank()) {
                httpRequestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(httpRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return ResponseEntity.ok(response.body());
            } else {
                log.error("Failed to fetch TTS voices, status: {}, body: {}", response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).body(response.body());
            }
        } catch (Exception e) {
            log.error("Failed to fetch TTS voices: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
