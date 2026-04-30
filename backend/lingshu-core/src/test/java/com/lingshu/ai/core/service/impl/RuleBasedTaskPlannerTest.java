package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.task.PlanStepType;
import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedTaskPlannerTest {

    private final RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();

    @Test
    void shouldGenerateFixPlan() {
        TaskRun run = taskRun("修复 demo 项目单测失败", "npm");
        TaskPlan plan = planner.plan(run);

        assertEquals("fix", plan.intentLabel());
        assertEquals(3, plan.stepCount());
        assertEquals(PlanStepType.SCAN_WORKSPACE, plan.steps().get(0).type());
        assertEquals(PlanStepType.READ_KEY_FILES, plan.steps().get(1).type());
        assertEquals(PlanStepType.EXECUTE_COMMAND, plan.steps().get(2).type());
    }

    @Test
    void shouldGenerateAnalyzePlan() {
        TaskRun run = taskRun("分析一下这个项目结构", "go");
        TaskPlan plan = planner.plan(run);

        assertEquals("analyze", plan.intentLabel());
        assertTrue(plan.stepCount() >= 2);
    }

    @Test
    void shouldGenerateBuildPlan() {
        TaskRun run = taskRun("编译并打包前端项目", "npm");
        TaskPlan plan = planner.plan(run);

        assertEquals("build", plan.intentLabel());
        assertEquals(PlanStepType.SCAN_WORKSPACE, plan.steps().get(0).type());
    }

    @Test
    void shouldGenerateAddFilePlan() {
        TaskRun run = taskRun("添加一个 README 文件到项目根目录", "shell");
        TaskPlan plan = planner.plan(run);

        assertEquals("add_file", plan.intentLabel());
        assertTrue(plan.stepCount() >= 2);
    }

    @Test
    void shouldGenerateTestPlan() {
        TaskRun run = taskRun("跑一下单元测试", "mvn");
        TaskPlan plan = planner.plan(run);

        assertEquals("test", plan.intentLabel());
        assertEquals(PlanStepType.SCAN_WORKSPACE, plan.steps().get(0).type());
        assertEquals(PlanStepType.EXECUTE_COMMAND, plan.steps().get(1).type());
    }

    @Test
    void shouldFallbackToGeneralWhenUnrecognized() {
        TaskRun run = taskRun("hello world", "node");
        TaskPlan plan = planner.plan(run);

        assertEquals("general", plan.intentLabel());
        assertEquals(3, plan.stepCount());
    }

    @Test
    void shouldHandleEmptyRequestText() {
        TaskRun run = TaskRun.builder()
                .id(99L)
                .userId("test")
                .title("untitled")
                .workspacePath("D:\\test")
                .commandCategory("shell")
                .requestText("")
                .state(TaskRunState.RUNNING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        TaskPlan plan = planner.plan(run);

        assertNotNull(plan);
        assertEquals("empty_request", plan.intentLabel());
    }

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
