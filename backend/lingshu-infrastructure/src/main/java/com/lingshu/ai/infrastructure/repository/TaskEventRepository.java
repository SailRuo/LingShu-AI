package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    List<TaskEvent> findByTaskRunIdOrderBySequenceNoAsc(Long taskRunId);

    Optional<TaskEvent> findTopByTaskRunIdOrderBySequenceNoDesc(Long taskRunId);
}
