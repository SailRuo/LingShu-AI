package com.lingshu.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AsrService {

    private static final Logger logger = LoggerFactory.getLogger(AsrService.class);
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final int BUFFER_SIZE = 4096;
    private static final int SILENCE_DURATION_MS = 1500;
    private static final int MIN_SPEECH_DURATION_MS = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private TargetDataLine targetDataLine;
    private ByteArrayOutputStream audioBuffer;
    private Consumer<String> onRecognitionResult;
    private Thread listeningThread;

    public AsrService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.appConfigService = AppConfigService.getInstance();
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    public boolean isListening() {
        return isListening.get();
    }

    public void setOnRecognitionResult(Consumer<String> callback) {
        this.onRecognitionResult = callback;
    }

    public void startListening() {
        if (isListening.compareAndSet(false, true)) {
            try {
                AudioFormat format = new AudioFormat(
                        SAMPLE_RATE,
                        SAMPLE_SIZE_IN_BITS,
                        CHANNELS,
                        SIGNED,
                        BIG_ENDIAN
                );

                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    logger.error("不支持 16kHz 单声道录音格式");
                    isListening.set(false);
                    return;
                }

                targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
                targetDataLine.open(format);
                targetDataLine.start();

                AppConfig config = appConfigService.load();
                int vadThreshold = config.vadThreshold();

                System.out.println("[ASR] 开始持续监听模式 (VAD阈值: " + vadThreshold + ")");
                logger.info("开始持续监听模式 (VAD)");

                listeningThread = new Thread(() -> {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    ByteArrayOutputStream speechBuffer = new ByteArrayOutputStream();
                    boolean inSpeech = false;
                    long speechStartTime = 0;
                    long lastSpeechTime = 0;
                    int frameCount = 0;

                    while (isListening.get()) {
                        int count = targetDataLine.read(buffer, 0, buffer.length);
                        if (count > 0) {
                            double rms = calculateRms(buffer, count);
                            frameCount++;
                            
                            if (frameCount % 50 == 0) {
                                System.out.println("[ASR] RMS: " + String.format("%.1f", rms) + " (threshold: " + vadThreshold + ")");
                            }

                            if (rms > vadThreshold) {
                                if (!inSpeech) {
                                    inSpeech = true;
                                    speechStartTime = System.currentTimeMillis();
                                    System.out.println("[ASR] 检测到语音开始, RMS: " + String.format("%.1f", rms));
                                }
                                speechBuffer.write(buffer, 0, count);
                                lastSpeechTime = System.currentTimeMillis();
                            } else if (inSpeech) {
                                speechBuffer.write(buffer, 0, count);

                                long silenceDuration = System.currentTimeMillis() - lastSpeechTime;
                                long speechDuration = lastSpeechTime - speechStartTime;

                                if (silenceDuration > SILENCE_DURATION_MS && speechDuration > MIN_SPEECH_DURATION_MS) {
                                    System.out.println("[ASR] 检测到语音结束，语音时长: " + speechDuration + "ms");
                                    
                                    byte[] speechData = speechBuffer.toByteArray();
                                    speechBuffer.reset();
                                    inSpeech = false;

                                    if (speechData.length > 0) {
                                        System.out.println("[ASR] 开始识别，音频大小: " + speechData.length + " bytes");
                                        recognize(speechData).thenAccept(text -> {
                                            System.out.println("[ASR] 识别结果: " + text);
                                            if (onRecognitionResult != null && !text.isBlank()) {
                                                onRecognitionResult.accept(text);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }
                }, "asr-listening-thread");
                listeningThread.setDaemon(true);
                listeningThread.start();

            } catch (LineUnavailableException e) {
                logger.error("录音设备初始化失败", e);
                isListening.set(false);
            }
        }
    }

    public void stopListening() {
        if (isListening.compareAndSet(true, false)) {
            if (targetDataLine != null) {
                targetDataLine.stop();
                targetDataLine.close();
            }
            if (listeningThread != null) {
                listeningThread.interrupt();
            }
            logger.info("停止持续监听模式");
        }
    }

    private double calculateRms(byte[] buffer, int length) {
        long sum = 0;
        int samples = length / 2;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += sample * sample;
        }
        return Math.sqrt((double) sum / samples);
    }

    public void startRecording() {
        if (isRecording.compareAndSet(false, true)) {
            try {
                AudioFormat format = new AudioFormat(
                        SAMPLE_RATE,
                        SAMPLE_SIZE_IN_BITS,
                        CHANNELS,
                        SIGNED,
                        BIG_ENDIAN
                );

                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    logger.error("不支持 16kHz 单声道录音格式");
                    isRecording.set(false);
                    return;
                }

                targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
                targetDataLine.open(format);
                targetDataLine.start();

                audioBuffer = new ByteArrayOutputStream();

                Thread recordingThread = new Thread(() -> {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (isRecording.get()) {
                        int count = targetDataLine.read(buffer, 0, buffer.length);
                        if (count > 0) {
                            audioBuffer.write(buffer, 0, count);
                        }
                    }
                }, "asr-recording-thread");
                recordingThread.setDaemon(true);
                recordingThread.start();

                logger.info("开始录音 (16kHz 单声道 PCM)");
            } catch (LineUnavailableException e) {
                logger.error("录音设备初始化失败", e);
                isRecording.set(false);
            }
        }
    }

    public CompletableFuture<String> stopRecordingAndRecognize() {
        if (isRecording.compareAndSet(true, false)) {
            if (targetDataLine != null) {
                targetDataLine.stop();
                targetDataLine.close();
            }

            byte[] audioData = audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];
            logger.info("录音结束，音频数据大小: {} bytes", audioData.length);

            if (audioData.length == 0) {
                logger.warn("没有录制到音频数据");
                return CompletableFuture.completedFuture("");
            }

            return recognize(audioData);
        }
        return CompletableFuture.completedFuture("");
    }

    public void cancelRecording() {
        if (isRecording.compareAndSet(true, false)) {
            if (targetDataLine != null) {
                targetDataLine.stop();
                targetDataLine.close();
            }
            audioBuffer = null;
            logger.info("录音已取消");
        }
    }

    public CompletableFuture<String> recognize(byte[] pcmData) {
        String asrUrl = getAsrUrl();
        if (asrUrl == null || asrUrl.isBlank()) {
            System.out.println("[ASR] 错误: ASR 服务地址未配置");
            logger.error("ASR 服务地址未配置");
            return CompletableFuture.completedFuture("");
        }

        System.out.println("[ASR] 发送识别请求到: " + asrUrl);
        byte[] wavData = convertPcmToWav(pcmData);
        System.out.println("[ASR] WAV 数据大小: " + wavData.length + " bytes");

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
                .uri(URI.create(asrUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartData))
                .build();

        logger.info("发送 ASR 请求到: {}", asrUrl);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    System.out.println("[ASR] HTTP 响应状态码: " + response.statusCode());
                    if (response.statusCode() == 200) {
                        try {
                            AsrResponse asrResponse = objectMapper.readValue(response.body(), AsrResponse.class);
                            System.out.println("[ASR] 识别成功: " + asrResponse.text());
                            logger.info("ASR 识别结果: {}", asrResponse.text());
                            return asrResponse.text();
                        } catch (Exception e) {
                            System.out.println("[ASR] 解析响应失败: " + response.body());
                            logger.error("解析 ASR 响应失败: {}", response.body(), e);
                            return "";
                        }
                    } else {
                        System.out.println("[ASR] 请求失败，状态码: " + response.statusCode() + ", 响应: " + response.body());
                        logger.error("ASR 请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                        return "";
                    }
                })
                .exceptionally(ex -> {
                    System.out.println("[ASR] 请求异常: " + ex.getMessage());
                    ex.printStackTrace();
                    logger.error("ASR 请求异常", ex);
                    return "";
                });
    }

    private byte[] convertPcmToWav(byte[] pcmData) {
        try {
            AudioFormat format = new AudioFormat(
                    SAMPLE_RATE,
                    SAMPLE_SIZE_IN_BITS,
                    CHANNELS,
                    SIGNED,
                    BIG_ENDIAN
            );

            ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
            AudioInputStream pcmStream = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize());

            ByteArrayOutputStream wavStream = new ByteArrayOutputStream();
            AudioSystem.write(pcmStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavStream);
            pcmStream.close();

            return wavStream.toByteArray();
        } catch (IOException e) {
            logger.error("PCM 转 WAV 失败", e);
            return pcmData;
        }
    }

    private String getAsrUrl() {
        AppConfig config = appConfigService.load();
        String url = config.asrWsUrl();
        if (url.startsWith("ws://")) {
            url = "http://" + url.substring(5);
        } else if (url.startsWith("wss://")) {
            url = "https://" + url.substring(6);
        }
        if (!url.contains("/asr")) {
            if (url.endsWith("/")) {
                url = url + "asr";
            } else {
                url = url + "/asr";
            }
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return url;
    }

    private record AsrResponse(String text) {}
}
