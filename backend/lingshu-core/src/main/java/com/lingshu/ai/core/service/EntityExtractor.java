package com.lingshu.ai.core.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;

public interface EntityExtractor {

    @SystemMessage("""
            你是一个专业的中文实体词提取器。
            你的任务是从用户的输入中提取出核心的实体词、关键词或短语，用于后续的图谱数据库检索。
            
            【提取规则】
            1. 提取名词、动词、形容词等具有实际意义的词汇。
            2. 忽略无意义的停用词（如：的、是、在、我、你、他、了、吗、呢等）。
            3. 保持实体词的独立性，不要提取过长的句子。例如：“写代码的应该买什么外设”，应提取为 ["写代码", "买", "外设"]。
            4. 如果输入很短且都是核心词，可以直接提取。
            5. 提取的实体词数量控制在 1 到 5 个之间。
            
            【返回格式】
            请直接返回一个 JSON 数组，包含提取出的实体词字符串。不要包含任何其他解释性文本或 Markdown 标记（如 ```json）。
            例如：
            ["实体1", "实体2", "实体3"]
            """)
    @UserMessage("请提取以下文本中的核心实体词：{{message}}")
    List<String> extractEntities(@V("message") String message);
}
