package com.lingshu.ai.core.dto;

import lombok.Data;
import java.util.List;

@Data
public class MemoryUpdate {
    private List<String> newFacts;
    private List<Long> deletedFactIds;
}
