package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.MemoryService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MemoryMaintenanceScheduler {

    private final MemoryService memoryService;

    public MemoryMaintenanceScheduler(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledMemoryMaintenance() {
        memoryService.runMemoryMaintenance();
    }
}
