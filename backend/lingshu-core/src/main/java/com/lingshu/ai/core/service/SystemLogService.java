package com.lingshu.ai.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.dto.SystemLog;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemLogService {
    private static final String LOG_CHANNEL = "lingshu:logs";
    private static final String LOG_LIST_KEY = "lingshu:logs:history";
    private static final int MAX_HISTORY = 200;
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final Map<String, Long> timers = new ConcurrentHashMap<>();

    public SystemLogService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void log(String content, String type, String section) {
        SystemLog sl = SystemLog.builder()
                .time(LocalDateTime.now().format(formatter))
                .content(content)
                .type(type.toUpperCase())
                .section(section)
                .build();
        
        try {
            String json = objectMapper.writeValueAsString(sl);
            redisTemplate.opsForList().leftPush(LOG_LIST_KEY, json);
            redisTemplate.opsForList().trim(LOG_LIST_KEY, 0, MAX_HISTORY - 1);
            redisTemplate.convertAndSend(LOG_CHANNEL, json);
        } catch (Exception e) {
            System.err.println("Failed to publish log: " + e.getMessage());
        }
    }

    public void info(String content, String section) { log(content, "info", section); }
    public void error(String content, String section) { log(content, "error", section); }
    public void debug(String content, String section) { log(content, "debug", section); }
    public void trace(String content, String section) { log(content, "trace", section); }
    public void warn(String content, String section) { log(content, "warn", section); }
    public void success(String content, String section) { log(content, "success", section); }

    public void startTimer(String key) {
        timers.put(key, System.currentTimeMillis());
    }

    public long endTimer(String key, String description, String section) {
        Long start = timers.remove(key);
        if (start == null) {
            debug("Timer not found: " + key, section);
            return -1;
        }
        long duration = System.currentTimeMillis() - start;
        info(String.format("%s - 耗时: %dms", description, duration), section);
        return duration;
    }

    public void llmStart(String model, String endpoint, String section) {
        info(String.format("LLM调用开始 | 模型: %s | 端点: %s", model, endpoint), section);
        startTimer("llm_" + section);
    }

    public void llmEnd(int tokenCount, String section) {
        long duration = endTimer("llm_" + section, "LLM调用完成", section);
        if (tokenCount > 0 && duration > 0) {
            info(String.format("Token统计: ~%d tokens | 速度: %.1f tokens/s", 
                tokenCount, tokenCount * 1000.0 / duration), section);
        }
    }

    public void llmError(String error, String section) {
        timers.remove("llm_" + section);
        error("LLM调用失败: " + error, section);
    }

    public void embeddingStart(int textLength, String section) {
        info(String.format("Embedding向量化开始 | 文本长度: %d字符", textLength), section);
        startTimer("embedding_" + section);
    }

    public void embeddingEnd(String section) {
        endTimer("embedding_" + section, "Embedding向量化完成", section);
    }

    public void dbStart(String operation, String target, String section) {
        debug(String.format("数据库操作: %s | 目标: %s", operation, target), section);
        startTimer("db_" + operation);
    }

    public void dbEnd(String operation, String section) {
        endTimer("db_" + operation, "数据库操作完成", section);
    }

    public String getLogChannel() {
        return LOG_CHANNEL;
    }
    
    public String getLogListKey() {
        return LOG_LIST_KEY;
    }
}
