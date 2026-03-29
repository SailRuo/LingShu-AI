package com.lingshu.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 系统设置数据传输对象（保持向后兼容）
 * 用于接收前端发送的扁平化配置数据，并转换为 JSON 格式存储
 */
@Data
public class SystemSettingDTO {

    @JsonProperty("source")
    private String source;

    @JsonProperty("chatModel")
    private String chatModel;

    @JsonProperty("baseUrl")
    private String baseUrl;

    @JsonProperty("apiKey")
    private String apiKey;

    @JsonProperty("embedSource")
    private String embedSource;

    @JsonProperty("embedModel")
    private String embedModel;

    @JsonProperty("embedBaseUrl")
    private String embedBaseUrl;

    @JsonProperty("embedApiKey")
    private String embedApiKey;

    @JsonProperty("proactiveEnabled")
    private Boolean proactiveEnabled;

    @JsonProperty("inactiveThresholdMinutes")
    private Integer inactiveThresholdMinutes;

    @JsonProperty("greetingCooldownSeconds")
    private Integer greetingCooldownSeconds;

    @JsonProperty("inactiveCheckIntervalMs")
    private Long inactiveCheckIntervalMs;

    @JsonProperty("memoryModelSource")
    private String memoryModelSource;

    @JsonProperty("memoryModel")
    private String memoryModel;

    @JsonProperty("memoryModelBaseUrl")
    private String memoryModelBaseUrl;

    @JsonProperty("memoryModelApiKey")
    private String memoryModelApiKey;

    @JsonProperty("ttsBaseUrl")
    private String ttsBaseUrl;

    @JsonProperty("ttsApiKey")
    private String ttsApiKey;

    @JsonProperty("ttsDefaultVoice")
    private String ttsDefaultVoice;

    @JsonProperty("ttsDefaultSpeed")
    private Double ttsDefaultSpeed;

    @JsonProperty("ttsDefaultFormat")
    private String ttsDefaultFormat;

    @JsonProperty("ttsEnabled")
    private Boolean ttsEnabled;
}
