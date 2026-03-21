package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.SystemLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SystemLogService {
    private final Sinks.Many<SystemLog> logs = Sinks.many().multicast().directBestEffort();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public void log(String content, String type, String section) {
        SystemLog sl = SystemLog.builder()
                .time(LocalDateTime.now().format(formatter))
                .content(content)
                .type(type.toUpperCase())
                .section(section)
                .build();
        logs.tryEmitNext(sl);
    }

    public void info(String content, String section) { log(content, "info", section); }
    public void error(String content, String section) { log(content, "error", section); }
    public void debug(String content, String section) { log(content, "debug", section); }
    public void trace(String content, String section) { log(content, "trace", section); }

    public Flux<SystemLog> logStream() {
        return logs.asFlux();
    }
}
