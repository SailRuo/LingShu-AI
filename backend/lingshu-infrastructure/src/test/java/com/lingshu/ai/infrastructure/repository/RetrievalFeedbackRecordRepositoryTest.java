package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(RetrievalFeedbackRecordRepositoryTest.TestJpaConfiguration.class)
class RetrievalFeedbackRecordRepositoryTest {

    @Autowired
    private RetrievalFeedbackRecordRepository repository;

    @Test
    void saveAndFlush_shouldPopulateCreatedAtViaPrePersistWhenMissing() {
        RetrievalFeedbackRecord record = RetrievalFeedbackRecord.builder()
                .turnId(201L)
                .sessionId(301L)
                .userId("user-201")
                .factId(401L)
                .query("用户最近在关注什么")
                .routingDecision("GRAPH_ONLY")
                .valid(Boolean.TRUE)
                .confidence(0.88)
                .reason("回答与事实一致")
                .build();

        assertThat(record.getCreatedAt()).isNull();

        RetrievalFeedbackRecord saved = repository.saveAndFlush(record);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void saveAndFlush_shouldRejectDuplicateTurnFactPair() {
        RetrievalFeedbackRecord first = RetrievalFeedbackRecord.builder()
                .turnId(202L)
                .sessionId(302L)
                .userId("user-202")
                .factId(402L)
                .query("用户最近在关注什么")
                .routingDecision("GRAPH_ONLY")
                .valid(Boolean.TRUE)
                .confidence(0.90)
                .reason("首次记录")
                .build();
        repository.saveAndFlush(first);

        RetrievalFeedbackRecord duplicate = RetrievalFeedbackRecord.builder()
                .turnId(202L)
                .sessionId(302L)
                .userId("user-202")
                .factId(402L)
                .query("用户最近在关注什么")
                .routingDecision("GRAPH_ONLY")
                .valid(Boolean.FALSE)
                .confidence(0.30)
                .reason("重复写入")
                .build();

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = RetrievalFeedbackRecord.class)
    @EnableJpaRepositories(basePackageClasses = RetrievalFeedbackRecordRepository.class)
    static class TestJpaConfiguration {
    }
}
