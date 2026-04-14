package com.lingshu.ai.web.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数请求: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception e) throws Exception {
        if (e instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            throw e;
        }
        log.error("系统内部错误: ", e);
        return ResponseEntity.internalServerError().body("系统内部错误: " + e.getMessage());
    }
}
