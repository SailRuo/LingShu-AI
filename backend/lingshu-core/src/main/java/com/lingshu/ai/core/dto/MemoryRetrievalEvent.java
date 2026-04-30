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

    // 当前检索所属的用户ID
    private String userId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // 当前检索的原始 Query
    private String query;

    public String getQuery() {
        return query;
    }

    // 从 Query 中提取的激活实体/关键词
    private List<String> extractedEntities;

    // 图谱阶段：通过实体关键词激活的节点 ID 列表
    private List<Long> graphMatchedIds;

    // 图谱阶段：激活的节点具体的记忆文本内容列表
    private List<String> graphMatchedContent;

    private List<Long> adoptedFactIds;

    public List<Long> getAdoptedFactIds() {
        return adoptedFactIds;
    }

    // 阶段二：语义向量检索阶段命中的匹配详情（含相似度分数）
    private List<SemanticMatch> semanticMatches;

    // 最终阶段：实际采纳并组装到大模型上下文中的事实 ID 及排序先后顺序
    private List<Long> finalRankedIds;

    // 最终阶段：实际采纳并组装应用的事实具体内容列表
    private List<String> finalRankedContent;

    // 检索发生的真实时间
    private LocalDateTime timestamp;

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // 从用户节点直接获取的基础事实文本列表（不经过 GAM-RAG 激活，直接从 Neo4j 用户节点读取）
    private List<String> baseFactContents;

    // 标记是否通过 fallback 路径（如身份查询）直接使用了图谱基础事实
    private Boolean fallbackActivated;

    // 路由决策结果，例如 "GRAPH_ONLY", "VECTOR_BACKUP", "GRAPH_PRIORITIZED_VECTOR_SUPPLEMENT"
    private String routingDecision;

    // 计算得到的图谱增益值
    private Double gain;

    public static MemoryRetrievalEventBuilder builder() {
        return new MemoryRetrievalEventBuilder();
    }

    public static class MemoryRetrievalEventBuilder {
        private String userId;
        private String query;
        private List<String> extractedEntities;
        private List<Long> graphMatchedIds;
        private List<String> graphMatchedContent;
        private List<Long> adoptedFactIds;
        private List<SemanticMatch> semanticMatches;
        private List<Long> finalRankedIds;
        private List<String> finalRankedContent;
        private LocalDateTime timestamp;
        private List<String> baseFactContents;
        private Boolean fallbackActivated;
        private String routingDecision;
        private Double gain;

        public MemoryRetrievalEventBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public MemoryRetrievalEventBuilder query(String query) {
            this.query = query;
            return this;
        }

        public MemoryRetrievalEventBuilder extractedEntities(List<String> extractedEntities) {
            this.extractedEntities = extractedEntities;
            return this;
        }

        public MemoryRetrievalEventBuilder graphMatchedIds(List<Long> graphMatchedIds) {
            this.graphMatchedIds = graphMatchedIds;
            return this;
        }

        public MemoryRetrievalEventBuilder graphMatchedContent(List<String> graphMatchedContent) {
            this.graphMatchedContent = graphMatchedContent;
            return this;
        }

        public MemoryRetrievalEventBuilder adoptedFactIds(List<Long> adoptedFactIds) {
            this.adoptedFactIds = adoptedFactIds;
            return this;
        }

        public MemoryRetrievalEventBuilder semanticMatches(List<SemanticMatch> semanticMatches) {
            this.semanticMatches = semanticMatches;
            return this;
        }

        public MemoryRetrievalEventBuilder finalRankedIds(List<Long> finalRankedIds) {
            this.finalRankedIds = finalRankedIds;
            return this;
        }

        public MemoryRetrievalEventBuilder finalRankedContent(List<String> finalRankedContent) {
            this.finalRankedContent = finalRankedContent;
            return this;
        }

        public MemoryRetrievalEventBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MemoryRetrievalEventBuilder baseFactContents(List<String> baseFactContents) {
            this.baseFactContents = baseFactContents;
            return this;
        }

        public MemoryRetrievalEventBuilder fallbackActivated(Boolean fallbackActivated) {
            this.fallbackActivated = fallbackActivated;
            return this;
        }

        public MemoryRetrievalEventBuilder routingDecision(String routingDecision) {
            this.routingDecision = routingDecision;
            return this;
        }

        public MemoryRetrievalEventBuilder gain(Double gain) {
            this.gain = gain;
            return this;
        }

        public MemoryRetrievalEvent build() {
            MemoryRetrievalEvent event = new MemoryRetrievalEvent();
            event.setUserId(userId);
            event.setQuery(query);
            event.setExtractedEntities(extractedEntities);
            event.setGraphMatchedIds(graphMatchedIds);
            event.setGraphMatchedContent(graphMatchedContent);
            event.setAdoptedFactIds(adoptedFactIds);
            event.setSemanticMatches(semanticMatches);
            event.setFinalRankedIds(finalRankedIds);
            event.setFinalRankedContent(finalRankedContent);
            event.setTimestamp(timestamp);
            event.setBaseFactContents(baseFactContents);
            event.setFallbackActivated(fallbackActivated);
            event.setRoutingDecision(routingDecision);
            event.setGain(gain);
            return event;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class SemanticMatch {
        private Long factId;
        private double score;
        private String contentSnippet;

        public SemanticMatch(Long factId, double score, String contentSnippet) {
            this.factId = factId;
            this.score = score;
            this.contentSnippet = contentSnippet;
        }
    }
}
