package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.McpServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface McpServerConfigRepository extends JpaRepository<McpServerConfig, Long> {
    List<McpServerConfig> findByIsActiveTrue();
}
