package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.FactRelationshipResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FactRelationshipEvaluator {

    @SystemMessage("""
            你是灵枢 AI 的记忆关系分析器。
            你的任务是评估两条记忆事实之间的语义关系。

            允许的关系类型(type)只有：
            SUPERSEDES: 新事实是旧事实的后续更新版本，或者在时间轴上取代了旧事实。
            CONTRADICTS: 新旧事实在语义上存在直接的逻辑冲突或否定关系。
            RELATED_TO: 新旧事实在语义上相关，但不足以构成替代或冲突。
            NONE: 新旧事实之间没有明显的语义关系。

            分析要求：
            1. 仔细对比两条事实的语义、时间倾向、对象倾向。
            2. 如果新事实明显纠正了旧事实中的某个具体值（如改变偏好、状态、计划），请输出 SUPERSEDES。
            3. 如果新事实和旧事实在同一问题上给出了互斥且非随时间演进的判断，请输出 CONTRADICTS。
            4. 如果两者谈论相同主题、对象或事件但无替代或冲突，请输出 RELATED_TO。
            5. 如果两者毫无关联，请输出 NONE。

            只返回合法的 JSON 格式：
            {
              "type": "SUPERSEDES",
              "confidence": 0.85,
              "reasoning": "简要说明判断理由"
            }
            """)
    @UserMessage("""
            旧事实（参照）：
            {{oldFact}}

            新事实（待定）：
            {{newFact}}
            """)
    FactRelationshipResult evaluate(@V("oldFact") String oldFact, @V("newFact") String newFact);

    @SystemMessage("""
            你是灵枢 AI 的记忆关系分析器。
            你的任务是从一组候选事实中，评估它们与一个新事实之间的语义关系。

            允许的关系类型(type)只有：
            SUPERSEDES: 新事实是旧事实的后续更新版本，或者在时间轴上取代了旧事实。
            CONTRADICTS: 新旧事实在语义上存在直接的逻辑冲突或否定关系。
            RELATED_TO: 新旧事实在语义上相关，但不足以构成替代或冲突。
            NONE: 如果所有候选事实与新事实都没有明显的语义关系。

            分析要求：
            1. 仔细对比候选事实列表与新事实。
            2. 优先寻找是否存在 SUPERSEDES 或 CONTRADICTS 关系的候选事实。
            3. 如果有多个候选事实相关，请选择关系最强（优先级：SUPERSEDES > CONTRADICTS > RELATED_TO）的一个。
            4. 如果没有候选事实符合前三种关系，返回 type 为 NONE 且 matchedFactId 为 null。

            只返回合法的 JSON 格式：
            {
              "matchedFactId": 123,
              "type": "SUPERSEDES",
              "confidence": 0.85,
              "reasoning": "简要说明判断理由"
            }
            """)
    @UserMessage("""
            候选事实列表 (ID: 内容)：
            {{candidates}}

            待定新事实：
            {{newFact}}
            """)
    FactRelationshipResult evaluateBatch(@V("candidates") String candidates, @V("newFact") String newFact);
}
