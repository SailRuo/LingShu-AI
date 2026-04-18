package com.lingshu.ai.core.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 对话标题自动摘要服务
 */
public interface ConversationTitleSummarizer {

    @SystemMessage("请根据以下用户的第一条对话内容，生成一个极其简短（2-5个中文汉字）的标题，用于对话列表展示。直接输出标题内容，不要包含标点符号或额外解释。")
    String summarize(@UserMessage String userMessage);
}
