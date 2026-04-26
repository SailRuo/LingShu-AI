package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class RetrievalFeedbackAnalyzerImpl implements RetrievalFeedbackAnalyzer {

    private static final Pattern NON_CONTENT_PATTERN = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9]");
    private static final Pattern LEADING_SUBJECT_PATTERN = Pattern.compile("^(用户|你|他|她|对方)+");

    private final FallbackJudge fallbackJudge;

    @Autowired
    public RetrievalFeedbackAnalyzerImpl(DynamicMemoryModel dynamicMemoryModel) {
        this(dynamicMemoryModel, RetrievalFeedbackAnalyzerImpl::buildFallbackJudge);
    }

    RetrievalFeedbackAnalyzerImpl(FallbackJudge fallbackJudge) {
        this.fallbackJudge = Objects.requireNonNull(fallbackJudge, "fallbackJudge");
    }

    RetrievalFeedbackAnalyzerImpl(DynamicMemoryModel dynamicMemoryModel, FallbackJudgeFactory fallbackJudgeFactory) {
        this(Objects.requireNonNull(fallbackJudgeFactory, "fallbackJudgeFactory")
                .create(Objects.requireNonNull(dynamicMemoryModel, "dynamicMemoryModel")));
    }

    @Override
    public RetrievalFeedbackResult analyze(RetrievalContextSnapshot snapshot, String assistantResponse) {
        if (snapshot == null) {
            return RetrievalFeedbackResult.builder()
                    .turnId(null)
                    .factFeedback(List.of())
                    .build();
        }

        List<RetrievalFeedbackResult.FactFeedback> feedback = new ArrayList<>();
        for (RetrievalFactCandidate fact : snapshot.getContextFacts()) {
            feedback.add(analyzeFact(fact, assistantResponse));
        }

        return RetrievalFeedbackResult.builder()
                .turnId(snapshot.getTurnId())
                .factFeedback(feedback)
                .build();
    }

    private RetrievalFeedbackResult.FactFeedback analyzeFact(RetrievalFactCandidate fact, String assistantResponse) {
        if (fact == null) {
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(null)
                    .valid(null)
                    .confidence(null)
                    .reason("missing fact candidate")
                    .build();
        }

        if (isDirectlySupported(fact, assistantResponse)) {
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .valid(Boolean.TRUE)
                    .confidence(0.98d)
                    .reason("direct-content-match")
                    .build();
        }

        try {
            return normalizeFallbackResult(fact, fallbackJudge.judge(fact, assistantResponse));
        } catch (RuntimeException exception) {
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .valid(null)
                    .confidence(null)
                    .reason("fallback judge failed: " + exception.getMessage())
                    .build();
        }
    }

    private RetrievalFeedbackResult.FactFeedback normalizeFallbackResult(RetrievalFactCandidate fact,
                                                                         RetrievalFeedbackResult.FactFeedback judged) {
        if (judged == null) {
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact.getFactId())
                    .valid(null)
                    .confidence(null)
                    .reason("fallback judge returned no result")
                    .build();
        }

        return RetrievalFeedbackResult.FactFeedback.builder()
                .factId(fact.getFactId())
                .valid(judged.getValid())
                .confidence(judged.getConfidence())
                .reason(judged.getReason())
                .build();
    }

    private boolean isDirectlySupported(RetrievalFactCandidate fact, String assistantResponse) {
        String normalizedResponse = normalize(assistantResponse);
        if (normalizedResponse.isEmpty()) {
            return false;
        }

        for (String candidate : directMatchCandidates(fact.getContent())) {
            if (candidate.length() >= 4 && normalizedResponse.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> directMatchCandidates(String factContent) {
        String normalizedFact = normalize(factContent);
        if (normalizedFact.isEmpty()) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(normalizedFact);

        String subjectStripped = LEADING_SUBJECT_PATTERN.matcher(normalizedFact).replaceFirst("");
        if (!subjectStripped.isEmpty()) {
            candidates.add(subjectStripped);
        }

        return candidates.stream().distinct().toList();
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return NON_CONTENT_PATTERN.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static FallbackJudge buildFallbackJudge(DynamicMemoryModel dynamicMemoryModel) {
        RetrievalFeedbackJudge judge = AiServices.builder(RetrievalFeedbackJudge.class)
                .chatModel(dynamicMemoryModel)
                .build();

        return (fact, assistantResponse) -> {
            JudgeDecision decision = judge.judge(
                    fact != null ? fact.getContent() : null,
                    assistantResponse == null ? "" : assistantResponse
            );
            return RetrievalFeedbackResult.FactFeedback.builder()
                    .factId(fact != null ? fact.getFactId() : null)
                    .valid(decision != null ? decision.getValid() : null)
                    .confidence(decision != null ? decision.getConfidence() : null)
                    .reason(decision != null ? decision.getReason() : "fallback judge returned no result")
                    .build();
        };
    }

    @FunctionalInterface
    interface FallbackJudge {

        RetrievalFeedbackResult.FactFeedback judge(RetrievalFactCandidate fact, String assistantResponse);
    }

    @FunctionalInterface
    interface FallbackJudgeFactory {

        FallbackJudge create(DynamicMemoryModel dynamicMemoryModel);
    }

    interface RetrievalFeedbackJudge {

        @SystemMessage("""
                你是检索反馈分析器。
                你的任务是判断助手回复是否实际使用了给定上下文事实。

                请只返回合法 JSON：
                {
                  "valid": true,
                  "confidence": 0.92,
                  "reason": "简要说明判断依据"
                }

                判断规则：
                1. valid=true 表示助手回复明确使用、复述或依赖了该事实。
                2. valid=false 表示助手回复没有使用该事实，或与该事实明显不符。
                3. 如果无法稳定判断，可将 valid 设为 null，confidence 设为 null，并说明原因。
                4. confidence 范围为 0 到 1。
                """)
        @UserMessage("""
                上下文事实：
                {{factContent}}

                助手回复：
                {{assistantResponse}}
                """)
        JudgeDecision judge(@V("factContent") String factContent, @V("assistantResponse") String assistantResponse);
    }

    static class JudgeDecision {

        private Boolean valid;
        private Double confidence;
        private String reason;

        public Boolean getValid() {
            return valid;
        }

        public void setValid(Boolean valid) {
            this.valid = valid;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
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
