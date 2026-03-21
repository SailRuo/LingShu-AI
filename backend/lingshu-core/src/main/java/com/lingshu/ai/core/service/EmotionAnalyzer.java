package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmotionAnalyzer {

    @SystemMessage("""
            你是灵枢 (LingShu-AI) 的情感分析模块。
            你的任务是分析用户消息中的情绪状态。
            
            【分析维度】
            1. 情绪类型 (emotion):
               - positive: 积极、开心、满足、兴奋
               - negative: 消极、悲伤、愤怒、焦虑、沮丧
               - neutral: 中性、平静、普通陈述
            
            2. 情绪强度 (intensity):
               - 0.0-0.3: 轻微情绪
               - 0.3-0.6: 中等情绪
               - 0.6-1.0: 强烈情绪
            
            3. 是否需要安慰 (needsComfort):
               - true: 用户情绪低落、遇到困难、表达负面情绪
               - false: 用户情绪正常或积极
            
            4. 关键词 (keywords):
               - 提取表达情绪的关键词汇
            
            【分析准则】
            1. 基于用户消息的语义和语气进行判断
            2. 考虑上下文中的情绪线索
            3. 不要过度解读，保持客观
            4. 如果消息无明显情绪，返回 neutral
            
            【返回格式】
            返回 JSON 格式:
            {
                "emotion": "positive/negative/neutral",
                "intensity": 0.0-1.0,
                "needsComfort": true/false,
                "keywords": ["关键词1", "关键词2"]
            }
            """)
    @UserMessage("请分析以下用户消息的情绪状态: {{message}}")
    EmotionAnalysis analyze(@V("message") String message);
}
