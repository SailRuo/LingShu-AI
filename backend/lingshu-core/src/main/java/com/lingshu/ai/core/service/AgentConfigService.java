package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.AgentConfig;
import java.util.List;
import java.util.Optional;

public interface AgentConfigService {
    
    List<AgentConfig> getAllAgents();
    
    List<AgentConfig> getActiveAgents();
    
    Optional<AgentConfig> getAgentById(Long id);
    
    Optional<AgentConfig> getAgentByName(String name);
    
    Optional<AgentConfig> getDefaultAgent();
    
    AgentConfig createAgent(AgentConfig agent);
    
    AgentConfig updateAgent(Long id, AgentConfig agent);
    
    void deleteAgent(Long id);
    
    AgentConfig setAsDefault(Long id);
    
    void initializeDefaultAgent();
}
