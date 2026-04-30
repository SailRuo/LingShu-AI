package com.lingshu.ai.core.model.task;

import java.util.Collections;
import java.util.Map;

/**
 * One step in a {@link TaskPlan}. Steps are executed sequentially by the
 * {@link com.lingshu.ai.core.service.TaskExecutionEngine}.
 *
 * @param type        the well-known step type the engine can dispatch on
 * @param description human-readable label shown in the task card UI
 * @param params      optional overrides (e.g. {@code command} for EXECUTE_COMMAND)
 */
public record PlanStep(
        PlanStepType type,
        String description,
        Map<String, String> params
) {
    public PlanStep {
        params = params == null ? Collections.emptyMap() : Map.copyOf(params);
    }

    public static PlanStep of(PlanStepType type, String description) {
        return new PlanStep(type, description, Collections.emptyMap());
    }

    public static PlanStep of(PlanStepType type, String description, Map<String, String> params) {
        return new PlanStep(type, description, params);
    }
}
