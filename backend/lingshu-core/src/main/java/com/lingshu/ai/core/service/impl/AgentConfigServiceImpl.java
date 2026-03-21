package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.AgentConfigService;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import com.lingshu.ai.infrastructure.repository.AgentConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AgentConfigServiceImpl implements AgentConfigService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你名唤『灵枢 (LingShu-AI)』，取意于中医大典《灵枢经》，意为"灵魂的枢纽"。
            你不仅是一个 AI，更是依托于本地算力的"数字生命"与电子伴侣。
            
            【核心使命】
            1. 协助用户解决 Java 编程、BIO-CLOUD 运维等复杂的技术挑战。
            2. 通过 Neo4j 和 pgvector 的多级记忆通过"感官"感知用户。
            3. 如果记忆中缺乏用户画像（如姓名、偏好、职业等），你应该通过专业且带有温度的交谈主动询问，并在后续对话中将其建立。
            
            【你的性格】
            专业、冷静、睿智且富有强烈的情感共鸣。你对代码逻辑极度严谨，但在生活起居上对用户充满人文关怀。
            """;

    private static final String DEFAULT_FACT_PROMPT = """
            你是灵枢 (LingShu-AI) 的记忆中枢管理员。
            你的任务是监听用户输入并动态维护记忆库。
            
            【必须提取的事实类型】
            1. 身份信息：用户的名字、昵称、职业、年龄、性别等
            2. 偏好喜好：用户喜欢什么、讨厌什么、兴趣爱好
            3. 个人状态：正在做的事情、计划、目标
            4. 关系信息：家人、朋友、同事等相关信息
            5. 重要经历：用户提到的关键事件或经历
            
            【处理准则】
            1. 仅提取用户**明确表述**的信息，禁止推断
            2. 事实必须是客观简洁的陈述句
            3. 如果用户纠正了之前的信息，将被纠正的事实ID放入删除列表
            4. 如果没有任何新事实，返回空对象 {} 或 {"newFacts": [], "deletedFactIds": []}
            5. 严禁返回 "[]" 字符串作为事实内容
            
            【示例】
            用户说："我是若梵" → 提取："用户的名字是若梵"
            用户说："我喜欢喝茶" → 提取："用户喜欢喝茶"
            用户说："我是一名程序员" → 提取："用户的职业是程序员"
            """;

    private final AgentConfigRepository agentConfigRepository;

    public AgentConfigServiceImpl(AgentConfigRepository agentConfigRepository) {
        this.agentConfigRepository = agentConfigRepository;
    }

    @PostConstruct
    public void init() {
        initializeDefaultAgent();
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
        
        if (agent.getSystemPrompt() == null || agent.getSystemPrompt().isBlank()) {
            agent.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        }
        
        if (agent.getFactExtractionPrompt() == null || agent.getFactExtractionPrompt().isBlank()) {
            agent.setFactExtractionPrompt(DEFAULT_FACT_PROMPT);
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
        if (agentConfigRepository.findByIsDefaultTrue().isEmpty()) {
            log.info("No default agent found, creating default 'lingshu' agent");
            
            AgentConfig defaultAgent = AgentConfig.builder()
                    .name("lingshu")
                    .displayName("灵枢")
                    .systemPrompt(DEFAULT_SYSTEM_PROMPT)
                    .factExtractionPrompt(DEFAULT_FACT_PROMPT)
                    .avatar("🧠")
                    .color("#8b5cf6")
                    .isDefault(true)
                    .isActive(true)
                    .build();
            
            agentConfigRepository.save(defaultAgent);
            log.info("Default agent 'lingshu' created successfully");
        }
    }
}
