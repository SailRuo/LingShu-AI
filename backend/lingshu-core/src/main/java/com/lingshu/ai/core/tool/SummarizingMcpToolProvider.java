package com.lingshu.ai.core.tool;

import com.lingshu.ai.core.service.ToolResultSummarizer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SummarizingMcpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(SummarizingMcpToolProvider.class);

    private final ToolProvider delegate;
    private final ToolResultSummarizer summarizer;
    private final Supplier<String> userIntentSupplier;

    public SummarizingMcpToolProvider(
            List<McpClient> mcpClients,
            ToolResultSummarizer summarizer,
            Supplier<String> userIntentSupplier) {
        this.delegate = dev.langchain4j.mcp.McpToolProvider.builder()
                .mcpClients(mcpClients)
                .build();
        this.summarizer = summarizer;
        this.userIntentSupplier = userIntentSupplier;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult delegateResult = delegate.provideTools(request);
        
        if (delegateResult == null) {
            return null;
        }

        Map<ToolSpecification, ToolExecutor> wrappedTools = new HashMap<>();
        
        delegateResult.tools().forEach((toolSpec, executor) -> {
            ToolExecutor wrappedExecutor = createSummarizingExecutor(toolSpec, executor);
            wrappedTools.put(toolSpec, wrappedExecutor);
        });

        return ToolProviderResult.builder()
                .addAll(wrappedTools)
                .build();
    }

    private ToolExecutor createSummarizingExecutor(ToolSpecification toolSpec, ToolExecutor delegate) {
        return (toolExecutionRequest, memoryId) -> {
            log.debug("执行工具: {}", toolSpec.name());
            
            String result = delegate.execute(toolExecutionRequest, memoryId);
            
            if (summarizer.shouldSummarize(toolSpec.name(), result)) {
                String userIntent = userIntentSupplier != null ? userIntentSupplier.get() : null;
                
                ToolResultSummarizer.SummarizeResult summarizeResult = 
                        summarizer.summarize(toolSpec.name(), result, userIntent);
                
                if (summarizeResult.wasSummarized()) {
                    log.info("工具 '{}' 结果已总结: {} -> {} 字符", 
                            toolSpec.name(), 
                            summarizeResult.originalLength(), 
                            summarizeResult.summarizedLength());
                    
                    return formatSummarizedResult(toolSpec.name(), summarizeResult);
                }
            }
            
            return result;
        };
    }

    private String formatSummarizedResult(String toolName, ToolResultSummarizer.SummarizeResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[工具 %s 返回数据已总结，原始长度: %d 字符]\n", toolName, result.originalLength()));
        
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
