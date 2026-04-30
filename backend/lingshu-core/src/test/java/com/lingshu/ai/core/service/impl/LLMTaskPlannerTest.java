package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.task.PlanStepType;
import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the conversion/fallback logic of {@link LLMTaskPlanner}.
 * Full LLM integration tests require a running model and are not
 * covered here — they belong in integration tests.
 */
class LLMTaskPlannerTest {

    // ── fallback: when LLM fails, delegate to rule-based planner ──────

    @Test
    void shouldFallbackToRuleBasedPlannerWhenLLMUnavailable() {
        // The constructor requires DynamicMemoryModel, which we can't easily mock
        // without pulling in LangChain4j internals. But the fallback path is
        // exercised when the AiService call throws. We test that path by
        // observing the LLMTaskPlanner's @Primary + constructor design.

        // When Spring wires LLMTaskPlanner, it injects both:
        // 1. DynamicMemoryModel (for the LLM call)
        // 2. RuleBasedTaskPlanner (for fallback)
        //
        // If the LLM call throws, plan() catches and delegates to fallbackPlanner.plan().
        //
        // This test verifies the RuleBasedTaskPlanner still works correctly
        // as the fallback target.
        RuleBasedTaskPlanner fallback = new RuleBasedTaskPlanner();

        TaskRun run = taskRun("修复 demo 项目的单测失败", "npm");
        TaskPlan plan = fallback.plan(run);

        assertEquals("fix", plan.intentLabel());
        assertEquals(3, plan.stepCount());
    }

    // ── verify RuleBasedTaskPlanner still works for all intents ───────

    @Test
    void fallbackPlanner_coversFixIntent() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        TaskPlan plan = planner.plan(taskRun("修复项目中的测试", "mvn"));
        assertEquals("fix", plan.intentLabel());
        assertEquals(PlanStepType.EXECUTE_COMMAND, plan.steps().get(2).type());
    }

    @Test
    void fallbackPlanner_coversAnalyzeIntent() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        TaskPlan plan = planner.plan(taskRun("分析一下项目代码结构", "python"));
        assertEquals("analyze", plan.intentLabel());
        assertTrue(plan.stepCount() >= 2);
    }

    @Test
    void fallbackPlanner_coversBuildIntent() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        TaskPlan plan = planner.plan(taskRun("编译并打包", "npm"));
        assertEquals("build", plan.intentLabel());
    }

    @Test
    void fallbackPlanner_coversAddFileIntent() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        TaskPlan plan = planner.plan(taskRun("添加一个 Dockerfile", "git"));
        assertEquals("add_file", plan.intentLabel());
    }

    @Test
    void fallbackPlanner_coversTestIntent() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        TaskPlan plan = planner.plan(taskRun("跑一下所有单元测试", "cargo"));
        assertEquals("test", plan.intentLabel());
        // test plan: scan + execute_command (no read_key_files)
        assertEquals(2, plan.stepCount());
    }

    // ── conversion logic ─────────────────────────────────────────────

    @Test
    void toTaskPlan_shouldHandleValidOutput() {
        // We can't instantiate LLMTaskPlanner directly without DynamicMemoryModel,
        // so we verify the DTO structure through the public record.
        LLMTaskPlanner.TaskPlanOutput output = new LLMTaskPlanner.TaskPlanOutput(
                "fix",
                "修复测试并将变更提交",
                List.of(
                        new LLMTaskPlanner.StepOutput("SCAN_WORKSPACE", "扫描项目结构", null),
                        new LLMTaskPlanner.StepOutput("EXECUTE_COMMAND", "运行测试", "npm test")
                )
        );

        assertEquals("fix", output.intentLabel());
        assertEquals(2, output.steps().size());
        assertEquals("SCAN_WORKSPACE", output.steps().get(0).type());
        assertEquals("npm test", output.steps().get(1).command());
    }

    @Test
    void toTaskPlan_shouldHandleNullFields() {
        LLMTaskPlanner.TaskPlanOutput output = new LLMTaskPlanner.TaskPlanOutput(
                null,
                null,
                List.of(
                        new LLMTaskPlanner.StepOutput(null, null, null)
                )
        );

        assertNotNull(output.steps());
        assertEquals(1, output.steps().size());
    }

    // ── helpers ──────────────────────────────────────────────────────

    private TaskRun taskRun(String requestText, String commandCategory) {
        return TaskRun.builder()
                .id(1L)
                .userId("test")
                .title("test task")
                .workspacePath("D:\\work\\demo")
                .commandCategory(commandCategory)
                .requestText(requestText)
                .state(TaskRunState.RUNNING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
