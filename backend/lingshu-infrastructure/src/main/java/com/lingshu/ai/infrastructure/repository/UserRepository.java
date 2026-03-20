package com.lingshu.ai.infrastructure.repository;

import com.lingshu.ai.infrastructure.entity.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends Neo4jRepository<UserNode, Long> {

    Optional<UserNode> findByName(String name);
}
