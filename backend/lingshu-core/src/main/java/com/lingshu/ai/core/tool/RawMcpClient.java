package com.lingshu.ai.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * 直接暴露底层 JSON-RPC 操作的 MCP 客户端接口。
 * 绕过 LangChain4j 等框架中受限的类型体系，允许直接获取原始响应（例如支持包含图像的返回内容）。
 */
public interface RawMcpClient {

    /**
     * 调用工具，并返回原始 JSON-RPC 的 result 对象（作为 JsonNode）。
     *
     * @param toolName  工具名称
     * @param arguments JSON 格式的参数字符串
     * @return 原始的完整 JSON 响应，便于自行提取 image/text/resource 等多类型内容
     */
    JsonNode callToolRaw(String toolName, String arguments) throws Exception;

    /**
     * 获取所有可用工具的列表。
     */
    List<ToolSpecification> listTools();

    /**
     * 获取原始的名称（用于标识是哪个服务）
     */
    String getClientName();
}
