package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.ChatService;
import com.lingshu.ai.core.service.ChatSessionService;
import com.lingshu.ai.core.service.ProactiveService;
import com.lingshu.ai.core.service.TurnTimelineService;
import com.lingshu.ai.core.service.impl.TaskSessionRouterService;
import com.lingshu.ai.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTaskIntentTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(
                        mock(ChatService.class),
                        mock(ChatSessionService.class),
                        mock(ProactiveService.class),
                        mock(TurnTimelineService.class),
                        new TaskSessionRouterService()
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void taskIntent_shouldReturnFalseForCasualMessage() throws Exception {
        mockMvc.perform(post("/api/chat/task-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"今天过得怎么样？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskRequest").value(false));
    }

    @Test
    void taskIntent_shouldReturnTrueAndReasonForTaskMessage() throws Exception {
        mockMvc.perform(post("/api/chat/task-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"请在 E:\\\\Project\\\\LingShu-AI 目录运行 mvn test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskRequest").value(true))
                .andExpect(jsonPath("$.reason").isNotEmpty());
    }
}
