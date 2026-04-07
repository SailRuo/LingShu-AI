package com.lingshu.ai.core.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpToolArtifactRegistry {

    private final Map<String, List<McpToolArtifact>> artifactsByToolCallId = new ConcurrentHashMap<>();

    public void put(String toolCallId, List<McpToolArtifact> artifacts) {
        if (toolCallId == null || toolCallId.isBlank() || artifacts == null || artifacts.isEmpty()) {
            return;
        }
        artifactsByToolCallId.put(toolCallId, List.copyOf(artifacts));
    }

    public List<McpToolArtifact> pop(String toolCallId) {
        if (toolCallId == null || toolCallId.isBlank()) {
            return List.of();
        }
        List<McpToolArtifact> artifacts = artifactsByToolCallId.remove(toolCallId);
        return artifacts == null ? List.of() : artifacts;
    }
}

