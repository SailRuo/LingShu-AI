package com.lingshu.ai.core.tool;

/**
 * MCP 工具返回的内容基类。
 * 用于类型安全地处理 MCP 协议中不同类型的返回内容（文本、图像等）。
 */
public sealed interface McpContent permits McpContent.Text, McpContent.Image, McpContent.Unknown {

    /**
     * 文本内容
     */
    record Text(String text) implements McpContent {}

    /**
     * 图像内容（Base64 编码）
     */
    record Image(String data, String mimeType) implements McpContent {}

    /**
     * 未知/不支持的内容类型
     */
    record Unknown(String type, String rawJson) implements McpContent {}
}
