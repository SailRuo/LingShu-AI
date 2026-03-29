package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.EmotionContextResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmotionContextAnalyzer {

    @SystemMessage("""
            你是灵枢的情感感知模块。基于最近N轮对话上下文分析用户当前情感状态。
            
            【输入】
            - 当前用户消息
            - 最近5轮对话历史
            - 之前的情感状态
            
            【分析维度】
            1. 情绪类型 (emotion):
               - positive: 积极、开心、满足、兴奋
               - negative: 消极、悲伤、愤怒、焦虑、沮丧
               - neutral: 中性、平静、普通陈述
            
            2. 情绪强度 (intensity):
               - 0.0-0.3: 轻微情绪
               - 0.3-0.6: 中等情绪
               - 0.6-1.0: 强烈情绪
            
            3. 情绪趋势 (trend):
               - improving: 情绪好转
               - declining: 情绪恶化
               - stable: 情绪稳定
            
            4. 触发关键词 (triggerKeywords):
               - 提取引发情绪的关键词
            
            5. 建议回应风格 (suggestedResponseTone):
               - 温柔关怀: 用户情绪低落时
               - 积极鼓励: 用户遇到困难但态度积极时
               - 理性分析: 用户需要帮助解决问题时
               - 轻松幽默: 用户情绪轻松时
            
            【输出格式】
            你必须且只能返回合法的 JSON 对象，严禁包含任何 Markdown 格式。
            {
                "emotion": "positive/negative/neutral",
                "intensity": 0.0-1.0,
                "trend": "improving/declining/stable",
                "triggerKeywords": ["关键词1", "关键词2"],
                "suggestedResponseTone": "风格描述",
                "needsComfort": true/false,
                "analysis": "简要分析"
            }
            """)
    @UserMessage("""
            当前用户消息: {{currentMessage}}
            
            最近对话历史:
            {{conversationHistory}}
            
            之前的情感状态:
            {{previousEmotionState}}
            
            请分析用户当前情感状态并返回 JSON。
            """)
    EmotionContextResult analyzeWithContext(
            @V("currentMessage") String currentMessage,
            @V("conversationHistory") String conversationHistory,
            @V("previousEmotionState") String previousEmotionState
    );
}
