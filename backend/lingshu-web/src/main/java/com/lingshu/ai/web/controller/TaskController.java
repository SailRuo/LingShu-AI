package com.lingshu.ai.web.controller;

import com.lingshu.ai.core.dto.task.TaskApprovalDecisionRequest;
import com.lingshu.ai.core.dto.task.TaskEventView;
import com.lingshu.ai.core.dto.task.TaskRunView;
import com.lingshu.ai.core.dto.task.TaskStartRequest;
import com.lingshu.ai.core.service.TaskRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRuntimeService taskRuntimeService;

    public TaskController(TaskRuntimeService taskRuntimeService) {
        this.taskRuntimeService = taskRuntimeService;
    }

    @PostMapping("/start")
    public TaskRunView start(@RequestBody TaskStartRequest request) {
        return taskRuntimeService.start(request);
    }

    @GetMapping("/{id}")
    public TaskRunView get(@PathVariable("id") Long id,
                           @RequestParam("userId") String userId) {
        return taskRuntimeService.get(id, userId);
    }

    @GetMapping("/{id}/events")
    public List<TaskEventView> getEvents(@PathVariable("id") Long id,
                                         @RequestParam("userId") String userId) {
        return taskRuntimeService.get(id, userId).events();
    }

    @PostMapping("/{id}/approve")
    public TaskRunView approve(@PathVariable("id") Long id,
                               @RequestParam("userId") String userId,
                               @RequestBody TaskApprovalDecisionRequest request) {
        return taskRuntimeService.approve(id, userId, request);
    }

    @PostMapping("/{id}/pause")
    public TaskRunView pause(@PathVariable("id") Long id,
                             @RequestParam("userId") String userId) {
        return taskRuntimeService.pause(id, userId);
    }

    @PostMapping("/{id}/resume")
    public TaskRunView resume(@PathVariable("id") Long id,
                              @RequestParam("userId") String userId) {
        return taskRuntimeService.resume(id, userId);
    }

    @PostMapping("/{id}/stop")
    public TaskRunView stop(@PathVariable("id") Long id,
                            @RequestParam("userId") String userId) {
        return taskRuntimeService.stop(id, userId);
    }
}
