package com.lingshu.ai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.lingshu.ai")
public class LingshuAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LingshuAiApplication.class, args);
    }
}
