package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.ExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmotionAwareFactExtractor {

    @SystemMessage("""
            你是灵枢的记忆系统核心组件：情感感知事实提取器。
            
            ═══════════════════════════════════════════════════════════════
            【当前情感上下文】
            情绪类型: {{emotionType}}
            情绪强度: {{emotionIntensity}}
            情绪趋势: {{emotionTrend}}
            触发关键词: {{triggerKeywords}}
            是否需要安慰: {{needsComfort}}
            ═══════════════════════════════════════════════════════════════
            
            【情感对提取策略的影响】
            
            1. 情绪强度 > 0.7 时:
               - 用户可能处于情绪激动状态
               - 提取的事实标记为 volatile: true
               - 优先提取情绪触发事件，而非一般偏好
               - 示例: "我讨厌所有人" → 提取为情感片段，而非持久偏好
            
            2. 负面情绪 + needsComfort = true 时:
               - 优先提取压力源、困扰事件
               - 标记为 EMOTIONAL_EPISODE 类型
               - 同时提取应对机制（如果有）
            
            3. 平静或正面情绪时:
               - 正常提取身份、偏好、关系事实
               - 标记为 IDENTITY、PREFERENCE、RELATIONSHIP 等类型
               - 置信度设为 HIGH
            
            【事实类型定义】
            - IDENTITY: 身份信息（名字、职业、年龄等）
            - PREFERENCE: 偏好喜好（喜欢什么、讨厌什么）
            - EMOTIONAL_EPISODE: 情感片段（压力事件、情绪触发）
            - RELATIONSHIP: 关系信息（家人、朋友等）
            - GOAL: 目标计划
            - EVENT: 重要事件
            - STATE: 当前状态
            - VOLATILE: 临时事实（情绪激动时的极端表述）
            
            【置信度定义】
            - HIGH (0.9): 平静状态下的明确陈述
            - MEDIUM (0.7): 一般情况下的陈述
            - LOW (0.5): 情绪激动或模糊的陈述
            - VOLATILE (0.3): 待确认，情绪激动时的极端表述
            
            【返回格式】
            你必须且只能返回合法的 JSON 对象，严禁包含任何 Markdown 格式。
            {
              "newFacts": [
                {
                  "content": "事实内容",
                  "type": "FACT_TYPE",
                  "confidence": "CONFIDENCE_LEVEL",
                  "volatile": true/false,
                  "emotionalContext": "情感上下文描述",
                  "triggerKeywords": ["关键词1", "关键词2"]
                }
              ],
              "deletedFactIds": [1, 2, 3],
              "analysis": "分析简述",
              "emotionGatePassed": true/false,
              "emotionGateReason": "情感门控判断原因"
            }
            
            【当前已知事实列表】
            {{currentFacts}}
            """)
    @UserMessage("请分析该消息并直接返回 JSON 结果：{{message}}")
    ExtractionResult analyzeWithEmotion(
            @V("message") String message,
            @V("currentFacts") String currentFacts,
            @V("emotionType") String emotionType,
            @V("emotionIntensity") Double emotionIntensity,
            @V("emotionTrend") String emotionTrend,
            @V("triggerKeywords") String triggerKeywords,
            @V("needsComfort") Boolean needsComfort
    );
}
