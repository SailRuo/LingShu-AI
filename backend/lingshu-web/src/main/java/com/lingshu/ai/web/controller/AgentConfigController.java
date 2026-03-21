package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.AgentConfigService;
import com.lingshu.ai.infrastructure.entity.AgentConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentConfigController {

    private final AgentConfigService agentConfigService;

    public AgentConfigController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    @GetMapping
    public List<AgentConfig> getAllAgents() {
        return agentConfigService.getAllAgents();
    }

    @GetMapping("/active")
    public List<AgentConfig> getActiveAgents() {
        return agentConfigService.getActiveAgents();
    }

    @GetMapping("/default")
    public ResponseEntity<AgentConfig> getDefaultAgent() {
        return agentConfigService.getDefaultAgent()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentConfig> getAgentById(@PathVariable Long id) {
        return agentConfigService.getAgentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<AgentConfig> getAgentByName(@PathVariable String name) {
        return agentConfigService.getAgentByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AgentConfig> createAgent(@RequestBody AgentConfig agent) {
        try {
            return ResponseEntity.ok(agentConfigService.createAgent(agent));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentConfig> updateAgent(@PathVariable Long id, @RequestBody AgentConfig agent) {
        try {
            return ResponseEntity.ok(agentConfigService.updateAgent(id, agent));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable Long id) {
        try {
            agentConfigService.deleteAgent(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/set-default")
    public ResponseEntity<AgentConfig> setAsDefault(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(agentConfigService.setAsDefault(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
