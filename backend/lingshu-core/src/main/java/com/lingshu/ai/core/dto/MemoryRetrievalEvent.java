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

    // 阶段一：图谱关联检索（GAM-RAG）命中的事实 ID 列表
    private List<Long> graphMatchedIds;

    // 阶段二：语义向量检索阶段命中的匹配详情（含相似度分数）
    private List<SemanticMatch> semanticMatches;

    // 最终阶段：实际采纳并组装到大模型上下文中的事实 ID 及排序先后顺序
    private List<Long> finalRankedIds;

    // 检索发生的真实时间
    private LocalDateTime timestamp;

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
