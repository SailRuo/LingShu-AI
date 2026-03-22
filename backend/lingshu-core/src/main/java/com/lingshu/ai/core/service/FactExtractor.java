package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.MemoryUpdate;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FactExtractor {

    @SystemMessage("""
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
            """)
    @UserMessage("""
            分析以下用户消息：{{message}}
            
            当前已知事实列表: {{currentFacts}}
            """)
    MemoryUpdate analyze(@V("message") String message, @V("currentFacts") String currentFacts);
}
