package com.lingshu.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sourceforge.jaad.SampleBuffer;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 语音流服务类。
 * 通过 WebSocket 接收 AAC ADTS 分片，在客户端实时解码为 PCM 后使用 Java Sound 播放。
 */
public class AudioStreamService {

    private static final Logger logger = LoggerFactory.getLogger(AudioStreamService.class);
    private static final String WS_URL = "ws://127.0.0.1:8000/ws";
    private static final String STREAM_FORMAT = "aac";
    private static final int PIPE_BUFFER_SIZE = 1024 * 1024;
    private static final int PCM_BUFFER_SIZE = 8192;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<StreamingPlaybackSession> currentSession = new AtomicReference<>();

    private CompletableFuture<WebSocket> webSocketFuture;
    private FileOutputStream debugFileOut;
    private Path debugAudioPath;

    public AudioStreamService() {
        this.httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null))
                .build();
        this.objectMapper = new ObjectMapper();
        this.webSocketFuture = connectWebSocket();
    }

    private CompletableFuture<WebSocket> connectWebSocket() {
        logger.info("建立长连接 WebSocket: {}", WS_URL);
        return httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), new WebSocketListener())
                .thenApply(ws -> {
                    logger.info("TTS 核心通道就绪");
                    return ws;
                })
                .exceptionally(ex -> {
                    logger.error("TTS 握手异常", ex);
                    return null;
                });
    }

    private synchronized void ensureWebSocket() {
        if (webSocketFuture == null || webSocketFuture.isCompletedExceptionally()
                || (webSocketFuture.isDone() && webSocketFuture.join() == null)) {
            webSocketFuture = connectWebSocket();
        }
    }

    public void speak(String text, String speaker) {
        stopPlayback();

        StreamingPlaybackSession session = new StreamingPlaybackSession();
        currentSession.set(session);

        try {
            if (debugFileOut != null) {
                debugFileOut.close();
            }
            debugAudioPath = Paths.get("debug_audio." + STREAM_FORMAT).toAbsolutePath();
            debugFileOut = new FileOutputStream(debugAudioPath.toFile());
            logger.info("调试文件已重置: {}", debugAudioPath);
        } catch (IOException e) {
            logger.warn("调试文件创建失败", e);
        }

        ensureWebSocket();

        String cookies = loadCookies();
        ObjectNode requestJson = objectMapper.createObjectNode();
        requestJson.put("text", text);
        requestJson.put("speaker", speaker);
        requestJson.put("speed", 0);
        requestJson.put("pitch", 0);
        requestJson.put("format", STREAM_FORMAT);
        requestJson.put("cookies", cookies);

        webSocketFuture.thenAccept(ws -> {
            if (ws != null) {
                logger.info("发送核心指令: {}", text);
                ws.sendText(requestJson.toString(), true);
            }
        });
    }

    private String loadCookies() {
        for (Path candidate : resolveCookieCandidates()) {
            try {
                if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    String cookies = Files.readString(candidate, StandardCharsets.UTF_8).trim();
                    logger.info("已加载 Cookie 文件: {}, length={}", candidate.toAbsolutePath(), cookies.length());
                    return cookies;
                }
            } catch (IOException e) {
                logger.warn("读取 Cookie 文件失败: {}", candidate.toAbsolutePath(), e);
            }
        }
        logger.warn("未找到 tts.cookie。已检查路径: {}", resolveCookieCandidates());
        return "";
    }

    private List<Path> resolveCookieCandidates() {
        return List.of(
                Paths.get("tts.cookie"),
                Paths.get(System.getProperty("user.dir", "."), "tts.cookie"),
                Paths.get("E:\\Project\\LingShu-AI\\fx-frontend\\tts.cookie")
        );
    }

    private void stopPlayback() {
        StreamingPlaybackSession session = currentSession.getAndSet(null);
        if (session != null) {
            session.stop();
        }
    }

    private void finishActiveSession() {
        StreamingPlaybackSession session = currentSession.get();
        if (session != null) {
            session.finish();
        }
        closeDebugFile();
    }

    private void closeDebugFile() {
        if (debugFileOut != null) {
            try {
                debugFileOut.close();
            } catch (IOException e) {
                logger.warn("关闭调试文件失败", e);
            } finally {
                debugFileOut = null;
            }
        }
    }

    private class WebSocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("WS 通道已开启");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                ObjectNode node = (ObjectNode) objectMapper.readTree(data.toString());
                String event = node.path("event").asText();
                if ("finish".equals(event)) {
                    logger.info("服务端合成完毕");
                    finishActiveSession();
                } else if ("error".equals(event)) {
                    logger.error("后端业务错误: {}", node.path("message").asText());
                    finishActiveSession();
                }
            } catch (Exception e) {
                logger.error("消息解析失败", e);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            //logger.info("收到音频分片: {} bytes", bytes.length);

            if (debugFileOut != null) {
                try {
                    debugFileOut.write(bytes);
                    debugFileOut.flush();
                } catch (IOException e) {
                    logger.warn("调试文件写入失败", e);
                }
            }

            StreamingPlaybackSession session = currentSession.get();
            if (session != null) {
                session.write(bytes);
            } else {
                logger.warn("收到音频分片时没有活动播放会话，已丢弃 {} bytes", bytes.length);
            }

            return WebSocket.Listener.super.onBinary(webSocket, ByteBuffer.wrap(bytes), last);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("WS 运行时错误", error);
            finishActiveSession();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.warn("WS 连接已断开: {} - {}", statusCode, reason);
            finishActiveSession();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }

    private final class StreamingPlaybackSession {
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final PipedInputStream aacInput;
        private final PipedOutputStream aacOutput;
        private Thread playbackThread;
        private SourceDataLine line;

        private StreamingPlaybackSession() {
            try {
                this.aacInput = new PipedInputStream(PIPE_BUFFER_SIZE);
                this.aacOutput = new PipedOutputStream(aacInput);
            } catch (IOException e) {
                throw new IllegalStateException("创建 AAC 管道失败", e);
            }
        }

        private void start() {
            if (started.compareAndSet(false, true)) {
                playbackThread = new Thread(this::playLoop, "aac-stream-player");
                playbackThread.setDaemon(true);
                playbackThread.start();
            }
        }

        private void write(byte[] bytes) {
            if (stopped.get()) {
                return;
            }
            try {
                aacOutput.write(bytes);
                aacOutput.flush();
                start();
            } catch (IOException e) {
                if (!stopped.get()) {
                    logger.error("写入 AAC 管道失败", e);
                    stop();
                }
            }
        }

        private void finish() {
            if (stopped.compareAndSet(false, true)) {
                try {
                    aacOutput.close();
                } catch (IOException e) {
                    logger.warn("关闭 AAC 输出流失败", e);
                }
            }
        }

        private void stop() {
            if (stopped.compareAndSet(false, true)) {
                closeQuietly(aacOutput);
                closeQuietly(aacInput);
            }
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
            }
            if (playbackThread != null) {
                playbackThread.interrupt();
            }
        }

        private void playLoop() {
            try (BufferedInputStream bufferedInput = new BufferedInputStream(aacInput)) {
                ADTSDemultiplexer demultiplexer = new ADTSDemultiplexer(bufferedInput);
                Decoder decoder = Decoder.create(demultiplexer.getDecoderInfo());
                SampleBuffer sampleBuffer = new SampleBuffer();

                AudioFormat pcmFormat = null;
                while (!stopped.get()) {
                    byte[] frame;
                    try {
                        frame = demultiplexer.readNextFrame();
                    } catch (IOException eof) {
                        if (stopped.get()) {
                            break;
                        }
                        throw eof;
                    }

                    if (frame == null || frame.length == 0) {
                        continue;
                    }

                    decoder.decodeFrame(frame, sampleBuffer);

                    if (pcmFormat == null) {
                        pcmFormat = new AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                sampleBuffer.getSampleRate(),
                                sampleBuffer.getBitsPerSample(),
                                sampleBuffer.getChannels(),
                                sampleBuffer.getChannels() * (sampleBuffer.getBitsPerSample() / 8),
                                sampleBuffer.getSampleRate(),
                                sampleBuffer.isBigEndian()
                        );
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
                        line = (SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                        line.open(pcmFormat);
                        line.start();
                        logger.info("PCM 播放已启动: {} Hz, {} channels", pcmFormat.getSampleRate(), pcmFormat.getChannels());
                    }

                    byte[] pcm = sampleBuffer.getData();
                    if (pcm != null && pcm.length > 0 && line != null) {
                        line.write(pcm, 0, pcm.length);
                    }
                }

                if (!stopped.get() && line != null) {
                    line.drain();
                }
                logger.info("流式播放结束");
            } catch (Exception e) {
                logger.error("AAC 解码或播放失败", e);
            } finally {
                stop();
            }
        }

        private void closeQuietly(AutoCloseable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception e) {
                logger.debug("关闭资源失败", e);
            }
        }
    }
}
