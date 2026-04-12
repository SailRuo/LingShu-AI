package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mcp_server_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String transportType; // "STDIO" or "SSE"

    private String command; // For STDIO

    @Column(columnDefinition = "TEXT")
    private String args; // JSON array of args for STDIO

    @Column(columnDefinition = "TEXT")
    private String env; // JSON object of env vars

    private String url; // For SSE

    @Column(columnDefinition = "TEXT")
    private String headers; // JSON object of headers for SSE/HTTP

    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    private java.util.List<java.util.Map<String, Object>> tools;
}
