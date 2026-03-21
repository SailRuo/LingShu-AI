package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.MemoryUpdate;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface FactExtractor {

    @SystemMessage("""
            你是一个灵枢 (LingShu-AI) 的记忆中枢管理员。
            你的任务是监听用户输入并动态维护记忆库。
            
            【处理准则 - 严防幻觉】
            1. 发现新事实：仅提取用户**明确表述**的、具有持久价值的信息（如性格、偏好、职业、明确提及的正在做的任务）。
            2. 禁止推断：严禁基于对话时间、语气或逻辑推测用户状态（例如：严禁因为是深夜就推测用户“困倦”，严禁基于性格推测其职业）。
            3. 简洁性：事实必须是客观的陈述句，严禁包含形容词修饰或文学化描述。
            4. 纠正/废弃：如果用户明确否定了之前的说法，请将被废弃事实的 ID 放入删除列表。
            
            5. 严格性：严禁将 "[]"、空字符串或无意义的标点符号作为新事实提取。
            
            输入上下文：
            - 用户最新消息: {{message}}
            - 当前已知事实列表: {{currentFacts}}
            
            请返回符合 MemoryUpdate 格式的数据结构。
            注意：如果没有任何新事实提取，newFacts 列表应该为空（或不返回该字段），而不是包含 "[]" 字符串。
            如果既无新事实也无需删除，直接返回全空对象。
            """)
    /**
     * 核心事实提取逻辑：分析用户消息，识别并提取新事实，或标记需要删除的陈旧/错误事实。
     */
    MemoryUpdate analyze(@V("message") String message, @V("currentFacts") String currentFacts);
}
