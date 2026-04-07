package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 获取记忆图谱数据，用于前端可视化展示。
     */
    @GetMapping("/graph")
    public Object getGraph(@RequestParam(required = false) String userId) {
        // 目前默认使用 "User" 作为用户标识
        return memoryService.getGraphData(resolveUserId(userId));
    }

    /**
     * 手动触发记忆提取流程。
     */
    @PostMapping("/extract")
    public void manualExtract(@RequestParam(required = false) String userId, @RequestBody String text) {
        memoryService.extractFacts(resolveUserId(userId), text);
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

    /**
     * 手动更新事实分类。
     */
    @PutMapping("/fact/{id}/classification")
    public void updateFactClassification(@PathVariable Long id, @RequestBody java.util.Map<String, String> payload) {
        log.info("API 触发：手动更新事实分类 #{}", id);
        memoryService.updateFactClassification(id, payload.get("clusterKey"), payload.get("subType"));
    }

    /**
     * 获取最近的记忆检索事件流。
     */
    @GetMapping("/events")
    public Object getRecentRetrievalEvents() {
        return memoryService.getRecentRetrievalEvents();
    }

    /**
     * P2: 获取记忆治理列表。
     */
    @GetMapping("/governance/list")
    public Object getMemoryGovernanceList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) String userId) {
        return memoryService.getMemoryGovernanceList(page, size, status, userId);
    }

    /**
     * P2: 手动归档事实。
     */
    @PutMapping("/fact/{id}/archive")
    public void archiveFact(@PathVariable Long id) {
        log.info("API 触发：手动归档事实 #{}", id);
        memoryService.archiveFact(id);
    }

    /**
     * P2: 手动恢复事实。
     */
    @PutMapping("/fact/{id}/restore")
    public void restoreFact(@PathVariable Long id) {
        log.info("API 触发：手动恢复事实 #{}", id);
        memoryService.restoreFact(id);
    }
    private String resolveUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "memory-debug:default";
        }
        return userId.trim();
    }
}
