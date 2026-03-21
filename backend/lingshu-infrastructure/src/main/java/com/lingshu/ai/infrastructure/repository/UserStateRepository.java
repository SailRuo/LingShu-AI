package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.UserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserStateRepository extends JpaRepository<UserState, Long> {

    Optional<UserState> findByUserId(String userId);

    @Query("SELECT u FROM UserState u WHERE u.needsGreeting = true")
    List<UserState> findUsersNeedingGreeting();

    @Query("SELECT u FROM UserState u WHERE u.lastActiveTime < :threshold")
    List<UserState> findInactiveUsers(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT u FROM UserState u WHERE u.lastEmotion = :emotion")
    List<UserState> findByLastEmotion(@Param("emotion") String emotion);

    @Query("SELECT u FROM UserState u WHERE u.affinity >= :minAffinity AND u.affinity <= :maxAffinity")
    List<UserState> findByAffinityRange(@Param("minAffinity") Integer minAffinity, @Param("maxAffinity") Integer maxAffinity);

    @Query("SELECT u FROM UserState u WHERE u.relationshipStage = :stage")
    List<UserState> findByRelationshipStage(@Param("stage") String stage);

    boolean existsByUserId(String userId);
}
