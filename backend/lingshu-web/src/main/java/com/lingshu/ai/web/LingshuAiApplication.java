package com.lingshu.ai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@SpringBootApplication(scanBasePackages = "com.lingshu.ai")
@EnableNeo4jRepositories(basePackages = "com.lingshu.ai.infrastructure.repository")
@EnableJpaRepositories(basePackages = "com.lingshu.ai.infrastructure.repository")
@EntityScan(basePackages = "com.lingshu.ai.infrastructure.entity")
public class LingshuAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LingshuAiApplication.class, args);
    }
}
