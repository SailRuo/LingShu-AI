package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.FactSemanticClassification;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FactSemanticClassifier {

    @SystemMessage("""
            你是灵枢 AI 的记忆语义分类器。
            你的任务是把一条记忆事实分类到最合适的主题轨道(topicKey)和事实子类型(subType)。

            允许的 topicKey 只有：
            interest, growth, goal, emotion, relationship, event, timeline, memory, health

            允许的 subType 只有：
            Preference, EmotionState, Person, Project, Goal, Event, TimeAnchor, Memory, HealthState

            分类要求：
            1. 只根据输入文本做语义分类，不要扩写事实。
            2. 优先参考历史主题分布，但不能违背当前事实本身语义。
            3. 如果内容主要描述偏好、爱好、习惯，使用 interest / Preference。
            4. 如果内容主要描述情绪状态，使用 emotion / EmotionState。
            5. 如果内容主要描述身体健康、疾病、生理状态（如消化不良、感冒、过敏），使用 health / HealthState。
            6. 如果内容主要描述人际对象或关系，使用 relationship / Person。
            7. 如果内容主要描述工作、项目、学习任务，优先 growth / Project。
            8. 如果内容主要描述目标、打算、计划，优先 goal / Goal。
            9. 如果内容主要描述时间锚点、最近安排、短期时间线，优先 timeline / TimeAnchor。
            10. 如果内容主要描述某个发生过的事件，优先 event / Event。
            11. 如果无法明确判断，返回 memory / Memory。

            只返回合法 JSON：
            {
              "topicKey": "interest",
              "subType": "Preference",
              "confidence": 0.78
            }
            """)
    @UserMessage("""
            待分类事实：
            {{fact}}

            历史主题参考：
            {{historySummary}}
            """)
    FactSemanticClassification classify(@V("fact") String fact, @V("historySummary") String historySummary);
}
