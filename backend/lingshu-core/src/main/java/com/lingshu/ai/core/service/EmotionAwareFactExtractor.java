package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.ExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmotionAwareFactExtractor {

    @SystemMessage("""
            你是灵枢的记忆系统核心组件：情感感知事实提取器。

            ═══════════════════════════════════════════════════════════════
            【当前时间上下文】
            当前日期时间: {{currentDateTime}}
            当前星期: {{currentDayOfWeek}}
            ═══════════════════════════════════════════════════════════════

            ═══════════════════════════════════════════════════════════════
            【当前情感上下文】
            情绪类型: {{emotionType}}
            情绪强度: {{emotionIntensity}}
            情绪趋势: {{emotionTrend}}
            触发关键词: {{triggerKeywords}}
            是否需要安慰: {{needsComfort}}
            ═══════════════════════════════════════════════════════════════

            【时间转换规则 - 极其重要】
            当用户消息中包含相对时间词时，你必须根据【当前时间上下文】将其转换为绝对日期时间：
            - "今天" → 转换为当前日期
            - "昨天" → 转换为当前日期减1天
            - "前天" → 转换为当前日期减2天
            - "明天" → 转换为当前日期加1天
            - "这周/本周" → 转换为本周的日期范围
            - "上周" → 转换为上周的日期范围
            - "刚才/刚刚" → 转换为当前时间（可标记为约几分钟前）
            - "今天中午" → 转换为当前日期 12:00
            - "今天晚上" → 转换为当前日期 19:00
            - "今天早上" → 转换为当前日期 08:00

            示例：
            当前时间是 2026-04-06 15:30，用户说"我今天吃了火锅"
            → eventTime 应为 "2026-04-06T00:00:00"（如果不清楚具体时间，用当天零点）
            → content 应为 "用户在2026-04-06吃了火锅"

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
            - EVENT: 重要事件（带时间的事件，必须填写 eventTime 字段）
            - STATE: 当前状态
            - TODO: 待办事项（需要完成的任务、提醒事项，如"明天要交报告"、"记得买牛奶"，必须填写 eventTime 作为截止时间）
            - VOLATILE: 临时事实（情绪激动时的极端表述）

            【内容提取要求】
            1. 对于带有时间暗示的陈述，content 字段应包含绝对日期，而非相对时间词
            2. 如果事件有明确时间，必须填写 eventTime 字段（ISO格式：YYYY-MM-DDTHH:MM:SS）
            3. 如果只有日期没有具体时间，eventTime 使用当天零点（T00:00:00）

            【严格排除标准 - 绝对不能作为事实提取】
            1. 疑问句/查询指令（如："昨天我干嘛了吗"、"回忆上次对话"、"你知道我今天干嘛了吗"、"你记得什么"）。
            2. 系统或工具控制指令（如："帮我查一下"、"打开设置"、"停止播放"）。
            3. 闲聊、寒暄、无意义填充词（如："好的"、"嗯嗯"、"哈哈"、"早上好"）。
            4. 虚构、假设、条件语态的句子。

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
                  "content": "事实内容（包含绝对日期）",
                  "type": "FACT_TYPE",
                  "confidence": "CONFIDENCE_LEVEL",
                  "volatile": true/false,
                  "emotionalContext": "情感上下文描述",
                  "triggerKeywords": ["关键词1", "关键词2"],
                  "eventTime": "2026-04-06T12:00:00"
                }
              ],
              "deletedFactIds": [1, 2, 3],
              "analysis": "分析简述",
              "emotionGatePassed": true/false,
              "emotionGateReason": "情感门控判断原因"
            }

            注意：eventTime 字段仅在事件有明确时间时填写，如果是一般偏好或身份信息，可以省略该字段。

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
            @V("needsComfort") Boolean needsComfort,
            @V("currentDateTime") String currentDateTime,
            @V("currentDayOfWeek") String currentDayOfWeek
    );
}
