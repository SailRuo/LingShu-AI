package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.dto.RetrievalContextSnapshot;
import com.lingshu.ai.core.dto.RetrievalFactCandidate;
import com.lingshu.ai.core.dto.RetrievalFeedbackResult;
import com.lingshu.ai.core.service.RetrievalContextSnapshotStore;
import com.lingshu.ai.core.service.RetrievalFeedbackAnalyzer;
import com.lingshu.ai.core.service.SystemLogService;
import com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord;
import com.lingshu.ai.infrastructure.repository.RetrievalFeedbackRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalFeedbackServiceSaveAllTest {

    @Test
    void analyzeTurn_shouldMapFactFeedbackRecordsBeforeSaveAll() {
        RetrievalContextSnapshot snapshot = RetrievalContextSnapshot.builder()
                .userId("user-42")
                .sessionId(12L)
                .turnId(104L)
                .query("用户最近在学什么")
                .routingDecision("GRAPH_ONLY")
                .contextFacts(List.of(
                        RetrievalFactCandidate.builder().factId(11L).content("用户在学 Rust").build(),
                        RetrievalFactCandidate.builder().factId(12L).content("用户喜欢骑行").build()
                ))
                .build();
        RetrievalContextSnapshotStore snapshotStore = mock(RetrievalContextSnapshotStore.class);
        when(snapshotStore.findByTurnId(104L)).thenReturn(Optional.of(snapshot));

        RetrievalFeedbackAnalyzer analyzer = mock(RetrievalFeedbackAnalyzer.class);
        when(analyzer.analyze(snapshot, "assistant response")).thenReturn(RetrievalFeedbackResult.builder()
                .turnId(104L)
                .factFeedback(List.of(
                        RetrievalFeedbackResult.FactFeedback.builder()
                                .factId(11L)
                                .valid(Boolean.TRUE)
                                .confidence(0.91)
                                .reason("回答与检索事实一致")
                                .build(),
                        RetrievalFeedbackResult.FactFeedback.builder()
                                .factId(12L)
                                .valid(Boolean.FALSE)
                                .confidence(0.32)
                                .reason("回答误引了偏好")
                                .build()
                ))
                .build());

        RetrievalFeedbackRecordRepository repository = mock(RetrievalFeedbackRecordRepository.class);
        when(repository.findByTurnIdAndFactIdIn(anyLong(), anyCollection())).thenReturn(List.of());
        RetrievalFeedbackService service = new RetrievalFeedbackService(
                snapshotStore,
                analyzer,
                mock(SystemLogService.class),
                repository
        );

        service.analyzeTurn(104L, "assistant response");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetrievalFeedbackRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(recordsCaptor.capture());

        List<RetrievalFeedbackRecord> records = recordsCaptor.getValue();
        assertThat(records).hasSize(2);
        assertThat(records)
                .extracting(
                        RetrievalFeedbackRecord::getTurnId,
                        RetrievalFeedbackRecord::getSessionId,
                        RetrievalFeedbackRecord::getUserId,
                        RetrievalFeedbackRecord::getFactId,
                        RetrievalFeedbackRecord::getQuery,
                        RetrievalFeedbackRecord::getRoutingDecision,
                        RetrievalFeedbackRecord::getValid,
                        RetrievalFeedbackRecord::getConfidence,
                        RetrievalFeedbackRecord::getReason
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                104L, 12L, "user-42", 11L, "用户最近在学什么", "GRAPH_ONLY", true, 0.91, "回答与检索事实一致"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                104L, 12L, "user-42", 12L, "用户最近在学什么", "GRAPH_ONLY", false, 0.32, "回答误引了偏好"
                        )
                );
    }
}
