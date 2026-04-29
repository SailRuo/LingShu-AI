package com.lingshu.ai.infrastructure.memory;

import com.lingshu.ai.infrastructure.entity.ChatTurn;
import com.lingshu.ai.infrastructure.entity.ChatTurnEvent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatContextAssemblerTest {

    @Test
    void assemble_shouldPreserveToolErrorFlagFromTurnEvents() {
        ChatTurn turn = ChatTurn.builder()
                .id(1L)
                .userMessage("帮我打开应用")
                .assistantMessage("")
                .status("running")
                .createdAt(LocalDateTime.of(2026, 4, 29, 23, 40))
                .build();

        ChatTurnEvent toolStart = ChatTurnEvent.builder()
                .id(11L)
                .turn(turn)
                .sequenceNo(1)
                .eventType("tool_start")
                .toolCallId("call-1")
                .toolName("App")
                .arguments("{}")
                .createdAt(LocalDateTime.of(2026, 4, 29, 23, 40, 1))
                .build();
        ChatTurnEvent toolEnd = ChatTurnEvent.builder()
                .id(12L)
                .turn(turn)
                .sequenceNo(2)
                .eventType("tool_end")
                .toolCallId("call-1")
                .toolName("App")
                .content("[工具执行失败] App: unknown error")
                .isError(true)
                .createdAt(LocalDateTime.of(2026, 4, 29, 23, 40, 2))
                .build();

        ChatContextAssembler.AssemblyResult result = new ChatContextAssembler().assemble(
                List.of(turn),
                Map.of(turn.getId(), List.of(toolStart, toolEnd)),
                Map.of()
        );

        ToolExecutionResultMessage message = assertInstanceOf(
                ToolExecutionResultMessage.class,
                result.messages().get(2)
        );
        assertEquals("call-1", message.id());
        assertTrue(Boolean.TRUE.equals(message.isError()));
    }
}
