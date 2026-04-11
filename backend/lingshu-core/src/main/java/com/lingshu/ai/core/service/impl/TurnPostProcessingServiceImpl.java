package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.service.AffinityService;
import com.lingshu.ai.core.service.EmotionAnalyzer;
import com.lingshu.ai.core.service.EmotionalEpisodeService;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.core.service.SystemLogService;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TurnPostProcessingServiceImpl {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TurnPostProcessingServiceImpl.class);

    private final EmotionAnalyzer emotionAnalyzer;
    private final MemoryService memoryService;
    private final AffinityService affinityService;
    private final SystemLogService systemLogService;
    private final DynamicMemoryModel dynamicMemoryModel;
    private final EmotionalEpisodeService emotionalEpisodeService;

    public TurnPostProcessingServiceImpl(EmotionAnalyzer emotionAnalyzer,
                                         MemoryService memoryService,
                                         AffinityService affinityService,
                                         SystemLogService systemLogService,
                                         DynamicMemoryModel dynamicMemoryModel,
                                         EmotionalEpisodeService emotionalEpisodeService) {
        this.emotionAnalyzer = emotionAnalyzer;
        this.memoryService = memoryService;
        this.affinityService = affinityService;
        this.systemLogService = systemLogService;
        this.dynamicMemoryModel = dynamicMemoryModel;
        this.emotionalEpisodeService = emotionalEpisodeService;
    }

    @Async("taskExecutor")
    public void processCompletedTurn(String userId, String userMessage, String assistantResponse,
                                     EmotionAnalysis preAnalyzedEmotion) {
        if (userId == null || userId.isBlank() || userMessage == null || userMessage.isBlank()) {
            return;
        }

        try {
            TurnPostProcessorDecision decision = buildDecisionClassifier().classify(
                    userMessage,
                    assistantResponse != null ? assistantResponse : ""
            );

            if (decision == null) {
                systemLogService.debug("回合后处理跳过: LLM 未返回有效决策", "POST_PROCESS");
                affinityService.recordInteraction(userId);
                return;
            }

            systemLogService.debug(String.format(
                    "回合后处理决策: emotion=%s, facts=%s, interaction=%s, confidence=%.2f, reason=%s",
                    decision.isAnalyzeEmotion(),
                    decision.isExtractFacts(),
                    decision.isRecordInteraction(),
                    decision.getConfidence(),
                    safeReason(decision.getReason())
            ), "POST_PROCESS");

            systemLogService.info(String.format(
                    "后处理决策: 情感分析=%s, 事实提取=%s, 记录互动=%s (置信度: %.2f) | 理由: %s",
                    decision.isAnalyzeEmotion() ? "是" : "否",
                    decision.isExtractFacts() ? "是" : "否",
                    decision.isRecordInteraction() ? "是" : "否",
                    decision.getConfidence(),
                    safeReason(decision.getReason())
            ), "POST_PROCESS");

            EmotionAnalysis emotionResult = preAnalyzedEmotion;
            if (decision.isAnalyzeEmotion()) {
                if (emotionResult == null) {
                    emotionResult = analyzeEmotion(userId, userMessage);
                } else {
                    applyEmotionResult(userId, userMessage, emotionResult);
                }
            }

            if (decision.isExtractFacts()) {
                extractFacts(userId, userMessage, assistantResponse, emotionResult);
            }

            if (decision.isRecordInteraction()) {
                affinityService.recordInteraction(userId);
            }
        } catch (Exception e) {
            log.warn("回合后处理失败: {}", e.getMessage(), e);
            try {
                affinityService.recordInteraction(userId);
            } catch (Exception ex) {
                log.warn("记录互动失败: {}", ex.getMessage(), ex);
            }
        }
    }

    private EmotionAnalysis analyzeEmotion(String userId, String userMessage) {
        try {
            EmotionAnalysis emotion = emotionAnalyzer.analyze(userMessage);
            if (emotion == null) {
                systemLogService.debug("情感分析返回空结果", "EMOTION");
                return null;
            }

            affinityService.updateEmotion(userId, emotion.getEmotion(), emotion.getIntensity());
            systemLogService.info(String.format(
                    "情感分析: %s (强度: %.2f)",
                    emotion.getEmotion(),
                    emotion.getIntensity() != null ? emotion.getIntensity() : 0.0
            ), "EMOTION");

            if (emotion.isPositive()) {
                affinityService.increaseAffinity(userId, 1);
            } else if (emotion.isNegative() && emotion.getIntensity() != null && emotion.getIntensity() > 0.5) {
                affinityService.decreaseAffinity(userId, 1);
            }

            if (emotion.needsAttention()) {
                systemLogService.info("检测到用户需要关注", "EMOTION");
            }

            emotionalEpisodeService.extractAndSaveEpisode(userId, userMessage, emotion);
            return emotion;
        } catch (Exception e) {
            log.warn("情感分析失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private void applyEmotionResult(String userId, String userMessage, EmotionAnalysis emotion) {
        if (emotion == null) {
            return;
        }
        affinityService.updateEmotion(userId, emotion.getEmotion(), emotion.getIntensity());
        if (emotion.isPositive()) {
            affinityService.increaseAffinity(userId, 1);
        } else if (emotion.isNegative() && emotion.getIntensity() != null && emotion.getIntensity() > 0.5) {
            affinityService.decreaseAffinity(userId, 1);
        }
        emotionalEpisodeService.extractAndSaveEpisode(userId, userMessage, emotion);
    }

    private void extractFacts(String userId, String userMessage, String assistantResponse, EmotionAnalysis emotion) {
        try {
            memoryService.extractFacts(userId, userMessage, assistantResponse, emotion);
        } catch (Exception e) {
            log.warn("事实提取失败: {}", e.getMessage(), e);
        }
    }

    private TurnDecisionClassifier buildDecisionClassifier() {
        return AiServices.builder(TurnDecisionClassifier.class)
                .chatModel(dynamicMemoryModel)
                .build();
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "n/a";
        }
        return reason.length() > 120 ? reason.substring(0, 120) + "..." : reason;
    }

    public interface TurnDecisionClassifier {

        @SystemMessage("""
                你是灵枢 (LingShu-AI) 的“回合后处理决策器”。
                你的唯一任务是在一轮对话结束后，判断是否触发以下三项：
                1. analyzeEmotion
                2. extractFacts
                3. recordInteraction

                你必须依据整轮语义判断，并直接返回严格 JSON。

                判定标准：

                1. analyzeEmotion
                - 只要用户表达了明显情绪、态度、压力、满意/不满、焦虑、兴奋等主观状态，设为 true。

                2. extractFacts
                - 只要用户透露了“可被后续对话复用”的个人信息或状态，设为 true。
                - 包括长期静态事实，也包括阶段性进行中状态。
                - 下面这类表达必须判为 true：
                  * 我在训练
                  * 我最近在备考
                  * 我这段时间在减脂
                  * 我正在学 Rust
                - 即使是技术对话，只要出现上述可记忆信息，也必须 true。
                - 仅当消息完全是一次性工具指令、纯噪声、无可记忆信息时，才可 false。

                3. recordInteraction
                - 绝大多数有效对话都应设为 true。
                - 仅空内容或明显系统噪声时可 false。

                输出要求：
                - confidence: 0.0 到 1.0
                - reason: 中文简短说明，明确指出触发依据
                - 只返回 JSON，不要任何额外文本
                - 严格注意：所有的 JSON 键和字符串值必须使用标准英文双引号 `"`，绝对不能使用中文双引号 `”` 等全角字符作为 JSON 格式符。

                返回格式：
                {
                  "analyzeEmotion": true,
                  "extractFacts": true,
                  "recordInteraction": true,
                  "confidence": 0.95,
                  "reason": "用户透露了进行中状态事实：我在训练"
                }
                """)
        @UserMessage("""
                【用户消息】
                {{userMessage}}

                【助手回复】
                {{assistantResponse}}

                直接返回决策 JSON。
                """)
        TurnPostProcessorDecision classify(@V("userMessage") String userMessage,
                                           @V("assistantResponse") String assistantResponse);
    }

    public static class TurnPostProcessorDecision {

        private boolean analyzeEmotion;
        private boolean extractFacts;
        private boolean recordInteraction;
        private double confidence;
        private String reason;

        public TurnPostProcessorDecision() {
        }

        public boolean isAnalyzeEmotion() {
            return analyzeEmotion;
        }

        public void setAnalyzeEmotion(boolean analyzeEmotion) {
            this.analyzeEmotion = analyzeEmotion;
        }

        public boolean isExtractFacts() {
            return extractFacts;
        }

        public void setExtractFacts(boolean extractFacts) {
            this.extractFacts = extractFacts;
        }

        public boolean isRecordInteraction() {
            return recordInteraction;
        }

        public void setRecordInteraction(boolean recordInteraction) {
            this.recordInteraction = recordInteraction;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
