package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import com.lingshu.ai.core.service.SystemLogService;
import org.springframework.stereotype.Service;

@Service
public class RetrievalFeedbackService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RetrievalFeedbackService.class);

    private final RetrievalContextSnapshotStore snapshotStore;
    private final RetrievalFeedbackAnalyzer retrievalFeedbackAnalyzer;
    private final SystemLogService systemLogService;

    public RetrievalFeedbackService(RetrievalContextSnapshotStore snapshotStore,
                                    RetrievalFeedbackAnalyzer retrievalFeedbackAnalyzer,
                                    SystemLogService systemLogService) {
        this.snapshotStore = snapshotStore;
        this.retrievalFeedbackAnalyzer = retrievalFeedbackAnalyzer;
        this.systemLogService = systemLogService;
    }

    public void analyzeTurn(Long turnId, String assistantResponse) {
        if (turnId == null) {
            return;
        }

        RetrievalContextSnapshot snapshot = snapshotStore.findByTurnId(turnId).orElse(null);
        if (snapshot == null) {
            systemLogService.debug("检索反馈跳过: 未找到快照 turnId=" + turnId, "MEMORY");
            return;
        }

        try {
            RetrievalFeedbackResult result = retrievalFeedbackAnalyzer.analyze(
                    snapshot,
                    assistantResponse == null ? "" : assistantResponse
            );
            logAnalysis(turnId, snapshot, result);
        } catch (Exception exception) {
            log.warn("检索反馈分析失败 turnId={}: {}", turnId, exception.getMessage(), exception);
            systemLogService.warn("检索反馈分析失败 turnId=" + turnId + ": " + exception.getMessage(), "MEMORY");
        } finally {
            snapshotStore.remove(turnId);
        }
    }

    private void logAnalysis(Long turnId,
                             RetrievalContextSnapshot snapshot,
                             RetrievalFeedbackResult result) {
        int contextFactCount = snapshot.getContextFacts().size();
        int supportedCount = 0;
        int unsupportedCount = 0;
        int uncertainCount = 0;

        for (RetrievalFeedbackResult.FactFeedback feedback : result.getFactFeedback()) {
            if (feedback == null || feedback.getValid() == null) {
                uncertainCount++;
            } else if (Boolean.TRUE.equals(feedback.getValid())) {
                supportedCount++;
            } else {
                unsupportedCount++;
            }
        }

        systemLogService.info(String.format(
                "检索反馈分析完成: turnId=%d, routing=%s, contextFacts=%d, supportedFacts=%d, unsupportedFacts=%d, uncertainFacts=%d",
                turnId,
                snapshot.getRoutingDecision(),
                contextFactCount,
                supportedCount,
                unsupportedCount,
                uncertainCount
        ), "MEMORY");
    }
}
