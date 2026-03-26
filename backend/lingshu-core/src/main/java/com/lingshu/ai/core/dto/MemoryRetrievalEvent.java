package com.lingshu.ai.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRetrievalEvent {

    // 当前检索的原始 Query
    private String query;

    // 从 Query 中提取的激活实体/关键词
    private List<String> extractedEntities;

    // 图谱阶段：通过实体关键词激活的节点 ID 列表
    private List<Long> graphMatchedIds;

    // 图谱阶段：激活的节点具体的记忆文本内容列表
    private List<String> graphMatchedContent;

    // 阶段二：语义向量检索阶段命中的匹配详情（含相似度分数）
    private List<SemanticMatch> semanticMatches;

    // 最终阶段：实际采纳并组装到大模型上下文中的事实 ID 及排序先后顺序
    private List<Long> finalRankedIds;

    // 最终阶段：实际采纳并组装应用的事实具体内容列表
    private List<String> finalRankedContent;

    // 检索发生的真实时间
    private LocalDateTime timestamp;

    // 从用户节点直接获取的基础事实文本列表（不经过 GAM-RAG 激活，直接从 Neo4j 用户节点读取）
    private List<String> baseFactContents;

    // 标记是否通过 fallback 路径（如身份查询）直接使用了图谱基础事实
    private Boolean fallbackActivated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemanticMatch {
        private Long factId;
        private double score;
        private String contentSnippet;
    }
}
