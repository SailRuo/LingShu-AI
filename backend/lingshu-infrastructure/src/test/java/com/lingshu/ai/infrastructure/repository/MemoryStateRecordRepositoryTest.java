package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.MemoryStateRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MemoryStateRecordRepositoryTest.TestJpaConfiguration.class)
class MemoryStateRecordRepositoryTest {

    @Autowired
    private MemoryStateRecordRepository repository;

    @Test
    void saveAndFindByFactId_shouldPersistAndQueryStateRecord() {
        MemoryStateRecord record = MemoryStateRecord.builder()
                .factId(501L)
                .taskVector("[0.11,0.22,0.33]")
                .build();

        MemoryStateRecord saved = repository.saveAndFlush(record);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTaskUncertainty()).isEqualTo(1.0d);
        assertThat(saved.getUpdateCount()).isZero();
        assertThat(saved.getStateVersion()).isEqualTo(1L);
        assertThat(saved.getLastUpdate()).isNotNull();

        MemoryStateRecord loaded = repository.findByFactId(501L).orElse(null);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getTaskVector()).isEqualTo("[0.11,0.22,0.33]");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = MemoryStateRecord.class)
    @EnableJpaRepositories(basePackageClasses = MemoryStateRecordRepository.class)
    static class TestJpaConfiguration {
    }
}
