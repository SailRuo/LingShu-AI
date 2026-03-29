package com.lingshu.ai.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.Set;

@Node("EmotionalEpisode")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionalEpisode {

    @Id
    @GeneratedValue
    private Long id;

    private String triggerEvent;
    
    private String emotionType;
    
    private Double emotionIntensity;
    
    private String emotionTrend;
    
    private Set<String> triggerKeywords;
    
    private String userResponse;
    
    private String copingMechanism;
    
    private String outcomeEmotion;
    
    private Double outcomeIntensity;
    
    private String contextSummary;
    
    private LocalDateTime occurredAt;
    
    private LocalDateTime lastRecalledAt;
    
    private double importance;
    
    private double recallCount;
    
    private String status;

    @Relationship(type = "EXPERIENCED_BY", direction = Relationship.Direction.OUTGOING)
    private UserNode user;
    
    public void incrementRecallCount() {
        this.recallCount++;
        this.lastRecalledAt = LocalDateTime.now();
    }
}
