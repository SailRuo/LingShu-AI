package com.lingshu.ai.core.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSessionRouterServiceTest {

    private final TaskSessionRouterService service = new TaskSessionRouterService();

    @Test
    void isTaskRequest_shouldReturnFalseForCasualConversation() {
        assertFalse(service.isTaskRequest("今天过得怎么样？"));
        assertFalse(service.isTaskRequest("测试一下你还记得我吗"));
        assertFalse(service.isTaskRequest("你好"));
        assertFalse(service.isTaskRequest("hello!"));
    }

    @Test
    void isTaskRequest_shouldReturnTrueForLocalProjectWork() {
        assertTrue(service.isTaskRequest("帮我在 E:\\Project\\LingShu-AI 里修复测试"));
        assertTrue(service.isTaskRequest("请在 backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/ChatController.java 里改代码"));
        assertTrue(service.isTaskRequest("进入仓库后运行 mvn test"));
    }

    @Test
    void decide_shouldExposeReasonForMatchedRequest() {
        TaskSessionRouterService.TaskRouteDecision decision = service.decide("请在 ./backend 目录运行 git status");

        assertTrue(decision.taskRequest());
        assertTrue(decision.reason().contains("command") || decision.reason().contains("path"));
    }

    @Test
    void decide_shouldNotRouteGreetingIntoTask() {
        TaskSessionRouterService.TaskRouteDecision decision = service.decide("你好");

        assertFalse(decision.taskRequest());
        assertTrue(decision.reason().contains("non-task"));
    }
}
