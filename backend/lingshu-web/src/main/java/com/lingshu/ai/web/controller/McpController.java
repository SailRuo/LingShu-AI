package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.McpService;
import com.lingshu.ai.infrastructure.entity.McpServerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    @GetMapping
    public ResponseEntity<List<McpServerConfig>> getAllConfigs() {
        return ResponseEntity.ok(mcpService.getAllConfigs());
    }

    @PostMapping
    public ResponseEntity<McpServerConfig> addConfig(@RequestBody McpServerConfig config) {
        config.setId(null); // Ensure fresh ID
        return ResponseEntity.ok(mcpService.saveConfig(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<McpServerConfig> updateConfig(@PathVariable Long id, @RequestBody McpServerConfig config) {
        config.setId(id);
        return ResponseEntity.ok(mcpService.saveConfig(config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        if (mcpService.deleteConfig(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/toggle")
    public ResponseEntity<McpServerConfig> toggleConfig(@PathVariable Long id) {
        return ResponseEntity.ok(mcpService.toggleConfig(id));
    }
    
    @PostMapping("/{id}/ping")
    public ResponseEntity<java.util.Map<String, Object>> ping(@PathVariable Long id) {
        java.util.List<java.util.Map<String, Object>> tools = mcpService.getToolDetails(id);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", tools.isEmpty() ? "error" : "success");
        result.put("tools", tools);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importConfigs(@RequestBody String json) {
        mcpService.importFromJson(json);
        return ResponseEntity.ok().build();
    }
}
