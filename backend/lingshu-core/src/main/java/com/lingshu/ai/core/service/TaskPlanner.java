package com.lingshu.ai.core.service;

import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.infrastructure.entity.TaskRun;

/**
 * Analyses a task request and produces a sequence of executable steps.
 * The resulting plan is stored in the task run so the execution engine
 * can follow it instead of running a fixed hard-coded workflow.
 */
public interface TaskPlanner {

    /**
     * Generate an execution plan for the given task run.
     * Implementations may be rule-based or LLM-backed.
     */
    TaskPlan plan(TaskRun run);
}
