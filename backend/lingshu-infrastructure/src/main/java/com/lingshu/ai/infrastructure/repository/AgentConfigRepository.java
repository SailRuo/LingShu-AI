package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.AgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {
    
    Optional<AgentConfig> findByName(String name);
    
    Optional<AgentConfig> findByIsDefaultTrue();
    
    List<AgentConfig> findByIsActiveTrueOrderByCreatedAtDesc();
    
    List<AgentConfig> findAllByOrderByIsDefaultDescCreatedAtDesc();
}
