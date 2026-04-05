package com.lingshu.ai.infrastructure.dto;

import lombok.Data;

/**
 * 语音合成请求对象
 */
@Data
public class TtsSpeakRequest {
    private String text;
}
