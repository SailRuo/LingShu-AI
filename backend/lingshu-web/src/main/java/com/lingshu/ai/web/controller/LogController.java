package com.lingshu.ai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.dto.SystemLog;
import com.lingshu.ai.core.service.SystemLogService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Collections;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final SystemLogService systemLogService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public LogController(SystemLogService systemLogService, 
                         StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper) {
        this.systemLogService = systemLogService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLogs() {
        return Flux.create(sink -> {
            try {
                SystemLog connectLog = SystemLog.builder()
                        .time(LocalDateTime.now().format(formatter))
                        .content("日志流连接已建立，正在加载历史日志...")
                        .type("INFO")
                        .section("SYSTEM")
                        .build();
                sink.next(createSse(connectLog));
                
                List<String> history = redisTemplate.opsForList().range(systemLogService.getLogListKey(), 0, -1);
                if (history != null && !history.isEmpty()) {
                    Collections.reverse(history);
                    int count = history.size();
                    for (String json : history) {
                        sink.next(ServerSentEvent.<String>builder().data(json).build());
                    }
                    SystemLog historyLog = SystemLog.builder()
                            .time(LocalDateTime.now().format(formatter))
                            .content("历史日志加载完成，共 " + count + " 条记录")
                            .type("SUCCESS")
                            .section("SYSTEM")
                            .build();
                    sink.next(createSse(historyLog));
                } else {
                    SystemLog emptyLog = SystemLog.builder()
                            .time(LocalDateTime.now().format(formatter))
                            .content("暂无历史日志，发送消息触发 LLM 调用链")
                            .type("INFO")
                            .section("SYSTEM")
                            .build();
                    sink.next(createSse(emptyLog));
                }
                
                var connection = redisTemplate.getConnectionFactory().getConnection();
                connection.subscribe(
                    (message, pattern) -> {
                        String json = new String(message.getBody());
                        sink.next(ServerSentEvent.<String>builder().data(json).build());
                    },
                    systemLogService.getLogChannel().getBytes()
                );
                
                sink.onCancel(() -> {
                    try {
                        connection.close();
                    } catch (Exception ignored) {}
                });
                
            } catch (Exception e) {
                SystemLog errorLog = SystemLog.builder()
                        .time(LocalDateTime.now().format(formatter))
                        .content("日志流错误: " + e.getMessage())
                        .type("ERROR")
                        .section("SYSTEM")
                        .build();
                sink.next(createSse(errorLog));
                sink.complete();
            }
        });
    }
    
    @DeleteMapping
    public ResponseEntity<Void> clearLogs() {
        redisTemplate.delete(systemLogService.getLogListKey());
        return ResponseEntity.ok().build();
    }
    
    private ServerSentEvent<String> createSse(SystemLog log) {
        try {
            String json = objectMapper.writeValueAsString(log);
            return ServerSentEvent.<String>builder().data(json).build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder().data("{\"error\":\"serialize failed\"}").build();
        }
    }
}
