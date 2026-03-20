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
import java.util.HashSet;
import java.util.Set;

@Node("User")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNode {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String nickname;

    private LocalDateTime firstEncounter;

    private LocalDateTime lastSeen;

    @Relationship(type = "HAS_FACT", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<FactNode> facts = new HashSet<>();

    public void addFact(FactNode fact) {
        this.facts.add(fact);
    }
}
