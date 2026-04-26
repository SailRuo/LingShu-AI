package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.MemoryStateRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemoryStateRecordRepository extends JpaRepository<MemoryStateRecord, Long> {

    Optional<MemoryStateRecord> findByFactId(Long factId);
}
