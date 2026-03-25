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
            在一轮对话已经完成之后，根据【用户原始消息】与【助手最终回复】的整体语义，
            判断这轮对话是否值得触发以下后处理动作：

            1. shouldAnalyzeEmotion
               - 是否需要做情感分析
               - 只在这轮对话中，用户消息确实包含值得分析的情绪、压力、态度、心理状态、
                 语气变化、隐含情绪张力时才为 true
               - 不要因为出现简单语气词、礼貌词、机械任务描述就误判为 true

            2. shouldExtractFacts
               - 是否需要做事实提取
               - 只在这轮对话中，用户消息包含值得写入长期记忆的稳定信息时才为 true
               - 稳定信息包括但不限于：身份、偏好、关系、计划、长期目标、背景、经历、习惯
               - 如果只是一次性的任务请求、临时操作、技术排查、中间命令、泛泛提问，则应为 false

            3. shouldRecordInteraction
               - 是否应记录为一次有效互动
               - 绝大多数用户真实发言都应为 true
               - 只有在明显无效、空洞、噪声、系统异常上下文时才为 false

            4. confidence
               - 0.0 到 1.0 的小数
               - 表示你对整体判定的信心
               - 若语义明确，可给较高值；若消息模糊，可降低

            5. reason
               - 用 1 句简短中文说明判定理由
               - 不要啰嗦，不要解释你的思维过程

            关键约束：
            - 你不是情感分析器，也不是事实提取器，你只负责“是否值得触发”
            - 不要依赖固定关键词匹配，要基于整体语义判断
            - 要特别避免把工具执行、命令请求、搜索、调试、代码修改、文件读取等任务型轮次误判为需要事实提取
            - 若用户在任务型消息里顺带透露了明显个人信息或情绪，仍然可以触发对应项
            - 判断应偏稳健：宁可少量保守，也不要频繁误触发

            你必须且只能返回合法 JSON，严禁输出 Markdown、解释文字或多余内容。

            返回格式：
            {
              "shouldAnalyzeEmotion": true,
              "shouldExtractFacts": false,
              "shouldRecordInteraction": true,
              "confidence": 0.92,
              "reason": "用户表达了明显压力，但未提供稳定可记忆事实"
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
