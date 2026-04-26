package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;

public interface RetrievalFeedbackAnalyzer {

    RetrievalFeedbackResult analyze(RetrievalContextSnapshot snapshot, String assistantResponse);
}
