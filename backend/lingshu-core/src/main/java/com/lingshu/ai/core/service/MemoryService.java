package com.lingshu.ai.core.service;

public interface MemoryService {
    
    /**
     * 从用户消息中提取事实并保存到图数据库中。
     */
    void extractFacts(String userId, String message);

    /**
     * 从记忆中检索与当前对话相关的上下文。
     */
    String retrieveContext(String userId, String message);

    /**
     * 获取完整的记忆图谱数据以供可视化。
     */
    Object getGraphData(String userId);

    /**
     * 通过 ID 删除一个事实，并清理相关的向量语义记忆。
     */
    void deleteFact(Long factId);

    /**
     * 执行一次记忆生命周期维护。
     */
    Object runMemoryMaintenance();

    /**
     * 获取最近一次记忆生命周期维护摘要。
     */
    Object getMemoryMaintenanceSummary();
}
