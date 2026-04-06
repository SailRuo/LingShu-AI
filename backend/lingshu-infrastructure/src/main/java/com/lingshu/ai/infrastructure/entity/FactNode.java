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

    private String normalizedContent;

    private String subType;

    private String clusterKey;

    private LocalDateTime observedAt;

    private LocalDateTime eventTime;

    private LocalDateTime lastActivatedAt;

    private double importance; // 0.0 - 1.0

    private double confidence; // 0.0 - 1.0
    private String classificationSource;

    private double activityScore; // 0.0 - 1.0

    private String status; // active / stable / cool / superseded / conflicted

    private Double decayRate;

    private Integer ttlDays;

    private Integer version;

    private Long supersedesFactId;

    private Long contradictsFactId;

    private String emotionalTone;

    private java.util.Set<String> involvedEntities;

    private String originalMessage;
}
