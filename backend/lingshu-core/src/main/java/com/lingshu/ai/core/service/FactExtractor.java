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
            
            输入上下文：
            - 用户最新消息: {{message}}
            - 当前已知事实列表: {{currentFacts}}
            
            处理逻辑：
            1. 发现新事实：如果消息中包含有关用户的性格、偏好、工作、习惯、情感偏向等新信息，提取为简洁的陈述句。
            2. 纠正/废弃：如果用户纠正了之前的说法（例如：“我不喜欢喝咖啡了”，而记忆库中有“用户喜欢咖啡”），或者某个信息已过时，请将被废弃事实的 ID 放入删除列表。
            3. 如果信息只是略微增强但无冲突，不需要删除旧的。
            
            请返回符合 MemoryUpdate 格式的数据结构。
            """)
    MemoryUpdate analyze(@V("message") String message, @V("currentFacts") String currentFacts);
}
