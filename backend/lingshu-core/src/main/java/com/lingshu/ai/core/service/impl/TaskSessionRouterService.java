package com.lingshu.ai.core.service.impl;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TaskSessionRouterService {

    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i).*[a-z]:\\\\[^\\s]*.*");
    private static final Pattern RELATIVE_PATH = Pattern.compile(".*(?:\\./|\\.\\./|[\\\\/](?:src|backend|frontend|lingshu-gui)[\\\\/]).*");
    private static final Pattern FILE_NAME = Pattern.compile(".*\\b[\\w.-]+\\.(?:java|kt|py|js|ts|tsx|jsx|vue|json|ya?ml|xml|md|sql|sh|bat|ps1)\\b.*");
    private static final Pattern COMMAND_INTENT = Pattern.compile("(?i).*(?:^|\\s)(git|npm|pnpm|yarn|python|pip|mvn|gradle|./gradlew|java|javac)(?:\\s|$).*");
    private static final Pattern PUNCTUATION_ONLY = Pattern.compile("^[\\p{Punct}\\s]+$");
    private static final Pattern SHORT_GREETING = Pattern.compile(
            "^(?i)(hi|hello|hey|yo|你好|您好|哈喽|在吗|在不在|早上好|中午好|下午好|晚上好|谢谢|多谢|好的|ok|okay)[!！?？~～\\s]*$"
    );

    private static final List<String> DEVELOPMENT_ACTIONS = List.of(
            "修复", "测试", "重构", "编写", "开发", "实现", "运行", "构建", "编译", "调试"
    );
    private static final List<String> DEVELOPMENT_TARGETS = List.of(
            "项目", "目录", "仓库", "代码", "编程", "脚本", "接口", "模块", "bug", "repo", "repository"
    );
    private static final List<String> ENGINEERING_SIGNALS = List.of(
            "报错", "错误", "失败", "异常", "日志", "堆栈", "test", "build", "compile", "debug"
    );

    public boolean isTaskRequest(String text) {
        return decide(text).taskRequest();
    }

    public TaskRouteDecision decide(String text) {
        if (text == null || text.isBlank()) {
            return new TaskRouteDecision(false, "blank message");
        }

        String normalized = text.trim();
        String lowerCased = normalized.toLowerCase(Locale.ROOT);

        if (isExplicitNonTask(normalized)) {
            return new TaskRouteDecision(false, "matched non-task greeting or acknowledgement");
        }

        if (WINDOWS_PATH.matcher(normalized).matches()) {
            return new TaskRouteDecision(true, "matched windows path");
        }
        if (RELATIVE_PATH.matcher(normalized).matches() || FILE_NAME.matcher(normalized).matches()) {
            return new TaskRouteDecision(true, "matched project path or file name");
        }
        if (COMMAND_INTENT.matcher(lowerCased).matches()) {
            return new TaskRouteDecision(true, "matched development command");
        }

        int score = 0;
        if (containsAny(normalized, DEVELOPMENT_ACTIONS)) {
            score += 1;
        }
        if (containsAny(lowerCased, DEVELOPMENT_TARGETS)) {
            score += 1;
        }
        if (containsAny(lowerCased, ENGINEERING_SIGNALS)) {
            score += 1;
        }
        if (score >= 2) {
            return new TaskRouteDecision(true, "matched development intent score >= 2");
        }

        return new TaskRouteDecision(false, "no task routing signal");
    }

    public record TaskRouteDecision(boolean taskRequest, String reason) {
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private boolean isExplicitNonTask(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.trim();
        if (normalized.length() <= 12 && SHORT_GREETING.matcher(normalized).matches()) {
            return true;
        }
        if (normalized.length() <= 8 && PUNCTUATION_ONLY.matcher(normalized).matches()) {
            return true;
        }
        return false;
    }
}
