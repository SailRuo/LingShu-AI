package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.service.McpService;
import com.lingshu.ai.infrastructure.entity.McpServerConfig;
import com.lingshu.ai.infrastructure.repository.McpServerConfigRepository;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpServiceImpl implements McpService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpServiceImpl.class);

    private final McpServerConfigRepository repository;
    private final ObjectMapper objectMapper;
    
    // Cache for active MCP clients
    private final Map<Long, McpClient> clientStorage = new ConcurrentHashMap<>();

    public McpServiceImpl(McpServerConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("Initializing active MCP clients...");
        List<McpServerConfig> activeConfigs = repository.findByIsActiveTrue();
        for (McpServerConfig config : activeConfigs) {
            try {
                initClient(config);
            } catch (Exception e) {
                log.error("Failed to initialize MCP client '{}': {}", config.getName(), e.getMessage());
            }
        }
    }

    private void initClient(McpServerConfig config) {
        removeClient(config.getId());
        
        McpClient mcpClient;
        if ("STDIO".equalsIgnoreCase(config.getTransportType())) {
            List<String> argsList = Collections.emptyList();
            Map<String, String> envMap = Collections.emptyMap();
            try {
                if (config.getArgs() != null && !config.getArgs().isBlank()) {
                    argsList = objectMapper.readValue(config.getArgs(), new TypeReference<List<String>>() {});
                }
                if (config.getEnv() != null && !config.getEnv().isBlank()) {
                    envMap = objectMapper.readValue(config.getEnv(), new TypeReference<Map<String, String>>() {});
                }
            } catch (Exception e) {
                log.error("Failed to parse args or env for MCP client: {}", config.getName(), e);
            }

            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(config.getCommand());
            fullCommand.addAll(argsList);
            
            StdioMcpTransport transport = new StdioMcpTransport.Builder()
                .command(fullCommand)
                .environment(envMap)
                .logEvents(true)
                .build();

            mcpClient = new DefaultMcpClient.Builder()
                    .clientName("lingshu-ai")
                    .transport(transport)
                    .build();
        } else {
            // SSE Transport
            StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                    .url(config.getUrl())
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            mcpClient = new DefaultMcpClient.Builder()
                    .clientName("lingshu-ai")
                    .transport(transport)
                    .build();
        }
        
        clientStorage.put(config.getId(), mcpClient);
        log.info("Successfully initialized MCP Client: {}", config.getName());
    }

    private void removeClient(Long id) {
        McpClient existing = clientStorage.remove(id);
        if (existing != null) {
            try {
                 existing.close();
            } catch (Exception e) {
                log.warn("Error closing MCP client", e);
            }
        }
    }

    @Override
    public List<McpServerConfig> getAllConfigs() {
        return repository.findAll();
    }

    @Override
    public McpServerConfig saveConfig(McpServerConfig config) {
        McpServerConfig saved = repository.save(config);
        if (saved.getIsActive()) {
            try {
                initClient(saved);
            } catch (Exception e) {
                log.error("Failed to start MCP client '{}' after saving: {}", saved.getName(), e.getMessage());
            }
        } else {
            removeClient(saved.getId());
        }
        return saved;
    }

    @Override
    public boolean deleteConfig(Long id) {
        if (repository.existsById(id)) {
            removeClient(id);
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    @Override
    public McpServerConfig toggleConfig(Long id) {
        McpServerConfig config = repository.findById(id).orElseThrow();
        config.setIsActive(!config.getIsActive());
        McpServerConfig saved = repository.save(config);
        if (saved.getIsActive()) {
            try {
                initClient(saved);
            } catch (Exception e) {
                log.error("Failed to start MCP client '{}' after toggle: {}", saved.getName(), e.getMessage());
            }
        } else {
            removeClient(id);
        }
        return saved;
    }

    @Override
    public List<McpClient> getActiveClients() {
        return new ArrayList<>(clientStorage.values());
    }
    
    @Override
    public java.util.List<java.util.Map<String, Object>> getToolDetails(Long id) {
        McpClient client = clientStorage.get(id);
        if (client == null) return Collections.emptyList();
        try {
            var tools = client.listTools();
            if (tools == null) return Collections.emptyList();
            
            java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
            for (var tool : tools) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                try {
                    map.put("name", tool.getClass().getMethod("getName").invoke(tool));
                    map.put("description", tool.getClass().getMethod("getDescription").invoke(tool));
                } catch (Exception ex) {
                    map.put("name", tool.toString());
                }
                result.add(map);
            }
            return result;
        } catch (Exception e) {
            log.warn("MCP Check failed for id {}: {}", id, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void importFromJson(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object serversObj = root.get("mcpServers");
            Map<String, Map<String, Object>> servers;
            
            if (serversObj instanceof Map) {
                servers = (Map<String, Map<String, Object>>) serversObj;
            } else {
                servers = (Map<String, Map<String, Object>>) (Object) root;
            }
            
            for (Map.Entry<String, Map<String, Object>> entry : servers.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> configMap = entry.getValue();
                
                McpServerConfig config = new McpServerConfig();
                config.setName(name);
                config.setIsActive(true);
                
                if (configMap.containsKey("url")) {
                    config.setTransportType("SSE");
                    config.setUrl(configMap.get("url").toString());
                } else {
                    config.setTransportType("STDIO");
                    if (configMap.containsKey("command")) {
                        config.setCommand(configMap.get("command").toString());
                    }
                    if (configMap.containsKey("args")) {
                        config.setArgs(objectMapper.writeValueAsString(configMap.get("args")));
                    }
                    if (configMap.containsKey("env")) {
                        config.setEnv(objectMapper.writeValueAsString(configMap.get("env")));
                    }
                }
                
                repository.findAll().stream()
                        .filter(c -> c.getName().equalsIgnoreCase(name))
                        .findFirst()
                        .ifPresent(existing -> config.setId(existing.getId()));
                
                saveConfig(config);
            }
        } catch (Exception e) {
            log.error("Failed to import MCP config from JSON", e);
            throw new RuntimeException("导入失败，JSON 格式不符合标准 MCP 规范: " + e.getMessage());
        }
    }
}
