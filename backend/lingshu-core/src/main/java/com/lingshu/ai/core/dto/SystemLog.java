package com.lingshu.ai.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemLog {
    private String time;
    private String content;
    private String type;
    private String section;
}
