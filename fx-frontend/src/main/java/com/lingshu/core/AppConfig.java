package com.lingshu.core;

public record AppConfig(String ttsWsUrl, String asrWsUrl, String themeColor, String themeMode, boolean asrEnabled, boolean ttsEnabled) {

    public static final String DEFAULT_TTS_WS_URL = "ws://127.0.0.1:8000/ws";
    public static final String DEFAULT_ASR_WS_URL = "http://127.0.0.1:50001/asr";
    public static final String DEFAULT_THEME_COLOR = "#0078D7";
    public static final String DEFAULT_THEME_MODE = "DARK";
    public static final boolean DEFAULT_ASR_ENABLED = true;
    public static final boolean DEFAULT_TTS_ENABLED = true;

    public AppConfig {
        ttsWsUrl = normalize(ttsWsUrl, DEFAULT_TTS_WS_URL);
        asrWsUrl = normalize(asrWsUrl, DEFAULT_ASR_WS_URL);
        themeColor = normalize(themeColor, DEFAULT_THEME_COLOR);
        themeMode = normalize(themeMode, DEFAULT_THEME_MODE);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
