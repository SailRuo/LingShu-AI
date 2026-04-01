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
            5. 重要经历：用户提到的关键事件或经历，如果陈述中包含时间（如“昨天”、“刚才”、“今天中午”），在提取时必须保留时间信息或上下文（例如：“用户昨天吃了炸酱面”）。

            【处理准则】            1. 仅提取用户**明确表述**的陈述句信息，禁止推断或臆测。
            2. 严禁提取：疑问句、寒暄（如你好、早安）、系统指令（如“查一下”、“播放音乐”）、探查记忆的提问（如“你记得我昨天说了什么吗”）。
            3. 如果用户纠正了之前的信息，将被纠正的事实ID放入删除列表。
            4. **新增 analysis 字段**：无论是否提取到新事实，都要简要说明分析结论（如：“提取到用户新爱好：打篮球” 或 “用户正在提问，未发现符合标准的事实信息”）。

            【返回格式限制】
            你必须且只能返回合法的 JSON 对象，严禁包含任何 Markdown 格式（如 ```json）或解释性文本。
            格式要求：
            {
              "newFacts": ["提取的事实1", "提取的事实2"],
              "deletedFactIds": [1, 2, 3],
              "analysis": "分析简述"
            }

            【当前已知事实列表】
            {{currentFacts}}
            """)
    @UserMessage("请分析该消息并直接返回 JSON 结果：{{message}}")
    String analyze(@V("message") String message, @V("currentFacts") String currentFacts);
}
