package com.lingshu.ai.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.lingshu.ai.core.service.ToolResultSummarizer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpImageContent;
import dev.langchain4j.mcp.client.McpPromptContent;
import dev.langchain4j.mcp.client.McpTextContent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;

/**
 * 支持多模态（图像）内容的 MCP ToolProvider。
 * <p>
 * 直接调用 {@link RawMcpClient#callToolRaw(String, String)} 获取原始 JSON-RPC 响应，
 * 从根本上绕过了 LangChain4j 默认的 {@code McpToolProvider} 仅限文本的校验机制。
 * <p>
 * 图像内容会被注入到 ChatMemory 中作为 {@link dev.langchain4j.data.message.ImageContent}，
 * 让多模态 LLM 能够直接"看到"图片。
 */
public class SafeMcpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(SafeMcpToolProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final List<RawMcpClient> mcpClients;
    private final ToolResultSummarizer summarizer;
    private final Supplier<String> userIntentSupplier;
    private final ChatMemoryProvider chatMemoryProvider;
    private final McpToolArtifactRegistry artifactRegistry;

    public SafeMcpToolProvider(
            List<RawMcpClient> mcpClients,
            ToolResultSummarizer summarizer,
            Supplier<String> userIntentSupplier,
            ChatMemoryProvider chatMemoryProvider,
            McpToolArtifactRegistry artifactRegistry) {
        this.mcpClients = mcpClients;
        this.summarizer = summarizer;
        this.userIntentSupplier = userIntentSupplier;
        this.chatMemoryProvider = chatMemoryProvider;
        this.artifactRegistry = artifactRegistry;
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
        return (toolExecutionRequest, memoryId) -> {
            log.debug("执行 MCP 工具: {}", toolSpec.name());

            try {
                // 直接使用我们持有的 Transport 获取原始 JSON 响应
                JsonNode rawResult = client.callToolRaw(toolSpec.name(), toolExecutionRequest.arguments());
                
                // 解析文本和图像内容
                List<McpPromptContent> contents = parseContents(rawResult);

                StringBuilder textParts = new StringBuilder();
                List<McpImageContent> images = new ArrayList<>();

                for (McpPromptContent content : contents) {
                    if (content instanceof McpTextContent text) {
                        textParts.append(text.text()).append("\n");
                    } else if (content instanceof McpImageContent image) {
                        images.add(image);
                    } else {
                        log.warn("工具 '{}' 返回了未知的内容类型: {}", toolSpec.name(), content.type());
                    }
                }

                // Fallback: some MCP servers return structuredContent instead of text content array
                if (textParts.isEmpty() && images.isEmpty()) {
                    String fallbackText = extractTextFallback(rawResult);
                    if (fallbackText != null && !fallbackText.isBlank()) {
                        textParts.append(fallbackText);
                    }
                }

                // 将图像直接注入到 ChatMemory 让 LLM 能够访问
                if (!images.isEmpty() && chatMemoryProvider != null && memoryId != null) {
                    injectImagesToMemory(images, memoryId, toolSpec.name());
                }

                if (!images.isEmpty() && artifactRegistry != null) {
                    List<McpToolArtifact> artifacts = images.stream()
                            .map(img -> new McpToolArtifact("image", img.mimeType(), img.data(), null))
                            .toList();
                    artifactRegistry.put(toolExecutionRequest.id(), artifacts);
                }

                // 进行摘要处理并合并成提示返回给 LLM
                return buildToolResult(toolSpec.name(), textParts.toString().trim(), images);
                
            } catch (Exception e) {
                log.error("MCP 工具执行失败: tool={}, error={}", toolSpec.name(), e.getMessage(), e);
                return "[工具执行失败] " + toolSpec.name() + ": " + e.getMessage();
            }
        };
    }

    // ======================== 解析与图像注入核心实现 ========================

    /**
     * 使用 Jackson + McpPromptContent 的多态类型体系反序列化 JSON 内容。
     */
    private List<McpPromptContent> parseContents(JsonNode rawResult) {
        List<McpPromptContent> contents = new ArrayList<>();

        JsonNode resultNode = rawResult.get("result");
        if (resultNode == null || !resultNode.has("content")) {
            return contents;
        }

        JsonNode contentArray = resultNode.get("content");
        if (!contentArray.isArray()) {
            return contents;
        }

        for (JsonNode item : contentArray) {
            try {
                McpPromptContent content = OBJECT_MAPPER.treeToValue(item, McpPromptContent.class);
                contents.add(content);
            } catch (Exception e) {
                String type = item.has("type") ? item.get("type").asText() : "unknown";
                log.warn("反序列化 MCP 内容失败 (type={}): {}", type, e.getMessage());
            }
        }

        return contents;
    }

    private String extractTextFallback(JsonNode rawResult) {
        JsonNode resultNode = rawResult.get("result");
        if (resultNode == null || resultNode.isNull()) {
            return null;
        }

        // 1) Prefer structuredContent if present
        JsonNode structured = resultNode.get("structuredContent");
        if (structured != null && !structured.isNull()) {
            return jsonNodeToReadableText(structured);
        }

        // 2) If content exists but polymorphic parse failed, try manual extraction
        JsonNode content = resultNode.get("content");
        if (content != null) {
            if (content.isTextual()) {
                return content.asText();
            }
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : content) {
                    if (item == null || item.isNull()) {
                        continue;
                    }
                    JsonNode textNode = item.get("text");
                    if (textNode != null && textNode.isTextual()) {
                        if (!sb.isEmpty()) {
                            sb.append("\n");
                        }
                        sb.append(textNode.asText());
                    } else {
                        if (!sb.isEmpty()) {
                            sb.append("\n");
                        }
                        sb.append(jsonNodeToReadableText(item));
                    }
                }
                if (!sb.isEmpty()) {
                    return sb.toString();
                }
            }
        }

        // 3) Last fallback: use whole result payload
        return jsonNodeToReadableText(resultNode);
    }

    private String jsonNodeToReadableText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isTextual()) {
                return node.asText();
            }
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private void injectImagesToMemory(List<McpImageContent> images, Object memoryId, String toolName) {
        try {
            var memory = chatMemoryProvider.get(memoryId);

            List<Content> messageContents = new ArrayList<>();
            messageContents.add(TextContent.from(
                    String.format("[工具 %s 返回了 %d 张图片，请分析以下图像内容]",
                            toolName, images.size())));

            for (McpImageContent image : images) {
                // McpImageContent.toContent() → ImageContent.from(data, mimeType)
                messageContents.add(image.toContent());
            }

            memory.add(UserMessage.from(messageContents));
            log.info("已将 {} 张图像注入 ChatMemory: tool={}", images.size(), toolName);
        } catch (Exception e) {
            log.error("注入图像到 ChatMemory 失败: {}", e.getMessage(), e);
        }
    }

    private String buildToolResult(String toolName, String textContent, List<McpImageContent> images) {
        StringBuilder sb = new StringBuilder();

        if (!textContent.isEmpty()) {
            sb.append(applySummarizer(toolName, textContent));
        } else if (images.isEmpty()) {
             sb.append("[无文本内容]");
        }

        if (!images.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(String.format(
                    "[工具 %s 返回了 %d 张图片，图片内容已注入到对话上下文中，请直接分析图片并回答用户问题]",
                    toolName, images.size()));
        }

        return sb.toString();
    }

    // ======================== 摘要器 ========================

    private String applySummarizer(String toolName, String result) {
        if (summarizer != null && summarizer.shouldSummarize(toolName, result)) {
            String userIntent = userIntentSupplier != null ? userIntentSupplier.get() : null;
            ToolResultSummarizer.SummarizeResult summarizeResult =
                    summarizer.summarize(toolName, result, userIntent);

            if (summarizeResult.wasSummarized()) {
                log.info("工具 '{}' 结果已总结: {} -> {} 字符",
                        toolName, summarizeResult.originalLength(), summarizeResult.summarizedLength());
                return formatSummarizedResult(toolName, summarizeResult);
            }
        }
        return result;
    }

    private String formatSummarizedResult(String toolName, ToolResultSummarizer.SummarizeResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[工具 %s 返回数据已总结，原始长度: %d 字符]\n",
                toolName, result.originalLength()));

        if (!result.relevant()) {
            sb.append("[内容与用户问题无关]\n");
        }

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
