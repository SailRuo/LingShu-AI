package com.lingshu.ai.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("Fact")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactNode {

    @Id
    @GeneratedValue
    private Long id;

    private String content;

    private String category; // e.g. "Work", "Preference", "Emotion"

    private LocalDateTime observedAt;

    private double importance; // 0.0 - 1.0
}
