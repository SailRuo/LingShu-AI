package com.lingshu.ai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.lingshu.ai", exclude = {DataSourceAutoConfiguration.class, Neo4jDataAutoConfiguration.class})
public class LingshuAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LingshuAiApplication.class, args);
    }
}
