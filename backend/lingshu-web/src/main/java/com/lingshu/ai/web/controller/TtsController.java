package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tts")
@CrossOrigin(origins = "*")
public class TtsController {

    private final SettingService settingService;
    private final WebClient webClient;

    public TtsController(SettingService settingService, WebClient webClient) {
        this.settingService = settingService;
        this.webClient = webClient;
    }

    @PostMapping(value = "/speak", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Flux<org.springframework.core.io.buffer.DataBuffer> speak(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isBlank()) {
            return Flux.empty();
        }

        SystemSetting setting = settingService.getSetting();
        Map<String, Object> ttsConfig = setting.getTtsConfig();
        
        String baseUrl = (String) ttsConfig.getOrDefault("baseUrl", "http://localhost:5050");
        String apiKey = (String) ttsConfig.getOrDefault("apiKey", "");
        String voice = (String) ttsConfig.getOrDefault("defaultVoice", "alloy");
        Double speed = (Double) ttsConfig.getOrDefault("defaultSpeed", 1.0);
        String format = (String) ttsConfig.getOrDefault("defaultFormat", "mp3");

        log.info("Requesting TTS for text: {} with voice: {}, baseUrl: {}", text, voice, baseUrl);

        return webClient.post()
                .uri(baseUrl + "/v1/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "input", text,
                        "voice", voice,
                        "speed", speed,
                        "response_format", format
                ))
                .retrieve()
                .bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                .doOnSubscribe(subscription -> log.info("TTS audio stream subscribed"))
                .doOnComplete(() -> log.info("TTS audio stream completed"))
                .doOnError(e -> log.error("TTS request failed: {}", e.getMessage(), e));
    }
}
