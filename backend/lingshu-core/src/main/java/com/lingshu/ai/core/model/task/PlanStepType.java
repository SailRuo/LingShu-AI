package com.lingshu.ai.core.model.task;

/**
 * Types of steps a {@link com.lingshu.ai.core.service.TaskExecutionEngine}
 * can execute. The planner selects from these types — any type not in this
 * enum is considered a planning-only metadata step and will be skipped by
 * the execution engine.
 */
public enum PlanStepType {

    /** Walk the workspace directory tree and report the file listing. */
    SCAN_WORKSPACE,

    /** Identify known project key files and read their contents. */
    READ_KEY_FILES,

    /** Execute a shell command (category or override from params). */
    EXECUTE_COMMAND,

    /** Analyze previously collected context (content, logs, output). */
    ANALYZE_CONTENT,

    /** Generate or write output files based on context. */
    GENERATE_OUTPUT
}
