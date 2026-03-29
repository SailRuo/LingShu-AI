package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.core.service.ToolResultSummarizer;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ToolResultSummarizerImpl implements ToolResultSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ToolResultSummarizerImpl.class);

    public static final String SYSTEM_PROMPT = """
        你是一个精准的数据提取引擎。你的任务是从工具返回的杂乱网页文本中，挖掘出与用户问题高度相关的“硬核信息”。
        
        ### 提取准则：
        1. **判定逻辑**：如果内容包含与问题相关的数值、日期、名称、结论或操作步骤，则 relevant=true。
        2. **信息分层**：
           - summary: 概括核心发现（不超过 %d 字）。
           - data_points: 提取具体的键值对（如：价格、地点、状态等）。
           - key_points: 罗列 3-5 条关键事实。
        3. **容错处理**：如果信息不全，请在 summary 中指出缺少什么，而不是直接返回 false。
        
        ### 约束：
        - 必须输出纯 JSON 格式。
        - 严禁胡编乱造。
        - 保持 JSON 结构的完整性。
        """;

    public static final String USER_PROMPT_TEMPLATE = """
        【用户问题】
        %s
        
        【原始数据来源：%s】
        ---
        %s
        ---
        
        请按以下 JSON 格式输出：
        {
          "relevant": true,
          "summary": "不超过%d字的摘要",
          "data_points": { "字段名": "具体数值/内容" },
          "key_points": ["核心点1", "核心点2"],
          "confidence": 0.95
        }
        """;

    private static final int CACHE_MAX_SIZE = 100;
    private static final int CACHE_EXPIRE_MINUTES = 30;

    private final Cache<String, SummarizeResult> summaryCache;
    private final StreamingChatModel streamingChatModel;
    private final SettingService settingService;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    public ToolResultSummarizerImpl(
            @Qualifier("streamingChatLanguageModel") StreamingChatModel streamingChatModel,
            SettingService settingService,
            SystemLogService systemLogService) {
        this.streamingChatModel = streamingChatModel;
        this.settingService = settingService;
        this.systemLogService = systemLogService;
        this.objectMapper = new ObjectMapper();
        this.summaryCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Override
    public SummarizeResult summarize(String toolName, String originalResult, String userIntent) {
        if (!shouldSummarize(toolName, originalResult)) {
            return SummarizeResult.notSummarized(originalResult);
        }

        String cacheKey = generateCacheKey(toolName, originalResult, userIntent);
        SummarizeResult cachedResult = summaryCache.getIfPresent(cacheKey);
        
        if (cachedResult != null) {
            log.info("工具 '{}' 命中缓存摘要 (原始: {} 字符)", toolName, originalResult.length());
            systemLogService.info(String.format("工具 %s 命中缓存摘要", toolName), "TOOL_SUMMARY");
            return cachedResult;
        }

        log.info("工具 '{}' 返回数据过长 ({} 字符)，启动结构化总结...", toolName, originalResult.length());
        systemLogService.info(String.format("工具 %s 返回数据过长，启动结构化总结 (原始: %d 字符)", 
                toolName, originalResult.length()), "TOOL_SUMMARY");

        try {
            SummarizeResult result = summarizeSync(toolName, originalResult, userIntent);
            
            summaryCache.put(cacheKey, result);
            
            log.info("工具 '{}' 数据总结完成: {} -> {} 字符, relevant={}, confidence={}", 
                    toolName, originalResult.length(), result.summarizedLength(), 
                    result.relevant(), result.confidence());
            systemLogService.success(String.format("工具 %s 数据总结完成 (%d -> %d 字符)", 
                    toolName, originalResult.length(), result.summarizedLength()), "TOOL_SUMMARY");
            
            return result;
        } catch (Exception e) {
            log.error("工具 '{}' 总结失败: {}", toolName, e.getMessage());
            systemLogService.error(String.format("工具 %s 总结失败: %s", toolName, e.getMessage()), "TOOL_SUMMARY");
            SummarizeResult fallbackResult = SummarizeResult.summarized(
                    truncateResult(originalResult), 
                    originalResult.length(),
                    true,
                    List.of(),
                    0.5
            );
            summaryCache.put(cacheKey, fallbackResult);
            return fallbackResult;
        }
    }

    @Override
    public boolean shouldSummarize(String toolName, String result) {
        if (result == null || result.isBlank()) {
            return false;
        }

        // 仅限浏览器相关工具进行总结，以保护其他工具（如 Snapshot）的原始数据结构
        if (toolName == null || !toolName.toLowerCase().contains("browser")) {
            return false;
        }

        int threshold = getThreshold();
        return result.length() > threshold;
    }

    private SummarizeResult summarizeSync(String toolName, String originalResult, String userIntent) {
        String systemPrompt = SYSTEM_PROMPT.formatted(MAX_SUMMARY_LENGTH);
        String userPrompt = USER_PROMPT_TEMPLATE.formatted(
                userIntent != null ? userIntent : "获取相关信息",
                toolName,
                originalResult,
                MAX_SUMMARY_LENGTH
        );

        log.info("========== 工具结果总结请求 ==========");
        log.info("工具名称: {}", toolName);
        log.info("原始内容长度: {} 字符", originalResult.length());
        log.info("用户意图: {}", userIntent);
        log.info("--- System Prompt ---\n{}", systemPrompt);
        log.info("--- User Prompt ---\n{}", userPrompt);

        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                )
                .build();

        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                future.complete(responseBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                log.error("工具结果摘要 流式响应错误: {}", error.getMessage());
                future.completeExceptionally(error);
            }
        });

        try {
            String jsonResponse = future.get(60, TimeUnit.SECONDS);
            
            log.info("---工具结果摘要 LLM 响应 ---\n{}", jsonResponse);
            log.info("========== 工具结果总结完成 ==========");
            
            return parseStructuredResponse(jsonResponse, originalResult.length());
        } catch (Exception e) {
            log.error("结构化输出失败: {}", e.getMessage());
            log.error("========== 工具结果总结失败 ==========");
            return fallbackSummarize(toolName, originalResult);
        }
    }

    @SuppressWarnings("unchecked")
    private SummarizeResult parseStructuredResponse(String jsonResponse, int originalLength) {
        try {
            String json = jsonResponse.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            }
            if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();
            
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            
            boolean relevant = Boolean.TRUE.equals(parsed.get("relevant"));
            String summary = (String) parsed.getOrDefault("summary", "");
            double confidence = ((Number) parsed.getOrDefault("confidence", 0.8)).doubleValue();
            
            List<String> keyPoints = List.of();
            Object keyPointsObj = parsed.get("key_points");
            if (keyPointsObj instanceof List<?> list) {
                keyPoints = list.stream()
                        .map(Object::toString)
                        .toList();
            }
            
            if (summary.length() > MAX_SUMMARY_LENGTH) {
                summary = summary.substring(0, MAX_SUMMARY_LENGTH) + "...";
            }
            
            return SummarizeResult.summarized(summary, originalLength, relevant, keyPoints, confidence);
        } catch (Exception e) {
            log.warn("解析结构化响应失败: {}, 原始响应: {}", e.getMessage(), jsonResponse);
            return SummarizeResult.summarized(
                    truncateResult(jsonResponse), 
                    originalLength, 
                    true, 
                    List.of(), 
                    0.5
            );
        }
    }

    private SummarizeResult fallbackSummarize(String toolName, String originalResult) {
        String truncated = truncateResult(originalResult);
        return SummarizeResult.summarized(
                truncated, 
                originalResult.length(), 
                true, 
                List.of(), 
                0.5
        );
    }

    private String generateCacheKey(String toolName, String originalResult, String userIntent) {
        try {
            String combined = toolName + ":" + originalResult + ":" + (userIntent != null ? userIntent : "");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return toolName + ":" + originalResult.hashCode();
        }
    }

    private int getThreshold() {
        try {
            SystemSetting setting = settingService.getSetting();
            Integer configuredThreshold = setting.getToolResultSummaryThreshold();
            return configuredThreshold != null ? configuredThreshold : DEFAULT_THRESHOLD;
        } catch (Exception e) {
            return DEFAULT_THRESHOLD;
        }
    }

    private String truncateResult(String result) {
        if (result.length() <= MAX_SUMMARY_LENGTH) {
            return result;
        }
        return result.substring(0, MAX_SUMMARY_LENGTH) + "\n...[内容过长已截断]";
    }
}
