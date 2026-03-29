package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.PromptBuilderService;
import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PromptBuilderServiceImpl implements PromptBuilderService {

    private final SettingService settingService;

    public PromptBuilderServiceImpl(SettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    public String buildSystemPrompt(AgentConfig config) {
        if (config == null) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();

        String systemPrompt = Optional.ofNullable(config.getSystemPrompt()).orElse("");
        if (StringUtils.hasText(systemPrompt)) {
            prompt.append(systemPrompt);
        }

        appendSection(prompt, "行为原则", config.getBehaviorPrinciples());
        appendSection(prompt, "自主决策机制", config.getDecisionMechanism());
        
        // 动态生成工具调用规则
        String dynamicToolRules = buildDynamicToolRules();
        if (StringUtils.hasText(dynamicToolRules)) {
            appendSection(prompt, "工具调用规则", dynamicToolRules);
        } else {
            appendSection(prompt, "工具调用规则", config.getToolCallRules());
        }
        
        appendSection(prompt, "情感陪伴策略", config.getEmotionalStrategy());
        appendSection(prompt, "主动问候机制", config.getGreetingTriggers());
        appendSection(prompt, "隐性规则", config.getHiddenRules());

        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private String buildDynamicToolRules() {
        try {
            SystemSetting localToolsSetting = settingService.getLocalToolsSetting();
            if (localToolsSetting == null || localToolsSetting.getSettings() == null) {
                return null;
            }

            List<Map<String, Object>> tools = (List<Map<String, Object>>) localToolsSetting.getSettings().get("tools");
            if (tools == null || tools.isEmpty()) {
                return null;
            }

            StringBuilder rules = new StringBuilder("工具是你感知与操作世界的\"延伸\"（Senses & Limbs）：\n");
            boolean hasEnabledTools = false;

            for (Map<String, Object> tool : tools) {
                Boolean enabled = (Boolean) tool.get("enabled");
                if (Boolean.TRUE.equals(enabled)) {
                    hasEnabledTools = true;
                    String name = (String) tool.get("name");
                    String prompt = (String) tool.get("prompt");
                    rules.append("- ").append(name).append("：").append(prompt).append("\n");
                }
            }

            if (!hasEnabledTools) {
                return null;
            }

            rules.append("- 交互规范：调用前需拟人化说明意图（如：\"等我检索一下我们的过往记录...\"）。");
            return rules.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String buildMergedSystemPrompt(com.lingshu.ai.infrastructure.entity.AgentConfig config, String relationshipPrompt, String longTermContext) {
        String baseSystem = buildSystemPrompt(config);

        StringBuilder contextBuilder = new StringBuilder();
        if (StringUtils.hasText(relationshipPrompt)) {
            contextBuilder.append("\n\n# 当前关系状态\n").append(relationshipPrompt);
        }

        if (StringUtils.hasText(longTermContext)) {
            contextBuilder.append("\n\n# 感官记忆 (长期事实)\n").append(longTermContext);
        }

        return String.format("""
                %s

                %s

                指令回复准则：
                - 当用户询问关于自己的信息（如喜好、身份、习惯等）时，优先从【感官记忆】中查找并回答。
                - 当用户显式要求"回忆"或"记得"时，引用记忆并说明来源。
                - 只有当记忆中确实没有相关信息时，才回答"之前的记忆有些模糊，能提醒我一下吗？"
                - 不要虚构任何用户信息。
                - 根据关系状态调整语气和亲密程度。


                """,
                baseSystem,
                contextBuilder.toString());
    }

    @Override
    public String buildFullPrompt(AgentConfig config, String relationshipPrompt,
                                   String longTermContext, String shortTermContext, String message) {
        // Fallback or legacy, keep if still needed by other parts, but use merged system prompt internally
        return String.format("""
                %s

                # 近期对话记录
                %s

                # 用户原始消息
                %s
                """,
                buildMergedSystemPrompt(config, relationshipPrompt, longTermContext),
                shortTermContext != null ? shortTermContext : "",
                message);
    }

    @Override
    public String buildWelcomePrompt(AgentConfig config, String relationshipPrompt, String historyContext) {
        String systemPrompt = buildSystemPrompt(config);
        String agentName = config != null && config.getDisplayName() != null ? config.getDisplayName() : "灵枢";

        return String.format("""
                %s

                %s

                【历史对话记录】
                %s

                请作为『%s』，基于以上对话和关系状态动态生成一句情感共识强烈的中文欢迎语。如果对话还没开启，请作为新伙伴询问用户的身份。
                """,
                systemPrompt,
                relationshipPrompt != null ? relationshipPrompt : "",
                historyContext != null ? historyContext : "",
                agentName);
    }

    @Override
    public String buildUserPrompt(String relationshipPrompt, String longTermContext, String message) {
        // 返回干净的用户原始消息，防止提示词泄露
        return message;
    }

    @Override
    public String buildWelcomeUserPrompt(String relationshipPrompt, String agentName) {
        // 返回极简指令，逻辑已移至 System Prompt
        // 增加自我介绍的要求，结合系统设定中的生命特征
        return String.format("""
                请开始我们的对话。

                任务：
                1. 请作为『%s』，结合你的系统设定（性格、背景、行为原则），向我展示一段充满生命气息的自我介绍。
                2. 如果是新朋友，请友好地询问我的身份，开启我们的邂逅。
                3. 根据当前的【关系状态】调整你的语气。
                """, agentName);
    }

    @Override
    public String buildGreetingUserPrompt(String timeOfDay) {
        // 关键逻辑已移至 System Prompt，这里只保留触发词
        return String.format("当前时间是 %s。请给我一个自然的问候。", timeOfDay);
    }

    @Override
    public String buildComfortUserPrompt(String emotion, double intensity) {
        // 关键逻辑已移至 System Prompt
        return String.format("探测到我的情绪状态为 %s (强度 %.2f)，请安慰我。", emotion, intensity);
    }

    private void appendSection(StringBuilder builder, String title, String content) {
        if (StringUtils.hasText(content)) {
            builder.append("\n\n# ").append(title).append("\n").append(content);
        }
    }
}
