package com.lingshu.ai.core.dto;

public class TurnPostProcessDecision {

    /**
     * 是否需要执行情感分析。
     * 该判断在一轮对话最终回答完成后进行，
     * 用于避免在 ReAct / 工具调用阶段触发情感处理。
     */
    private Boolean shouldAnalyzeEmotion;

    /**
     * 是否需要执行事实提取。
     * 由独立判定器基于本轮用户消息与最终回复共同决定。
     */
    private Boolean shouldExtractFacts;

    /**
     * 是否需要记录本轮互动。
     * 一般情况下大多数有效轮次都应为 true。
     */
    private Boolean shouldRecordInteraction;

    /**
     * 判定原因，用于日志与调试。
     * 例如：
     * - "pure tool-execution turn"
     * - "contains emotional intent"
     * - "contains stable user facts"
     * - "normal conversational turn"
     */
    private String reason;

    public TurnPostProcessDecision() {
    }

    public TurnPostProcessDecision(Boolean shouldAnalyzeEmotion,
                                   Boolean shouldExtractFacts,
                                   Boolean shouldRecordInteraction,
                                   String reason) {
        this.shouldAnalyzeEmotion = shouldAnalyzeEmotion;
        this.shouldExtractFacts = shouldExtractFacts;
        this.shouldRecordInteraction = shouldRecordInteraction;
        this.reason = reason;
    }

    public Boolean getShouldAnalyzeEmotion() {
        return shouldAnalyzeEmotion;
    }

    public void setShouldAnalyzeEmotion(Boolean shouldAnalyzeEmotion) {
        this.shouldAnalyzeEmotion = shouldAnalyzeEmotion;
    }

    public Boolean getShouldExtractFacts() {
        return shouldExtractFacts;
    }

    public void setShouldExtractFacts(Boolean shouldExtractFacts) {
        this.shouldExtractFacts = shouldExtractFacts;
    }

    public Boolean getShouldRecordInteraction() {
        return shouldRecordInteraction;
    }

    public void setShouldRecordInteraction(Boolean shouldRecordInteraction) {
        this.shouldRecordInteraction = shouldRecordInteraction;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean shouldAnalyzeEmotion() {
        return Boolean.TRUE.equals(shouldAnalyzeEmotion);
    }

    public boolean shouldExtractFacts() {
        return Boolean.TRUE.equals(shouldExtractFacts);
    }

    public boolean shouldRecordInteraction() {
        return Boolean.TRUE.equals(shouldRecordInteraction);
    }

    public static TurnPostProcessDecision none(String reason) {
        return new TurnPostProcessDecision(false, false, false, reason);
    }

    public static TurnPostProcessDecision interactionOnly(String reason) {
        return new TurnPostProcessDecision(false, false, true, reason);
    }

    public static TurnPostProcessDecision full(String reason) {
        return new TurnPostProcessDecision(true, true, true, reason);
    }

    @Override
    public String toString() {
        return "TurnPostProcessDecision{" +
                "shouldAnalyzeEmotion=" + shouldAnalyzeEmotion +
                ", shouldExtractFacts=" + shouldExtractFacts +
                ", shouldRecordInteraction=" + shouldRecordInteraction +
                ", reason='" + reason + '\'' +
                '}';
    }
}
