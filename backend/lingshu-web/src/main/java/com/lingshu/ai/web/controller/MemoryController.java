package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.service.MemoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memory")
@CrossOrigin(origins = "*")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/graph")
    public Object getGraph() {
        // For now, use "User" as default
        return memoryService.getGraphData("User");
    }

    @PostMapping("/extract")
    public void manualExtract(@RequestBody String text) {
        memoryService.extractFacts("User", text);
    }

    @DeleteMapping("/fact/{id}")
    public void deleteFact(@PathVariable Long id) {
        memoryService.deleteFact(id);
    }
}
