package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.core.model.task.PlanStep;
import com.lingshu.ai.core.model.task.PlanStepType;
import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.core.service.TaskPlanner;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * LLM-powered task planner that decomposes natural-language task requests
 * into concrete execution steps. Follows the same AiServices pattern used
 * by {@code EmotionAnalyzer}, {@code FactExtractor}, etc.
 * <p>
 * Falls back to {@link RuleBasedTaskPlanner} when the LLM call fails or
 * returns malformed output.
 */
@Service
@Primary
public class LLMTaskPlanner implements TaskPlanner {

    private static final Logger log = LoggerFactory.getLogger(LLMTaskPlanner.class);

    private static final String SYSTEM_PROMPT = """
            You are a task planner for an AI coding / dev-agent. Your ONLY job is to decompose a user's task request into a concrete, sequential plan of executable steps.

            ## Available step types

            - **SCAN_WORKSPACE** — Walk the project directory tree and list files.
            - **READ_KEY_FILES** — Identify and read the project's key configuration / build files (package.json, pom.xml, Cargo.toml, etc.).
            - **EXECUTE_COMMAND** — Run a real shell / terminal command inside the workspace. Must include a `command` field.
            - **ANALYZE_CONTENT** — Analyse collected context (file contents, command output). The execution engine currently SKIPS this type, but including it provides informative logging for the user.
            - **GENERATE_OUTPUT** — Generate / write files based on context. The execution engine currently SKIPS this type, but including it provides informative logging for the user.

            ## Planning rules

            1. EVERY plan MUST start with SCAN_WORKSPACE. You cannot analyse or act on a project you haven't scanned.
            2. READ_KEY_FILES should come second (unless the request is purely "rename this file" — then skip it).
            3. EXECUTE_COMMAND steps MUST use real, concrete commands appropriate for the specified category and context. Never use placeholder commands like "run tests" — write "npm test" or "cargo test".
            4. The command in EXECUTE_COMMAND should be a single shell line. Stderr redirection (`2>&1`) is handled by the engine automatically.
            5. ANALYZE_CONTENT and GENERATE_OUTPUT steps are informational checkpoints. Use them to mark where deeper analysis or content generation will happen.
            6. Limit to 3–5 steps. Be precise, not verbose.
            7. The intent label should be one short word that captures the essence of the request: "fix", "analyze", "build", "test", "add", "refactor", "deploy", "inspect", "setup", "document", "investigate", "general".

            ## Output format

            Return ONLY valid JSON — no markdown fences, no backticks, no explanatory text. The JSON must match this structure exactly:

            {
              "intentLabel": "fix",
              "summary": "one-line Chinese description of the plan",
              "steps": [
                {"type": "SCAN_WORKSPACE", "description": "扫描项目目录结构"},
                {"type": "READ_KEY_FILES", "description": "读取 package.json 和关键配置文件"},
                {"type": "EXECUTE_COMMAND", "description": "运行单元测试", "command": "npm test"},
                {"type": "ANALYZE_CONTENT", "description": "分析测试失败原因"}
              ]
            }

            The `command` field is ONLY required for EXECUTE_COMMAND steps. For all other step types, omit the `command` field entirely.
            """;

    private final TaskPlanAiService aiService;
    private final RuleBasedTaskPlanner fallbackPlanner;

    public LLMTaskPlanner(DynamicMemoryModel dynamicMemoryModel, RuleBasedTaskPlanner fallbackPlanner) {
        this.aiService = AiServices.builder(TaskPlanAiService.class)
                .chatModel(dynamicMemoryModel)
                .build();
        this.fallbackPlanner = fallbackPlanner;
    }

    @Override
    public TaskPlan plan(TaskRun run) {
        try {
            String request = buildRequestText(run);
            TaskPlanOutput output = aiService.plan(request, SYSTEM_PROMPT);
            TaskPlan plan = toTaskPlan(output);
            log.info("LLM planner generated plan [{}] {} {} step(s) for run {}",
                    plan.intentLabel(), plan.summary(), plan.stepCount(), run.getId());
            return plan;
        } catch (Exception e) {
            log.warn("LLM planning failed for run {}, falling back to rule-based planner: {}",
                    run.getId(), e.getMessage());
            return fallbackPlanner.plan(run);
        }
    }

    // ── internal AiServices interface ──────────────────────────────────

    /**
     * AiServices contract. The framework reads {@link SystemMessage} and
     * {@link UserMessage} annotations, then calls the LLM and deserializes
     * the JSON response into {@link TaskPlanOutput}.
     */
    @FunctionalInterface
    interface TaskPlanAiService {
        @SystemMessage("{{systemPrompt}}")
        TaskPlanOutput plan(@UserMessage String request, @V("systemPrompt") String systemPrompt);
    }

    /**
     * Mirror of the JSON the LLM is instructed to return.
     */
    record TaskPlanOutput(String intentLabel, String summary, List<StepOutput> steps) {
    }

    record StepOutput(String type, String description, String command) {
    }

    // ── conversion ────────────────────────────────────────────────────

    private String buildRequestText(TaskRun run) {
        return String.format("""
                Task request: %s
                Workspace path: %s
                Command category: %s
                """,
                run.getRequestText(),
                run.getWorkspacePath(),
                run.getCommandCategory() != null ? run.getCommandCategory() : "(not specified)");
    }

    private TaskPlan toTaskPlan(TaskPlanOutput output) {
        List<PlanStep> steps = output.steps().stream()
                .map(this::toPlanStep)
                .toList();
        return new TaskPlan(steps,
                output.summary() != null ? output.summary() : "",
                output.intentLabel() != null ? output.intentLabel() : "general",
                LocalDateTime.now());
    }

    private PlanStep toPlanStep(StepOutput s) {
        PlanStepType type = parseStepType(s.type());
        String description = s.description() != null ? s.description() : s.type();
        Map<String, String> params = (s.command() != null && !s.command().isBlank())
                ? Map.of("command", s.command().trim())
                : Map.of();
        return new PlanStep(type, description, params);
    }

    private PlanStepType parseStepType(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlanStepType.SCAN_WORKSPACE;
        }
        try {
            return PlanStepType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown step type from LLM: '{}', defaulting to SCAN_WORKSPACE", raw);
            return PlanStepType.SCAN_WORKSPACE;
        }
    }
}
