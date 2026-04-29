package com.lingshu.ai.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeMcpToolProviderTest {

    @Test
    void executeWithContext_shouldMarkToolFailureAsErrorResult() throws Exception {
        ToolSpecification spec = ToolSpecification.builder()
                .name("App")
                .description("desktop app tool")
                .parameters(JsonObjectSchema.builder().build())
                .build();
        RawMcpClient client = new RawMcpClient() {
            @Override
            public com.fasterxml.jackson.databind.JsonNode callToolRaw(String toolName, String arguments) {
                throw new IllegalStateException();
            }

            @Override
            public List<ToolSpecification> listTools() {
                return List.of(spec);
            }

            @Override
            public String getClientName() {
                return "test-client";
            }
        };

        SafeMcpToolProvider provider = new SafeMcpToolProvider(List.of(client), null, () -> "test");
        ToolProviderResult result = provider.provideTools(null);
        ToolExecutionResult executionResult = result.toolExecutorByName("App").executeWithContext(
                ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("App")
                        .arguments("{}")
                        .build(),
                null
        );

        assertTrue(executionResult.isError());
        assertTrue(executionResult.resultText().contains("[工具执行失败] App"));
        assertTrue(executionResult.resultText().contains("unknown error"));
    }
}
