package com.lingshu.ai.core.tool;


import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 MCP 工具提供者，主要用于截获并处理 MCP 返回的 ImageContent。
 * 它通过直接向 ChatMemory 书写 UserMessage，来解决原版 LangChain4j ToolExecutor
 * 返回值限定为 String，导致丢失图片的问题。
 */
public class ImageHandlingMcpToolProvider implements ToolProvider {
    private static final Logger log = LoggerFactory.getLogger(ImageHandlingMcpToolProvider.class);

    private final McpToolProvider delegate;
    private final List<McpClient> mcpClients;
    private final ChatMemoryProvider chatMemoryProvider;

    public ImageHandlingMcpToolProvider(List<McpClient> mcpClients, ChatMemoryProvider chatMemoryProvider) {
        this.mcpClients = mcpClients;
        this.chatMemoryProvider = chatMemoryProvider;
        this.delegate = McpToolProvider.builder().mcpClients(mcpClients).build();
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        // 先让原生 McpToolProvider 拿到所有的 ToolSpecification
        ToolProviderResult originalResult = delegate.provideTools(request);
        if (originalResult == null || originalResult.tools() == null) {
            return originalResult;
        }

        Map<ToolSpecification, ToolExecutor> tools = originalResult.tools();
        Map<ToolSpecification, ToolExecutor> newTools = new HashMap<>();

        for (Map.Entry<ToolSpecification, ToolExecutor> entry : tools.entrySet()) {
            ToolSpecification spec = entry.getKey();
            
            // 我们重写 Executor，绕过 ToolExecutionHelper
            ToolExecutor customExecutor = (toolExecutionRequest, memoryId) -> {
                McpClient targetClient = findClientForTool(spec.name());
                if (targetClient == null) {
                    return "Error: MCP Client not found for tool " + spec.name();
                }

                try {
                    // 直接使用反射执行 McpClient.executeTool，如果需要构建请求包，内部 fallback 处理
                    Object mcpResult = executeMcpToolFallback(targetClient, toolExecutionRequest);
                    
                    // 处理执行结果 (McpCallToolResult)
                    StringBuilder textResponse = new StringBuilder();
                    boolean hasImage = false;

                    if (mcpResult != null) {
                        List<?> contentList = extractContentList(mcpResult);
                        
                        for (Object contentItem : contentList) {
                            String type = extractField(contentItem, "type");
                            
                            if ("text".equals(type) || "TEXT".equalsIgnoreCase(type)) {
                                String text = extractField(contentItem, "text");
                                if (text != null) {
                                    textResponse.append(text).append("\n");
                                }
                            } else if ("image".equals(type) || "IMAGE".equalsIgnoreCase(type)) {
                                String data = extractField(contentItem, "data");
                                String mimeType = extractField(contentItem, "mimeType");
                                
                                if (data != null && !data.isEmpty()) {
                                    hasImage = true;
                                    log.info("Intercepted MCP ImageContent (mimeType: {}), injecting into memory...", mimeType);
                                    
                                    // 重点：将 ImageContent 组装为 UserMessage，追加到当前 ChatMemory 中
                                    if (memoryId != null && chatMemoryProvider != null) {
                                        ChatMemory memory = chatMemoryProvider.get(memoryId);
                                        if (memory != null) {
                                            ImageContent imageContent = ImageContent.from(data, mimeType != null ? mimeType : "image/png");
                                            memory.add(UserMessage.from(imageContent));
                                            textResponse.append("[系统提示：已在多模态上下文中为你注入了返回的图片。]\n");
                                        } else {
                                            log.warn("Cannot inject image: ChatMemory is null for memoryId {}", memoryId);
                                        }
                                    } else {
                                        log.warn("Cannot inject image: memoryId or chatMemoryProvider is null");
                                    }
                                }
                            }
                        }
                    }

                    if (!hasImage && textResponse.length() == 0) {
                        return "Tool executed successfully with no content.";
                    }

                    return textResponse.toString().trim();

                } catch (Exception e) {
                    log.error("Error executing MCP tool customized: ", e);
                    return "Error parsing or executing MCP tool: " + e.getMessage();
                }
            };
            
            newTools.put(spec, customExecutor);
        }

        return new ToolProviderResult(newTools);
    }

    private McpClient findClientForTool(String toolName) {
        for (McpClient client : mcpClients) {
            try {
                // 通过 client.listTools() 获取列表
                Method listMethod = null;
                for (Method m : client.getClass().getMethods()) {
                    if (m.getName().equals("listTools")) {
                        listMethod = m;
                        break;
                    }
                }
                
                if (listMethod != null) {
                    List<?> tools = (List<?>) listMethod.invoke(client);
                    if (tools != null) {
                        for (Object t : tools) {
                            String name = extractField(t, "name");
                            if (toolName.equals(name)) {
                                return client;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (mcpClients.size() == 1) {
            return mcpClients.get(0);
        }
        return null;
    }

    private Object executeMcpToolFallback(McpClient client, ToolExecutionRequest request) throws Exception {
       Method executeMethod = null;
       for (Method m : client.getClass().getMethods()) {
           if (m.getName().equals("executeTool") && m.getParameterCount() == 1) {
               executeMethod = m;
               break;
           }
       }
       if (executeMethod == null) {
           throw new IllegalStateException("Cannot find executeTool method on McpClient");
       }
       
       Class<?> reqClass = executeMethod.getParameterTypes()[0];
       
       // 如果参数就是 ToolExecutionRequest，直接传过去
       if (reqClass.isAssignableFrom(request.getClass())) {
           return executeMethod.invoke(client, request);
       }
       
       // 否则提取 toolName 和 arguments 取给备用的 Request Builder
       String toolName = request.name();
       String argumentsJson = request.arguments();
       
       com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
       mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
       Map<String, Object> arguments = mapper.readValue(argumentsJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
       
       Object requestObj = null;
       
       try {
           requestObj = reqClass.getConstructor(String.class, Map.class).newInstance(toolName, arguments);
       } catch (Exception e1) {
           try {
               Method builderMethod = reqClass.getMethod("builder");
               Object builder = builderMethod.invoke(null);
               builder.getClass().getMethod("name", String.class).invoke(builder, toolName);
               builder.getClass().getMethod("arguments", Map.class).invoke(builder, arguments);
               requestObj = builder.getClass().getMethod("build").invoke(builder);
           } catch (Exception e2) {
               try {
                   requestObj = reqClass.getConstructors()[0].newInstance(toolName, arguments);
               } catch (Exception e3) {}
           }
       }
       
       if (requestObj == null) {
           try {
               Map<String, Object> paramsMap = new HashMap<>();
               paramsMap.put("name", toolName);
               paramsMap.put("arguments", arguments);

               Map<String, Object> reqMap = new HashMap<>();
               reqMap.put("method", "tools/call");
               reqMap.put("params", paramsMap);
               reqMap.put("name", toolName);
               reqMap.put("arguments", arguments);

               requestObj = mapper.convertValue(reqMap, reqClass);
           } catch (Exception e4) {
               throw new RuntimeException("Failed to construct " + reqClass.getName() + " with Jackson fallback: " + e4.getMessage(), e4);
           }
       }
       
       if (requestObj == null) {
           throw new RuntimeException("Failed to construct CallToolRequest for tool: " + toolName + " | Class: " + reqClass.getName());
       }
       
       return executeMethod.invoke(client, requestObj);
    }
    
    private List<?> extractContentList(Object mcpResult) {
        if (mcpResult == null) return java.util.Collections.emptyList();
        try {
            Method contentMethod = mcpResult.getClass().getMethod("content");
            List<?> val = (List<?>) contentMethod.invoke(mcpResult);
            return val != null ? val : java.util.Collections.emptyList();
        } catch (Exception e) {
            try {
                Method getContentMethod = mcpResult.getClass().getMethod("getContent");
                List<?> val = (List<?>) getContentMethod.invoke(mcpResult);
                return val != null ? val : java.util.Collections.emptyList();
            } catch (Exception ex) {
                return java.util.Collections.emptyList();
            }
        }
    }
    
    private String extractField(Object item, String fieldName) {
        if (item == null) return null;
        try {
            Method m = item.getClass().getMethod(fieldName);
            return (String) m.invoke(item);
        } catch (Exception e) {
            try {
                String capitalized = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                Method m = item.getClass().getMethod("get" + capitalized);
                return (String) m.invoke(item);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
