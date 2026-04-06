package com.lingshu.ai.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 核心思想：自己持有 McpTransport。
 * 不走 DefaultMcpClient 的 executeTool，直接使用 raw JSON-RPC，
 * 完全接管工具调用的执行与内容解析过程。
 */
public class SimpleMcpClient implements RawMcpClient {

    private static final Logger log = LoggerFactory.getLogger(SimpleMcpClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 独立的 ID 生成器，为避免与默认 client 的 ping 等发生冲突，可以从一个较大的数或负数开始
    private final AtomicLong reqIdGenerator = new AtomicLong(System.currentTimeMillis());

    private final String clientName;
    private final McpTransport transport;
    // 依赖 defaultClient 仅用于安全地关闭和查询基础工具列表
    private final McpClient delegateClient;

    public SimpleMcpClient(String clientName, McpTransport transport, McpClient delegateClient) {
        this.clientName = clientName;
        this.transport = transport;
        this.delegateClient = delegateClient;
    }

    @Override
    public JsonNode callToolRaw(String toolName, String arguments) throws Exception {
        log.debug("Raw call tool: {}, args: {}", toolName, arguments);

        long id = reqIdGenerator.incrementAndGet();

        ObjectNode argsNode;
        if (arguments != null && !arguments.isBlank()) {
            argsNode = (ObjectNode) OBJECT_MAPPER.readTree(arguments);
        } else {
            argsNode = OBJECT_MAPPER.createObjectNode();
        }

        // 构建底层的 JSON-RPC 请求 (对应于 protocol-tools-call)
        McpCallToolRequest request = new McpCallToolRequest(id, toolName, argsNode);
        
        // 构建 invocation 上下文
        McpCallContext context = new McpCallContext(null, request);

        // 利用 transport 完全自我控制发送并等待响应
        // 注意：此处设置 60 秒超时作为默认防护
        return transport.executeOperationWithResponse(context).get(60, TimeUnit.SECONDS);
    }

    @Override
    public List<ToolSpecification> listTools() {
        return delegateClient.listTools();
    }

    @Override
    public String getClientName() {
        return clientName;
    }

    /**
     * 关闭资源，释放底层的 client 和 transport。
     */
    public void close() throws Exception {
        if (delegateClient != null) {
            delegateClient.close();
        }
    }
}
