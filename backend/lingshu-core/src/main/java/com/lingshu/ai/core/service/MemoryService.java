package com.lingshu.ai.core.service;

public interface MemoryService {

    /**
     * 从用户消息中提取事实并保存到图数据库中。
     */
    void extractFacts(String userId, String message);

    void extractFacts(String userId, String message, com.lingshu.ai.core.dto.EmotionAnalysis emotion);

    void extractFacts(String userId, String userMessage, String assistantResponse, com.lingshu.ai.core.dto.EmotionAnalysis emotion);

    /**
     * 从记忆中检索与当前对话相关的上下文。
     */
    String retrieveContext(String userId, String message);

    void updateRelationshipsFromRetrievalEvent(com.lingshu.ai.core.dto.MemoryRetrievalEvent event);

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
    void updateFactClassification(Long factId, String clusterKey, String subType);

    java.util.List<com.lingshu.ai.core.dto.MemoryRetrievalEvent> getRecentRetrievalEvents(String userId);

    /**
     * P2: 记忆治理 - 获取所有事实列表（包含已归档等），支持分页或治理。
     */
    Object getMemoryGovernanceList(int page, int size, String status, String userId);

    /**
     * P2: 记忆治理 - 手动将记忆归档到冷库
     */
    void archiveFact(Long factId);

    /**
     * P2: 记忆治理 - 手动从冷库恢复记忆
     */
    void restoreFact(Long factId);

    /**
     * 一次性重建所有已知事实的语义向量索引，用于元数据补全或变更。
     */
    void rebuildAllEmbeddings();
}
