package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.AgentConfigService;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import com.lingshu.ai.infrastructure.repository.AgentConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AgentConfigServiceImpl implements AgentConfigService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentConfigServiceImpl.class);

    private final AgentConfigRepository agentConfigRepository;

    public AgentConfigServiceImpl(AgentConfigRepository agentConfigRepository) {
        this.agentConfigRepository = agentConfigRepository;
    }

    @PostConstruct
    public void init() {
        if (agentConfigRepository.findByIsDefaultTrue().isEmpty()) {
            log.warn("No default agent found in database. Please configure an agent via the management interface.");
        }
    }

    @Override
    public List<AgentConfig> getAllAgents() {
        return agentConfigRepository.findAllByOrderByIsDefaultDescCreatedAtDesc();
    }

    @Override
    public List<AgentConfig> getActiveAgents() {
        return agentConfigRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    @Override
    public Optional<AgentConfig> getAgentById(Long id) {
        return agentConfigRepository.findById(id);
    }

    @Override
    public Optional<AgentConfig> getAgentByName(String name) {
        return agentConfigRepository.findByName(name);
    }

    @Override
    public Optional<AgentConfig> getDefaultAgent() {
        return agentConfigRepository.findByIsDefaultTrue();
    }

    @Override
    @Transactional("transactionManager")
    public AgentConfig createAgent(AgentConfig agent) {
        if (agent.getName() == null || agent.getName().isBlank()) {
            throw new IllegalArgumentException("Agent name is required");
        }
        
        if (agentConfigRepository.findByName(agent.getName()).isPresent()) {
            throw new IllegalArgumentException("Agent with name '" + agent.getName() + "' already exists");
        }
        
        if (agent.getDisplayName() == null || agent.getDisplayName().isBlank()) {
            agent.setDisplayName(agent.getName());
        }
        
        return agentConfigRepository.save(agent);
    }

    @Override
    @Transactional("transactionManager")
    public AgentConfig updateAgent(Long id, AgentConfig agent) {
        AgentConfig existing = agentConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with id: " + id));
        
        if (agent.getName() != null && !agent.getName().equals(existing.getName())) {
            if (agentConfigRepository.findByName(agent.getName()).isPresent()) {
                throw new IllegalArgumentException("Agent with name '" + agent.getName() + "' already exists");
            }
            existing.setName(agent.getName());
        }
        
        if (agent.getDisplayName() != null) existing.setDisplayName(agent.getDisplayName());
        if (agent.getSystemPrompt() != null) existing.setSystemPrompt(agent.getSystemPrompt());
        if (agent.getFactExtractionPrompt() != null) existing.setFactExtractionPrompt(agent.getFactExtractionPrompt());
        if (agent.getBehaviorPrinciples() != null) existing.setBehaviorPrinciples(agent.getBehaviorPrinciples());
        if (agent.getDecisionMechanism() != null) existing.setDecisionMechanism(agent.getDecisionMechanism());
        if (agent.getToolCallRules() != null) existing.setToolCallRules(agent.getToolCallRules());
        if (agent.getEmotionalStrategy() != null) existing.setEmotionalStrategy(agent.getEmotionalStrategy());
        if (agent.getGreetingTriggers() != null) existing.setGreetingTriggers(agent.getGreetingTriggers());
        if (agent.getHiddenRules() != null) existing.setHiddenRules(agent.getHiddenRules());
        if (agent.getAvatar() != null) existing.setAvatar(agent.getAvatar());
        if (agent.getColor() != null) existing.setColor(agent.getColor());
        if (agent.getIsActive() != null) existing.setIsActive(agent.getIsActive());
        
        return agentConfigRepository.save(existing);
    }

    @Override
    @Transactional("transactionManager")
    public void deleteAgent(Long id) {
        AgentConfig agent = agentConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with id: " + id));
        
        if (agent.getIsDefault()) {
            throw new IllegalArgumentException("Cannot delete the default agent");
        }
        
        agentConfigRepository.deleteById(id);
    }

    @Override
    @Transactional("transactionManager")
    public AgentConfig setAsDefault(Long id) {
        AgentConfig newDefault = agentConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with id: " + id));
        
        agentConfigRepository.findByIsDefaultTrue().ifPresent(oldDefault -> {
            oldDefault.setIsDefault(false);
            agentConfigRepository.save(oldDefault);
        });
        
        newDefault.setIsDefault(true);
        return agentConfigRepository.save(newDefault);
    }

    @Override
    @Transactional("transactionManager")
    public void initializeDefaultAgent() {
        // Initialization logic removed as per user request to avoid hardcoded prompts.
        // Defaults should be managed via database or external configuration.
    }

    @Override
    public AgentConfig getDefaultAgentConfig() {
        return AgentConfig.builder()
                .isActive(true)
                .build();
    }
}
