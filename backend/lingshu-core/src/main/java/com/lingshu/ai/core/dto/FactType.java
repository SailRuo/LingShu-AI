package com.lingshu.ai.core.dto;

public enum FactType {
    IDENTITY("身份事实", "用户的身份信息，如名字、职业、年龄等"),
    PREFERENCE("偏好事实", "用户的喜好、兴趣、习惯等"),
    EMOTIONAL_EPISODE("情感片段", "用户经历的情感事件，包含情绪状态和触发因素"),
    RELATIONSHIP("关系事实", "用户与他人或事物的关系信息"),
    GOAL("目标事实", "用户的目标、计划、愿望等"),
    EVENT("事件事实", "用户经历的重要事件"),
    STATE("状态事实", "用户的当前状态，如正在做的事情"),
    TODO("待办事项", "用户需要完成的任务、提醒事项，如明天要交报告、记得买牛奶"),
    VOLATILE("临时事实", "情绪激动时的极端表述，需要后续确认");

    private final String displayName;
    private final String description;

    FactType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
