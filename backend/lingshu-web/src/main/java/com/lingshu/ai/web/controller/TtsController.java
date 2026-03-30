package com.lingshu.ai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.SettingService;
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

    @PostMapping(value = "/speak", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> speak(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.ok().build();
        }

        SystemSetting setting = settingService.getSetting();
        Map<String, Object> ttsConfig = setting.getTtsConfig();
        
        String baseUrl = (String) ttsConfig.getOrDefault("baseUrl", "http://localhost:5050");
        String voice = (String) ttsConfig.getOrDefault("defaultVoice", "alloy");
        Double speed = (Double) ttsConfig.getOrDefault("defaultSpeed", 1.0);
        String format = (String) ttsConfig.getOrDefault("defaultFormat", "mp3");

        log.info("Requesting TTS for text: {} with voice: {}, baseUrl: {}", text, voice, baseUrl);

        StreamingResponseBody stream = outputStream -> {
            try {
                Map<String, Object> requestBody = Map.of(
                        "input", text,
                        "speed", speed,
                        "response_format", format
                );

                String jsonBody = objectMapper.writeValueAsString(requestBody);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/audio/speech"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200 && response.body() != null) {
                    outputStream.write(response.body());
                    outputStream.flush();
                    log.info("TTS audio stream completed, size: {} bytes", response.body().length);
                } else {
                    log.error("TTS request failed, status: {}, body: {}", response.statusCode(), 
                            response.body() != null ? new String(response.body()) : "null");
                }
            } catch (Exception e) {
                log.error("TTS request failed: {}", e.getMessage(), e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }
}
