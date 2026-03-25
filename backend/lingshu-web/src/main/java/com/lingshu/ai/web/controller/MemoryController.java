package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/memory")
@CrossOrigin(origins = "*")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 获取记忆图谱数据，用于前端可视化展示。
     */
    @GetMapping("/graph")
    public Object getGraph() {
        // 目前默认使用 "User" 作为用户标识
        return memoryService.getGraphData("User");
    }

    /**
     * 手动触发记忆提取流程。
     */
    @PostMapping("/extract")
    public void manualExtract(@RequestBody String text) {
        memoryService.extractFacts("User", text);
    }

    /**
     * 删除指定的事实节点及其关联。
     */
    @DeleteMapping("/fact/{id}")
    public void deleteFact(@PathVariable Long id) {
        log.info("API 触发：正在清理事实节点 #{}", id);
        memoryService.deleteFact(id);
    }

    /**
     * 手动执行一次记忆生命周期维护。
     */
    @PostMapping("/maintenance/run")
    public Object runMaintenance() {
        log.info("API 触发：执行记忆生命周期维护");
        return memoryService.runMemoryMaintenance();
    }

    /**
     * 获取最近一次记忆生命周期维护摘要。
     */
    @GetMapping("/maintenance/summary")
    public Object getMaintenanceSummary() {
        return memoryService.getMemoryMaintenanceSummary();
    }
}
