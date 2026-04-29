package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.dto.task.TaskApprovalDecisionRequest;
import com.lingshu.ai.core.dto.task.TaskEventView;
import com.lingshu.ai.core.dto.task.TaskRunView;
import com.lingshu.ai.core.dto.task.TaskStartRequest;
import com.lingshu.ai.core.event.TaskEventAppendedEvent;
import com.lingshu.ai.core.service.TaskRuntimeService;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.web.exception.GlobalExceptionHandler;
import com.lingshu.ai.web.websocket.ChatWebSocketHandler;
import com.lingshu.ai.web.websocket.TaskEventBroadcastListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private MockMvc mockMvc;

    private TaskRuntimeService taskRuntimeService;

    @BeforeEach
    void setUp() {
        taskRuntimeService = org.mockito.Mockito.mock(TaskRuntimeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskRuntimeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void shouldStartTaskFromRestApi() throws Exception {
        TaskRunView view = sampleView("WAITING_APPROVAL");
        when(taskRuntimeService.start(new TaskStartRequest(
                "web:test-user",
                12L,
                "帮我修复 D:\\work\\demo 测试",
                "D:\\work\\demo",
                "npm"
        ))).thenReturn(view);

        mockMvc.perform(post("/api/tasks/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":"web:test-user",
                                  "chatSessionId":12,
                                  "requestText":"帮我修复 D:\\\\work\\\\demo 测试",
                                  "workspacePath":"D:\\\\work\\\\demo",
                                  "commandCategory":"npm"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.state").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.events[0].eventType").value("TASK_CREATED"));
    }

    @Test
    void shouldDelegateApproveOperationToService() throws Exception {
        TaskRunView view = sampleView("RUNNING");
        when(taskRuntimeService.approve(
                eq(101L),
                eq("web:test-user"),
                eq(new TaskApprovalDecisionRequest(true, true))
        )).thenReturn(view);

        mockMvc.perform(post("/api/tasks/101/approve")
                        .queryParam("userId", "web:test-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grantWorkspace": true,
                                  "grantCommandCategory": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(taskRuntimeService).approve(
                101L,
                "web:test-user",
                new TaskApprovalDecisionRequest(true, true)
        );
    }

    @Test
    void shouldReturnTaskEventsForOwnedRun() throws Exception {
        when(taskRuntimeService.get(101L, "web:test-user"))
                .thenReturn(sampleView("WAITING_APPROVAL"));

        mockMvc.perform(get("/api/tasks/101/events")
                        .queryParam("userId", "web:test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("TASK_CREATED"))
                .andExpect(jsonPath("$[0].sequenceNo").value(1));
    }

    private TaskRunView sampleView(String state) {
        return new TaskRunView(
                101L,
                "web:test-user",
                12L,
                "修复测试",
                "D:\\work\\demo",
                "npm",
                state,
                null,
                List.of(new TaskEventView(
                        201L,
                        1,
                        "TASK_CREATED",
                        "{\"requestText\":\"帮我修复 D:\\\\work\\\\demo 测试\"}",
                        1714400000000L
                ))
        );
    }
}

class TaskEventBroadcastListenerTest {

    @Test
    void shouldConvertAppendedTaskEventIntoTaskEventWebSocketMessage() {
        ChatWebSocketHandler chatWebSocketHandler = org.mockito.Mockito.mock(ChatWebSocketHandler.class);
        TaskEventBroadcastListener listener = new TaskEventBroadcastListener(chatWebSocketHandler);
        Map<String, Object> payload = Map.of(
                "workspacePath", "D:\\work\\demo",
                "commandCategory", "npm"
        );
        TaskEventAppendedEvent event = new TaskEventAppendedEvent(
                this,
                "web:test-user",
                101L,
                TaskEventType.APPROVAL_REQUIRED,
                payload
        );

        listener.handleTaskEventAppended(event);

        verify(chatWebSocketHandler).broadcastTaskEvent(
                eq("web:test-user"),
                argThat(matchesTaskEventMessage(101L, "APPROVAL_REQUIRED", payload))
        );
    }

    private ArgumentMatcher<Map<String, Object>> matchesTaskEventMessage(Long taskRunId,
                                                                         String eventType,
                                                                         Map<String, Object> payload) {
        return message -> "taskEvent".equals(message.get("type"))
                && taskRunId.equals(message.get("taskRunId"))
                && eventType.equals(message.get("eventType"))
                && payload.equals(message.get("payload"));
    }
}
