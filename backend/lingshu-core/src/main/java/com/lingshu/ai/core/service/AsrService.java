package com.lingshu.ai.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AsrService {

    private static final Logger logger = LoggerFactory.getLogger(AsrService.class);
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AsrService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<String> recognize(String asrUrl, byte[] audioData, String mimeType) {
        if (asrUrl == null || asrUrl.isBlank()) {
            logger.error("ASR 服务地址未配置");
            return CompletableFuture.completedFuture("");
        }

        String normalizedUrl = normalizeAsrUrl(asrUrl);
        logger.debug("准备处理音频识别请求，目标地址: {}", normalizedUrl);

        byte[] wavData;
        if (mimeType != null && mimeType.contains("webm")) {
            wavData = audioData;
        } else if (mimeType != null && mimeType.contains("wav")) {
            wavData = audioData;
        } else {
            wavData = convertPcmToWav(audioData);
        }

        logger.debug("音频数据大小: {} bytes", wavData.length);

        String boundary = "----LingShuASRBoundary" + System.currentTimeMillis();
        String crlf = "\r\n";
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("--").append(boundary).append(crlf);
        headerBuilder.append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"").append(crlf);
        headerBuilder.append("Content-Type: audio/wav").append(crlf);
        headerBuilder.append(crlf);

        StringBuilder footerBuilder = new StringBuilder();
        footerBuilder.append(crlf).append("--").append(boundary).append("--").append(crlf);

        byte[] header = headerBuilder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footer = footerBuilder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ByteArrayOutputStream multipartStream = new ByteArrayOutputStream();
        try {
            multipartStream.write(header);
            multipartStream.write(wavData);
            multipartStream.write(footer);
        } catch (IOException e) {
            logger.error("构建 multipart 数据失败", e);
            return CompletableFuture.completedFuture("");
        }

        byte[] multipartData = multipartStream.toByteArray();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizedUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartData))
                .build();

        logger.info("发送 ASR 请求到: {}", normalizedUrl);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("HTTP 响应状态码: {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        try {
                            AsrResponse asrResponse = objectMapper.readValue(response.body(), AsrResponse.class);
                            String text = asrResponse.cleanText();
                            logger.info("识别成功: {}", text);
                            return text;
                        } catch (Exception e) {
                            logger.error("解析响应失败: {}", response.body(), e);
                            return "";
                        }
                    } else {
                        logger.error("请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                        return "";
                    }
                })
                .exceptionally(ex -> {
                    logger.error("请求异常", ex);
                    return "";
                });
    }

    public CompletableFuture<String> recognizeFromBase64(String asrUrl, String base64Audio, String mimeType) {
        try {
            byte[] audioData = Base64.getDecoder().decode(base64Audio);
            return recognize(asrUrl, audioData, mimeType);
        } catch (Exception e) {
            logger.error("Base64 解码失败", e);
            return CompletableFuture.completedFuture("");
        }
    }

    private String normalizeAsrUrl(String url) {
        String normalized = url;
        if (normalized.startsWith("ws://")) {
            normalized = "http://" + normalized.substring(5);
        } else if (normalized.startsWith("wss://")) {
            normalized = "https://" + normalized.substring(6);
        }
        if (!normalized.contains("/asr")) {
            if (normalized.endsWith("/")) {
                normalized = normalized + "asr";
            } else {
                normalized = normalized + "/asr";
            }
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        return normalized;
    }

    private byte[] convertPcmToWav(byte[] pcmData) {
        try {
            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                    SAMPLE_RATE,
                    SAMPLE_SIZE_IN_BITS,
                    CHANNELS,
                    SIGNED,
                    BIG_ENDIAN
            );

            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(pcmData);
            javax.sound.sampled.AudioInputStream pcmStream = new javax.sound.sampled.AudioInputStream(
                    bais, format, pcmData.length / format.getFrameSize());

            ByteArrayOutputStream wavStream = new ByteArrayOutputStream();
            javax.sound.sampled.AudioSystem.write(pcmStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavStream);
            pcmStream.close();

            return wavStream.toByteArray();
        } catch (IOException e) {
            logger.error("PCM 转 WAV 失败", e);
            return pcmData;
        }
    }

    private static class AsrResponse {
        public String text;

        String cleanText() {
            if (text == null) return "";
            return text.replaceAll("<\\|[^|]*\\|>", "").trim();
        }
    }
}
