package com.lingshu.ai.core.service;

public interface MemoryService {
    
    /**
     * Extracts facts from a user message and saves them to the graph.
     */
    void extractFacts(String userId, String message);

    /**
     * Retrieves relevant context from memory for the current conversation.
     */
    String retrieveContext(String userId, String message);

    /**
     * Retrieves the entire memory graph for visualization.
     */
    Object getGraphData(String userId);

    /**
     * Deletes a fact by its ID and cleans up associated semantic memory.
     */
    void deleteFact(Long factId);
}
