package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "memory_state_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryStateRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fact_id", nullable = false, unique = true)
    private Long factId;

    // Serialized float vector payload (JSON/CSV decided in service layer).
    @Lob
    @Column(name = "task_vector", columnDefinition = "TEXT")
    private String taskVector;

    @Column(name = "task_uncertainty", nullable = false)
    private Double taskUncertainty;

    @Column(name = "update_count", nullable = false)
    private Integer updateCount;

    @Column(name = "state_version", nullable = false)
    private Long stateVersion;

    @Column(name = "last_update", nullable = false)
    private LocalDateTime lastUpdate;

    @PrePersist
    void initializeDefaults() {
        if (taskUncertainty == null) {
            taskUncertainty = 1.0d;
        }
        if (updateCount == null) {
            updateCount = 0;
        }
        if (stateVersion == null) {
            stateVersion = 1L;
        }
        if (lastUpdate == null) {
            lastUpdate = LocalDateTime.now();
        }
    }
}
