package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.model.task.PlanStep;
import com.lingshu.ai.core.model.task.PlanStepType;
import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.core.service.TaskPlanner;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule-based (non-LLM) planner that decomposes task requests via keyword matching.
 * <p>
 * Intent categories and their corresponding plans:
 * <ul>
 *   <li><b>fix / test</b> — scan, read key files, run test command</li>
 *   <li><b>analyze / inspect</b> — scan, read key files</li>
 *   <li><b>build / compile</b> — scan, read key files, run build command</li>
 *   <li><b>add / create / write</b> — scan, read key files, run status check</li>
 *   <li><b>general</b> — scan, read key files, run version check</li>
 * </ul>
 */
@Service
public class RuleBasedTaskPlanner implements TaskPlanner {

    @Override
    public TaskPlan plan(TaskRun run) {
        String text = run.getRequestText();
        if (text == null || text.isBlank()) {
            return fallbackPlan(run, "empty_request");
        }

        String lower = text.toLowerCase(Locale.ROOT);
        String category = run.getCommandCategory() != null ? run.getCommandCategory() : "";

        if (matchesAny(lower, "修复", "fix", "改", "修", "debug", "解决")) {
            return fixPlan(run, category);
        }
        if (matchesAny(lower, "分析", "analyze", "看看", "了解", "检查", "审视", "查看", "审查")) {
            return analyzePlan(run, category);
        }
        if (matchesAny(lower, "编译", "compile", "build", "构建", "打包", "部署", "deploy")) {
            return buildPlan(run, category);
        }
        if (matchesAny(lower, "添加", "add", "创建", "create", "加", "写", "write", "生成", "generate")) {
            return addFilePlan(run, category);
        }
        if (matchesAny(lower, "测试", "test", "跑测试", "验证", "单测", "单元测试")) {
            return testOnlyPlan(run, category);
        }
        return fallbackPlan(run, "general");
    }

    // ── intent plans ──────────────────────────────────────────────────

    private TaskPlan fixPlan(TaskRun run, String category) {
        String testCmd = buildTestCommand(category);
        return buildPlan(run, "fix", "Fix / debug workflow",
                List.of(
                        PlanStep.of(PlanStepType.SCAN_WORKSPACE, "扫描工作区目录结构"),
                        PlanStep.of(PlanStepType.READ_KEY_FILES, "识别并读取项目关键文件"),
                        PlanStep.of(PlanStepType.EXECUTE_COMMAND, "运行测试命令",
                                testCmd.isBlank() ? Map.of() : Map.of("command", testCmd))
                ));
    }

    private TaskPlan analyzePlan(TaskRun run, String category) {
        String cmd = buildVersionCommand(category);
        List<PlanStep> steps = new ArrayList<>();
        steps.add(PlanStep.of(PlanStepType.SCAN_WORKSPACE, "扫描工作区目录结构"));
        steps.add(PlanStep.of(PlanStepType.READ_KEY_FILES, "识别并读取项目关键文件"));
        if (!cmd.isBlank()) {
            steps.add(PlanStep.of(PlanStepType.EXECUTE_COMMAND, "运行版本/状态检查",
                    Map.of("command", cmd)));
        }
        return buildPlan(run, "analyze", "Project analysis workflow", steps);
    }

    private TaskPlan buildPlan(TaskRun run, String category) {
        String buildCmd = buildBuildCommand(category);
        return buildPlan(run, "build", "Build / compile workflow",
                List.of(
                        PlanStep.of(PlanStepType.SCAN_WORKSPACE, "扫描工作区目录结构"),
                        PlanStep.of(PlanStepType.READ_KEY_FILES, "识别并读取项目关键文件"),
                        PlanStep.of(PlanStepType.EXECUTE_COMMAND, "运行构建命令",
                                buildCmd.isBlank() ? Map.of() : Map.of("command", buildCmd))
                ));
    }

    private TaskPlan addFilePlan(TaskRun run, String category) {
        String cmd = buildStatusCommand(category);
        return buildPlan(run, "add_file", "Add / create file workflow",
                List.of(
                        PlanStep.of(PlanStepType.SCAN_WORKSPACE, "扫描工作区目录结构"),
                        PlanStep.of(PlanStepType.READ_KEY_FILES, "识别并读取项目关键文件"),
                        PlanStep.of(PlanStepType.EXECUTE_COMMAND, "检查当前项目状态",
                                cmd.isBlank() ? Map.of() : Map.of("command", cmd))
                ));
    }

    private TaskPlan testOnlyPlan(TaskRun run, String category) {
        String testCmd = buildTestCommand(category);
        return buildPlan(run, "test", "Run tests workflow",
                List.of(
                        PlanStep.of(PlanStepType.SCAN_WORKSPACE, "扫描工作区目录结构"),
                        PlanStep.of(PlanStepType.EXECUTE_COMMAND, "运行测试命令",
                                testCmd.isBlank() ? Map.of() : Map.of("command", testCmd))
                ));
    }

    private TaskPlan fallbackPlan(TaskRun run, String intent) {
        String cmd = buildVersionCommand(run.getCommandCategory() != null ? run.getCommandCategory() : "");
        return buildPlan(run, intent, "General project analysis",
                List.of(
                        PlanStep.of(PlanStepType.SCAN_WORKSPACE, "扫描工作区目录结构"),
                        PlanStep.of(PlanStepType.READ_KEY_FILES, "识别并读取项目关键文件"),
                        PlanStep.of(PlanStepType.EXECUTE_COMMAND, "运行版本检查",
                                cmd.isBlank() ? Map.of() : Map.of("command", cmd))
                ));
    }

    // ── helpers ───────────────────────────────────────────────────────

    private TaskPlan buildPlan(TaskRun run, String intentLabel, String summary, List<PlanStep> steps) {
        return new TaskPlan(steps, summary, intentLabel, LocalDateTime.now());
    }

    private static boolean matchesAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ── command builders ──────────────────────────────────────────────

    private String buildTestCommand(String category) {
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "npm" -> "npm test 2>&1 || npm run test 2>&1 || npm --version";
            case "mvn", "maven" -> "mvn test 2>&1 || mvn --version";
            case "cargo" -> "cargo test 2>&1 || cargo --version";
            case "go" -> "go test ./... 2>&1 || go version";
            case "python", "python3" -> "python -m pytest 2>&1 || python -m unittest discover 2>&1 || python --version";
            case "pip" -> "pip --version 2>&1";
            case "java" -> "java -version 2>&1";
            case "node" -> "node --version 2>&1";
            case "yarn" -> "yarn test 2>&1 || yarn --version";
            case "pnpm" -> "pnpm test 2>&1 || pnpm --version";
            default -> "";
        };
    }

    private String buildBuildCommand(String category) {
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "npm" -> "npm run build 2>&1 || npm --version";
            case "mvn", "maven" -> "mvn compile 2>&1 || mvn --version";
            case "cargo" -> "cargo build 2>&1 || cargo --version";
            case "go" -> "go build ./... 2>&1 || go version";
            default -> "";
        };
    }

    private String buildVersionCommand(String category) {
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "npm" -> "npm --version 2>&1 && npm ls --depth=0 2>&1 || npm --version";
            case "mvn", "maven" -> "mvn --version 2>&1";
            case "cargo" -> "cargo --version 2>&1";
            case "go" -> "go version 2>&1";
            case "java" -> "java -version 2>&1";
            case "node" -> "node --version 2>&1";
            case "python", "python3" -> "python --version 2>&1 || python3 --version 2>&1";
            case "git" -> "git status --short 2>&1 && git branch --show-current 2>&1 || git --version";
            default -> "";
        };
    }

    private String buildStatusCommand(String category) {
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "git" -> "git status --short 2>&1 && git branch --show-current 2>&1 || git --version";
            case "npm" -> "npm --version 2>&1";
            case "docker" -> "docker ps 2>&1 || docker --version";
            case "shell", "powershell", "pwsh" -> "ls -la 2>&1 || dir 2>&1";
            default -> buildVersionCommand(category);
        };
    }
}
