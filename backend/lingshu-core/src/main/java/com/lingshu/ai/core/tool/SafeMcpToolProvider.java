package com.lingshu.ai.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.lingshu.ai.core.service.ToolResultSummarizer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpImageContent;
import dev.langchain4j.mcp.client.McpPromptContent;
import dev.langchain4j.mcp.client.McpTextContent;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;

/**
 * 支持多模态（图像）内容的 MCP ToolProvider。
 * <p>
 * 在 1.13.0 版本中，通过重写 {@link ToolExecutor#executeWithContext(ToolExecutionRequest, InvocationContext)}
 * 返回 {@link ToolExecutionResult}，从而原生支持多模态内容（List<Content>）。
 */
public class SafeMcpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(SafeMcpToolProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final List<RawMcpClient> mcpClients;
    private final ToolResultSummarizer summarizer;
    private final Supplier<String> userIntentSupplier;

    public SafeMcpToolProvider(
            List<RawMcpClient> mcpClients,
            ToolResultSummarizer summarizer,
            Supplier<String> userIntentSupplier) {
        this.mcpClients = mcpClients;
        this.summarizer = summarizer;
        this.userIntentSupplier = userIntentSupplier;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();

        for (RawMcpClient client : mcpClients) {
            try {
                List<ToolSpecification> toolSpecs = client.listTools();
                for (ToolSpecification spec : toolSpecs) {
                    tools.put(spec, createExecutor(client, spec));
                }
            } catch (Exception e) {
                log.error("获取 MCP {} 工具列表失败: {}", client.getClientName(), e.getMessage(), e);
            }
        }

        if (tools.isEmpty()) {
            return null;
        }

        return ToolProviderResult.builder()
                .addAll(tools)
                .build();
    }

    private ToolExecutor createExecutor(RawMcpClient client, ToolSpecification toolSpec) {
        return new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
                // 兼容旧版，但 1.13.0 应该优先调用 executeWithContext
                Object result = doExecute(client, toolSpec, toolExecutionRequest);
                if (result instanceof List<?>) {
                     // 如果是多模态内容且被迫转为 String，尝试提取文本部分
                     @SuppressWarnings("unchecked")
                     List<Content> contents = (List<Content>) result;
                     return contents.stream()
                             .filter(c -> c instanceof TextContent)
                             .map(c -> ((TextContent) c).text())
                             .findFirst()
                             .orElse("[Multimodal Content]");
                }
                return String.valueOf(result);
            }

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest toolExecutionRequest, InvocationContext context) {
                Object result = doExecute(client, toolSpec, toolExecutionRequest);
                if (result instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Content> contents = (List<Content>) result;
                    return ToolExecutionResult.builder()
                            .resultContents(contents)
                            .build();
                } else {
                    return ToolExecutionResult.builder()
                            .resultText(String.valueOf(result))
                            .build();
                }
            }
        };
    }

    private Object doExecute(RawMcpClient client, ToolSpecification toolSpec, ToolExecutionRequest toolExecutionRequest) {
        log.debug("执行 MCP 工具: {}", toolSpec.name());
        try {
            JsonNode rawResult = client.callToolRaw(toolSpec.name(), toolExecutionRequest.arguments());
            List<McpPromptContent> contents = parseContents(rawResult);

            StringBuilder textParts = new StringBuilder();
            List<McpImageContent> images = new ArrayList<>();

            for (McpPromptContent content : contents) {
                if (content instanceof McpTextContent text) {
                    textParts.append(text.text()).append("\n");
                } else if (content instanceof McpImageContent image) {
                    images.add(image);
                }
            }

            if (textParts.isEmpty() && images.isEmpty()) {
                String fallbackText = extractTextFallback(rawResult);
                if (fallbackText != null && !fallbackText.isBlank()) {
                    textParts.append(fallbackText);
                }
            }

            return buildMultimodalResult(toolSpec.name(), textParts.toString().trim(), images);
        } catch (Exception e) {
            log.error("MCP 工具执行失败: tool={}, error={}", toolSpec.name(), e.getMessage(), e);
            return "[工具执行失败] " + toolSpec.name() + ": " + e.getMessage();
        }
    }

    private List<McpPromptContent> parseContents(JsonNode rawResult) {
        List<McpPromptContent> contents = new ArrayList<>();
        JsonNode resultNode = rawResult.get("result");
        if (resultNode == null || !resultNode.has("content")) return contents;
        JsonNode contentArray = resultNode.get("content");
        if (!contentArray.isArray()) return contents;

        for (JsonNode item : contentArray) {
            try {
                contents.add(OBJECT_MAPPER.treeToValue(item, McpPromptContent.class));
            } catch (Exception e) {
                log.warn("反序列化 MCP 内容失败: {}", e.getMessage());
            }
        }
        return contents;
    }

    private String extractTextFallback(JsonNode rawResult) {
        JsonNode resultNode = rawResult.get("result");
        if (resultNode == null || resultNode.isNull()) return null;
        JsonNode structured = resultNode.get("structuredContent");
        if (structured != null && !structured.isNull()) return jsonNodeToReadableText(structured);
        JsonNode content = resultNode.get("content");
        if (content != null) {
            if (content.isTextual()) return content.asText();
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : content) {
                    JsonNode textNode = item.get("text");
                    if (textNode != null && textNode.isTextual()) sb.append(textNode.asText()).append("\n");
                }
                return sb.toString().trim();
            }
        }
        return jsonNodeToReadableText(resultNode);
    }

    private String jsonNodeToReadableText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return node.isTextual() ? node.asText() : OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private Object buildMultimodalResult(String toolName, String textContent, List<McpImageContent> images) {
        if (images.isEmpty()) {
            return textContent.isEmpty() ? "[无内容]" : applySummarizer(toolName, textContent);
        }

        List<Content> resultList = new ArrayList<>();
        String finalOutput = textContent.isEmpty() ? "" : applySummarizer(toolName, textContent);
        if (!finalOutput.isEmpty()) {
            resultList.add(TextContent.from(finalOutput));
        }

        for (McpImageContent image : images) {
            resultList.add(ImageContent.from(image.data(), image.mimeType()));
        }
        return resultList;
    }

    private String applySummarizer(String toolName, String result) {
        if (summarizer != null && summarizer.shouldSummarize(toolName, result)) {
            String userIntent = userIntentSupplier != null ? userIntentSupplier.get() : null;
            ToolResultSummarizer.SummarizeResult summarizeResult = summarizer.summarize(toolName, result, userIntent);
            if (summarizeResult.wasSummarized()) {
                return formatSummarizedResult(toolName, summarizeResult);
            }
        }
        return result;
    }

    private String formatSummarizedResult(String toolName, ToolResultSummarizer.SummarizeResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[工具 %s 返回数据已总结，原始长度: %d 字符]\n", toolName, result.originalLength()));
        if (!result.relevant()) sb.append("[内容与用户问题无关]\n");
        sb.append("\n摘要：\n").append(result.content()).append("\n");
        if (!result.keyPoints().isEmpty()) {
            sb.append("\n关键点：\n");
            for (int i = 0; i < result.keyPoints().size(); i++) {
                sb.append(String.format("%d. %s\n", i + 1, result.keyPoints().get(i)));
            }
        }
        sb.append(String.format("\n[置信度: %.0f%%]", result.confidence() * 100));
        return sb.toString();
    }
}
