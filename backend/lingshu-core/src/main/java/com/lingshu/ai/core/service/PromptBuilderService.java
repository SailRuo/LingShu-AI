package com.lingshu.ai.core.service;

import com.lingshu.ai.infrastructure.entity.AgentConfig;

public interface PromptBuilderService {
    
    String buildSystemPrompt(AgentConfig config);
    
    String buildFullPrompt(AgentConfig config, String relationshipPrompt, String longTermContext, String shortTermContext, String message);
    
    String buildWelcomePrompt(AgentConfig config, String relationshipPrompt, String historyContext);
    
    String buildUserPrompt(String relationshipPrompt, String longTermContext, String shortTermContext, String message);
    
    String buildWelcomeUserPrompt(String relationshipPrompt, String historyContext, String agentName);
    
    String buildGreetingUserPrompt(String relationshipPrompt, String memoryContext, String timeOfDay, String agentName);
    
    String buildComfortUserPrompt(String relationshipPrompt, String emotion, double intensity, String agentName);
}
