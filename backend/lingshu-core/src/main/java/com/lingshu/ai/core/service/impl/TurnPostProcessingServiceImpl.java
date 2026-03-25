package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.service.AffinityService;
import com.lingshu.ai.core.service.EmotionAnalyzer;
import com.lingshu.ai.core.service.MemoryService;
import com.lingshu.ai.core.service.SystemLogService;
import dev.langchain4j.model.chat.ChatModel;
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
    private final ChatModel chatLanguageModel;

    public TurnPostProcessingServiceImpl(EmotionAnalyzer emotionAnalyzer,
                                         MemoryService memoryService,
                                         AffinityService affinityService,
                                         SystemLogService systemLogService,
                                         ChatModel chatLanguageModel) {
        this.emotionAnalyzer = emotionAnalyzer;
        this.memoryService = memoryService;
        this.affinityService = affinityService;
        this.systemLogService = systemLogService;
        this.chatLanguageModel = chatLanguageModel;
    }

    @Async("taskExecutor")
    public void processCompletedTurn(String userId, String userMessage, String assistantResponse) {
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

            systemLogService.info(String.format(
                    "回合后处理决策: emotion=%s, facts=%s, interaction=%s, confidence=%.2f, reason=%s",
                    decision.isAnalyzeEmotion(),
                    decision.isExtractFacts(),
                    decision.isRecordInteraction(),
                    decision.getConfidence(),
                    safeReason(decision.getReason())
            ), "POST_PROCESS");

            systemLogService.success(String.format(
                    "后处理决策事件 | analyzeEmotion=%s | extractFacts=%s | recordInteraction=%s | confidence=%.2f",
                    decision.isAnalyzeEmotion(),
                    decision.isExtractFacts(),
                    decision.isRecordInteraction(),
                    decision.getConfidence()
            ), "POST_PROCESS");

            if (decision.isAnalyzeEmotion()) {
                systemLogService.info("后处理事件: 已触发情感分析", "POST_PROCESS");
                analyzeEmotion(userId, userMessage);
            } else {
                systemLogService.debug("后处理事件: 跳过情感分析", "POST_PROCESS");
            }

            if (decision.isExtractFacts()) {
                systemLogService.info("后处理事件: 已触发事实提取", "POST_PROCESS");
                extractFacts(userId, userMessage);
            } else {
                systemLogService.debug("后处理事件: 跳过事实提取", "POST_PROCESS");
            }

            if (decision.isRecordInteraction()) {
                systemLogService.info("后处理事件: 已记录互动", "POST_PROCESS");
                affinityService.recordInteraction(userId);
            } else {
                systemLogService.debug("后处理事件: 跳过互动记录", "POST_PROCESS");
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

    private void analyzeEmotion(String userId, String userMessage) {
        try {
            systemLogService.info("回合后处理: 开始情感分析", "EMOTION");
            EmotionAnalysis emotion = emotionAnalyzer.analyze(userMessage);
            if (emotion == null) {
                systemLogService.debug("情感分析返回空结果", "EMOTION");
                return;
            }

            affinityService.updateEmotion(userId, emotion.getEmotion(), emotion.getIntensity());
            systemLogService.info(String.format(
                    "情绪分析: %s (强度: %.2f)",
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
        } catch (Exception e) {
            log.warn("情感分析失败: {}", e.getMessage(), e);
        }
    }

    private void extractFacts(String userId, String userMessage) {
        try {
            systemLogService.info("回合后处理: 开始事实提取", "MEMORY");
            memoryService.extractFacts(userId, userMessage);
        } catch (Exception e) {
            log.warn("事实提取失败: {}", e.getMessage(), e);
        }
    }

    private TurnDecisionClassifier buildDecisionClassifier() {
        return AiServices.builder(TurnDecisionClassifier.class)
                .chatModel(chatLanguageModel)
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
                你是“回合后处理决策器”。
                你的任务不是回复用户，而是在一轮对话已经完成后，
                根据“用户原始消息”和“助手最终回复”判断：
                1. 是否需要做情感分析
                2. 是否需要做事实提取
                3. 是否需要记录一次互动

                关键原则：
                - 你只负责“是否触发”的决策，不做真正的情感分析/事实提取。
                - 你必须尽量智能，不依赖固定关键词。
                - 重点判断这一轮是否具有“用户状态更新价值”或“长期记忆价值”。
                - 如果这轮主要是后端工具执行、命令查询、文件读取、代码排查、环境检查，
                  且没有明显暴露用户情绪或稳定个人事实，则不要触发情感分析/事实提取。
                - 如果用户表达了情绪、态度、困扰、满意/失望、压力、偏好、身份、计划、经历、
                  长期稳定习惯、关系信息、自我描述等，则应触发相应处理。
                - 如果用户透露了具有长期记忆价值的稳定事实，例如持续的偏好、反复出现的困扰、
                  性相关需求或习惯、常见的应对方式、自我认知或关系状态，通常应触发 extractFacts=true。
                - reason 必须只基于当前提供的“用户消息”和“助手最终回复”，不得编造未出现的细节。
                - recordInteraction 通常应为 true；除非输入无效或完全没有形成有效回合。

                决策定义：
                - analyzeEmotion=true：
                  当用户消息值得更新“当前情绪状态”时触发。
                - extractFacts=true：
                  当用户消息中可能包含值得写入长期记忆的稳定信息时触发。
                - recordInteraction=true：
                  当这是一轮真实有效的用户交互时触发。

                输出要求：
                你必须且只能输出合法 JSON，不得输出 Markdown，不得输出解释文字。
                格式如下：
                {
                  "analyzeEmotion": true,
                  "extractFacts": false,
                  "recordInteraction": true,
                  "confidence": 0.0,
                  "reason": "一句简短中文原因"
                }
                """)
        @UserMessage("""
                用户消息：
                {{userMessage}}

                助手最终回复：
                {{assistantResponse}}

                请直接返回 JSON。
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
