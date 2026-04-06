package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.McpServerConfig;
import com.lingshu.ai.core.tool.RawMcpClient;

import java.util.List;

public interface McpService {
    List<McpServerConfig> getAllConfigs();
    McpServerConfig saveConfig(McpServerConfig config);
    boolean deleteConfig(Long id);
    McpServerConfig toggleConfig(Long id);
    
    /**
     * Get all active MCP clients
     */
    List<RawMcpClient> getActiveClients();
    
    /**
     * Get discovered tools from an active MCP client
     * @param id MCP configuration ID
     * @return List of tool metadata maps (name, description, etc.)
     */
    java.util.List<java.util.Map<String, Object>> getToolDetails(Long id);

    /**
     * Import MCP configurations from standard JSON format
     * @param json JSON string containing mcpServers object
     */
    void importFromJson(String json);
}
