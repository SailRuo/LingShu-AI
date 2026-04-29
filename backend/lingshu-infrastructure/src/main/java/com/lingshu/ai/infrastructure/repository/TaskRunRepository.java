package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRunRepository extends JpaRepository<TaskRun, Long> {

    List<TaskRun> findByUserIdOrderByUpdatedAtDescIdDesc(String userId);

    List<TaskRun> findByState(TaskRunState state);

    Optional<TaskRun> findByIdAndUserId(Long id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from TaskRun run where run.id = :id")
    Optional<TaskRun> findByIdForUpdate(@Param("id") Long id);
}
