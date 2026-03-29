package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.EmotionalEpisodeResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmotionalEpisodeExtractor {

    @SystemMessage("""
            你是灵枢的情感片段提取模块。你的任务是从用户消息中提取完整的情感事件。
            
            【情感片段定义】
            情感片段是一个带有情感上下文的记忆单元，包含：
            1. 触发事件: 引发情绪的具体事件或情境
            2. 情绪类型: positive/negative/neutral
            3. 情绪强度: 0.0-1.0
            4. 触发关键词: 引发情绪的关键词
            5. 用户反应: 用户对事件的行为或心理反应
            6. 应对机制: 用户采取的应对方式（如果有）
            7. 结果情绪: 事件后的情绪状态变化
            
            【提取条件】
            只有在以下情况才提取情感片段：
            1. 用户明确表达了情绪体验
            2. 情绪强度 >= 0.5
            3. 存在明确的触发事件
            4. 用户需要安慰 = true
            
            【不需要提取的情况】
            1. 简单的事实陈述
            2. 情绪强度 < 0.5
            3. 没有明确的触发事件
            4. 一般性的偏好表达
            
            【返回格式】
            你必须且只能返回合法的 JSON 对象，严禁包含任何 Markdown 格式。
            {
                "shouldExtract": true/false,
                "reason": "判断原因",
                "episode": {
                    "triggerEvent": "触发事件描述",
                    "emotionType": "positive/negative/neutral",
                    "emotionIntensity": 0.0-1.0,
                    "triggerKeywords": ["关键词1", "关键词2"],
                    "userResponse": "用户反应描述",
                    "copingMechanism": "应对机制描述",
                    "outcomeEmotion": "结果情绪类型",
                    "outcomeIntensity": 0.0-1.0,
                    "contextSummary": "情境摘要"
                }
            }
            
            如果 shouldExtract 为 false，episode 字段可以为 null。
            """)
    @UserMessage("""
            用户消息: {{message}}
            
            当前情感状态:
            - 情绪类型: {{emotionType}}
            - 情绪强度: {{emotionIntensity}}
            - 触发关键词: {{triggerKeywords}}
            - 是否需要安慰: {{needsComfort}}
            
            请分析是否需要提取情感片段并返回 JSON。
            """)
    EmotionalEpisodeResult extract(
            @V("message") String message,
            @V("emotionType") String emotionType,
            @V("emotionIntensity") Double emotionIntensity,
            @V("triggerKeywords") String triggerKeywords,
            @V("needsComfort") Boolean needsComfort
    );
}
