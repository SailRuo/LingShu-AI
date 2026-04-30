package com.lingshu.ai.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalFactCandidate {

    private Long factId;
    private String content;
    private String source;
    private Integer rank;

    public Long getFactId() {
        return factId;
    }

    public void setFactId(Long factId) {
        this.factId = factId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public static RetrievalFactCandidateBuilder builder() {
        return new RetrievalFactCandidateBuilder();
    }

    public static class RetrievalFactCandidateBuilder {
        private Long factId;
        private String content;
        private String source;
        private Integer rank;

        public RetrievalFactCandidateBuilder factId(Long factId) {
            this.factId = factId;
            return this;
        }

        public RetrievalFactCandidateBuilder content(String content) {
            this.content = content;
            return this;
        }

        public RetrievalFactCandidateBuilder source(String source) {
            this.source = source;
            return this;
        }

        public RetrievalFactCandidateBuilder rank(Integer rank) {
            this.rank = rank;
            return this;
        }

        public RetrievalFactCandidate build() {
            return new RetrievalFactCandidate(factId, content, source, rank);
        }
    }
}
