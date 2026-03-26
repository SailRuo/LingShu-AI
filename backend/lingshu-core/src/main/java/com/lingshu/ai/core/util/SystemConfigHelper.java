package com.lingshu.ai.core.util;

import java.util.Map;

/**
 * 系统配置访问辅助类
 * 提供便捷方法访问 JSON 格式的系统配置
 */
public class SystemConfigHelper {
    
    /**
     * 从配置 Map 中获取字符串值
     */
    public static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * 从配置 Map 中获取整数值
     */
    public static Integer getInteger(Map<String, Object> config, String key, Integer defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 从配置 Map 中获取长整数值
     */
    public static Long getLong(Map<String, Object> config, String key, Long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    /**
     * 从配置 Map 中获取布尔值
     */
    public static Boolean getBoolean(Map<String, Object> config, String key, Boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
