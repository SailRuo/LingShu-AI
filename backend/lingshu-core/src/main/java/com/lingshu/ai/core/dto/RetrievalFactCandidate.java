package com.lingshu.ai.core.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalFactCandidate {

    private Long factId;
    private String content;
    private String source;
    private Integer rank;
}
