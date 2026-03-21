package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.dto.SystemLog;
import com.lingshu.ai.core.service.SystemLogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final SystemLogService systemLogService;

    public LogController(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SystemLog> streamLogs() {
        return systemLogService.logStream();
    }
}
