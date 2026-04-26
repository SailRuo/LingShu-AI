package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.RetrievalFeedbackRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RetrievalFeedbackRecordRepository extends JpaRepository<RetrievalFeedbackRecord, Long> {

    List<RetrievalFeedbackRecord> findByTurnIdAndFactIdIn(Long turnId, Collection<Long> factIds);
}
