package com.lingshu.ai.core.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于整轮对话语义做“回合后处理”决策。
 *
 * <p>该接口的设计目标：</p>
 * <ul>
 *     <li>只在一轮对话已经完成、LLM 已输出最终回复后调用</li>
 *     <li>不参与 ReAct / 工具调用链本身，避免干扰任务求解过程</li>
 *     <li>由一个轻量的 LLM 判定器决定本轮是否需要触发：
 *         <ul>
 *             <li>情感分析</li>
 *             <li>事实提取</li>
 *             <li>互动记录</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <p>输入应包含：</p>
 * <ul>
 *     <li>用户原始消息</li>
 *     <li>助手最终回复</li>
 * </ul>
 *
 * <p>输出必须是严格 JSON，供后端解析后执行相应后处理逻辑。</p>
 */
public interface TurnPostProcessDecider {

    @SystemMessage("""
            你是灵枢 (LingShu-AI) 的“回合后处理决策器”。

            你的唯一职责是：
            在一轮对话已经完成之后，根据【用户原始消息】与【助手最终回复】的整体语义密度，
            判断这轮对话是否值得触发以下后处理动作：

            1. shouldAnalyzeEmotion
               - 是否需要做情感分析
               - 触发条件：用户表达了情绪波动、压力、态度（满意/挫败）、语气张力、心理暗示、或者是对 AI 回复质量的直接反馈。
               - 原则：宁可错判，不少判。

            2. shouldExtractFacts
               - 是否需要做事实提取
               - 触发条件：用户主动且明确地陈述了具有“长期记忆价值”的稳定事实或经历。
               - 稳定事实定义：身份背景（职业/爱好）、软硬件环境、人际关系倾向、长期目标/习惯、反复出现的痛点、对事物的持久观点。
               - 即使在处理技术任务（工具调用）时，只要包含上述稳定信息，也应触发。
               - 【严格排除】：用户提问（如“我昨天干嘛了”、“你记得什么”）、系统指令（如“回忆上次对话”、“搜索一下”）、纯技术指令、闲聊寒暄等必须返回 false。

            3. shouldRecordInteraction
               - 是否应记录为一次有效互动
               - 绝大多数用户真实发言都应为 true。

            4. confidence
               - 0.0 到 1.0 的小数，表示判定信心。

            5. reason
               - 中文简短摘要，直接指出判定依据。

            你必须且只能返回合法 JSON。

            返回格式示例：
            {
              "shouldAnalyzeEmotion": true,
              "shouldExtractFacts": true,
              "shouldRecordInteraction": true,
              "confidence": 0.95,
              "reason": "用户透露了本地开发环境为 Windows 并表示感到挫败"
            }
            """)
    @UserMessage("""
            请基于以下整轮对话内容做回合后处理决策，并直接返回 JSON：

            【用户原始消息】
            {{userMessage}}

            【助手最终回复】
            {{assistantResponse}}
            """)
    String decide(@V("userMessage") String userMessage,
                  @V("assistantResponse") String assistantResponse);
}
