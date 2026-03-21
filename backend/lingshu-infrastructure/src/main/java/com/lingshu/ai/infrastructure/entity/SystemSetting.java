package com.lingshu.ai.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    @Id
    private String id; // Use "DEFAULT" for the main setting

    private String source; // "ollama" or "openai"

    private String chatModel;
    
    private String baseUrl;
    
    private String apiKey;
    
    private Boolean proactiveEnabled;
    
    private Integer inactiveThresholdMinutes;
    
    private Integer greetingCooldownSeconds;
    
    private Long inactiveCheckIntervalMs;
}
