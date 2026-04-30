package com.lingshu.ai.core.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingshu.ai.core.model.task.PlanStep;
import com.lingshu.ai.core.model.task.PlanStepType;
import com.lingshu.ai.core.model.task.TaskPlan;
import com.lingshu.ai.core.service.TaskEventStreamService;
import com.lingshu.ai.core.service.TaskExecutionEngine;
import com.lingshu.ai.core.service.TaskPlanner;
import com.lingshu.ai.infrastructure.entity.TaskRun;
import com.lingshu.ai.infrastructure.repository.TaskRunRepository;
import com.lingshu.ai.infrastructure.task.TaskEventType;
import com.lingshu.ai.infrastructure.task.TaskRunState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class TaskExecutionEngineImpl implements TaskExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionEngineImpl.class);

    private static final int SCAN_MAX_DEPTH = 4;
    private static final int SCAN_MAX_FILES = 200;
    private static final long COMMAND_TIMEOUT_SECONDS = 30;
    private static final int FILE_READ_MAX_CHARS = 3000;
    private static final int OUTPUT_TRUNCATE_CHARS = 4000;

    private static final Set<String> KEY_FILE_PATTERNS = Set.of(
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts", "Cargo.toml", "Makefile",
            "README.md", "README", ".gitignore", "docker-compose.yml",
            "docker-compose.yaml", "Dockerfile", ".env.example", ".env.template",
            "tsconfig.json", "vite.config.ts", "vite.config.js",
            "tailwind.config.js", "tailwind.config.ts", ".eslintrc.json",
            ".eslintrc.js", ".prettierrc", "application.yml", "application.properties",
            "babel.config.js", "jest.config.js", "jest.config.ts",
            "next.config.js", "next.config.ts", "nuxt.config.js", "nuxt.config.ts",
            "vue.config.js", "webpack.config.js",
            "requirements.txt", "setup.py", "setup.cfg", "pyproject.toml"
    );

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            "node_modules", ".git", "target", "build", "__pycache__",
            ".idea", ".vscode", ".gradle", "dist", ".next", ".nuxt"
    );

    private final Executor taskExecutor;
    private final TaskEventStreamService taskEventStreamService;
    private final TaskRunRepository taskRunRepository;
    private final TaskPlanner taskPlanner;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, TaskHandle> activeRuns = new ConcurrentHashMap<>();

    public TaskExecutionEngineImpl(@Qualifier("taskExecutor") Executor taskExecutor,
                                   TaskEventStreamService taskEventStreamService,
                                   TaskRunRepository taskRunRepository,
                                   TaskPlanner taskPlanner) {
        this.taskExecutor = taskExecutor;
        this.taskEventStreamService = taskEventStreamService;
        this.taskRunRepository = taskRunRepository;
        this.taskPlanner = taskPlanner;
        this.objectMapper = new ObjectMapper();
    }

    // ── public contract ────────────────────────────────────────────────

    @Override
    public void schedule(TaskRun run) {
        TaskHandle handle = new TaskHandle();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                executeWorkflow(run, handle);
            } catch (Exception e) {
                log.error("Task execution engine failed for run {}: {}", run.getId(), e.getMessage(), e);
                emitLog(run, "step", "execute_workflow", "message", "Execution engine error: " + e.getMessage());
                transitionState(run, TaskRunState.FAILED, "Execution engine error: " + e.getMessage());
            }
        }, taskExecutor);
        handle.setFuture(future);
        activeRuns.put(run.getId(), handle);
        future.whenComplete((unused, throwable) -> activeRuns.remove(run.getId(), handle));
    }

    @Override
    public void pause(TaskRun run) {
        cancel(run.getId());
    }

    @Override
    public void resume(TaskRun run) {
        schedule(run);
    }

    @Override
    public void stop(TaskRun run) {
        cancel(run.getId());
    }

    @Override
    public void restorePendingTasks() {
        taskRunRepository.findByState(TaskRunState.RUNNING).forEach(this::schedule);
    }

    // ── plan-driven workflow ────────────────────────────────────────────

    private record WfContext(
            Path workspaceRoot,
            List<String> scannedFiles,
            Map<String, String> keyFileContents,
            String lastCommandOutput
    ) {
        WfContext withScanned(List<String> files) {
            return new WfContext(workspaceRoot, List.copyOf(files), keyFileContents, lastCommandOutput);
        }

        WfContext withKeys(Map<String, String> contents) {
            return new WfContext(workspaceRoot, scannedFiles, Map.copyOf(contents), lastCommandOutput);
        }

        WfContext withOutput(String output) {
            return new WfContext(workspaceRoot, scannedFiles, keyFileContents, output);
        }
    }

    private void executeWorkflow(TaskRun run, TaskHandle handle) {
        Path workspaceRoot = Paths.get(run.getWorkspacePath()).toAbsolutePath().normalize();

        TaskPlan plan = taskPlanner.plan(run);
        storePlanInSnapshot(run, plan);

        emitLog(run,
                "step", "execution_plan",
                "message", String.format("[%s] %s — %d step(s)",
                        plan.intentLabel(), plan.summary(), plan.stepCount()),
                "intentLabel", plan.intentLabel(),
                "planSummary", plan.summary(),
                "stepCount", String.valueOf(plan.stepCount()));

        WfContext ctx = new WfContext(workspaceRoot, List.of(), Map.of(), "");

        for (PlanStep step : plan.steps()) {
            if (handle.isCancelled()) return;
            ctx = executeStep(run, handle, step, ctx);
            if (taskEnteredTerminalState(run)) return;
        }

        buildSummaryAndComplete(run, plan, ctx);
    }

    private boolean taskEnteredTerminalState(TaskRun run) {
        // reload from db to check whether a step already transitioned us to FAILED
        return taskRunRepository.findById(run.getId())
                .map(r -> r.getState() == TaskRunState.FAILED || r.getState() == TaskRunState.STOPPED)
                .orElse(false);
    }

    private WfContext executeStep(TaskRun run, TaskHandle handle, PlanStep step, WfContext ctx) {
        return switch (step.type()) {
            case SCAN_WORKSPACE -> ctx.withScanned(scanWorkspace(run, handle, ctx.workspaceRoot()));
            case READ_KEY_FILES -> ctx.withKeys(readKeyFiles(run, handle, ctx.workspaceRoot(), ctx.scannedFiles()));
            case EXECUTE_COMMAND -> {
                String override = step.params().getOrDefault("command", "");
                yield ctx.withOutput(executeCommand(run, handle, ctx.workspaceRoot(), override));
            }
            case ANALYZE_CONTENT, GENERATE_OUTPUT -> {
                emitLog(run,
                        "step", step.type().name(),
                        "message", "Not yet implemented: " + step.description(),
                        "status", "skipped");
                yield ctx;
            }
        };
    }

    // ── plan persistence ──────────────────────────────────────────────

    private void storePlanInSnapshot(TaskRun run, TaskPlan plan) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("intentLabel", plan.intentLabel());
            snapshot.put("summary", plan.summary());
            snapshot.put("steps", plan.steps().stream()
                    .map(s -> Map.of(
                            "type", s.type().name(),
                            "description", s.description(),
                            "params", s.params()))
                    .toList());
            run.setRuntimeSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize plan snapshot for run {}", run.getId(), e);
        }
    }

    // ── step 1: scan workspace ─────────────────────────────────────────

    private List<String> scanWorkspace(TaskRun run, TaskHandle handle, Path workspaceRoot) {
        String step = "scan_workspace";
        emitStepStart(run, step);
        emitLog(run, "step", step, "message", "Scanning workspace: " + workspaceRoot);

        if (!Files.exists(workspaceRoot)) {
            String msg = "Workspace path does not exist: " + workspaceRoot;
            emitLog(run, "step", step, "message", msg);
            transitionState(run, TaskRunState.FAILED, msg);
            return List.of();
        }
        if (!Files.isDirectory(workspaceRoot)) {
            String msg = "Workspace path is not a directory: " + workspaceRoot;
            emitLog(run, "step", step, "message", msg);
            transitionState(run, TaskRunState.FAILED, msg);
            return List.of();
        }

        try {
            List<String> files = Files.walk(workspaceRoot, SCAN_MAX_DEPTH)
                    .filter(p -> !isIgnoredPath(p, workspaceRoot))
                    .limit(SCAN_MAX_FILES)
                    .map(p -> relativize(workspaceRoot, p))
                    .collect(Collectors.toList());

            emitLog(run,
                    "step", step,
                    "message", "Found " + files.size() + " files/directories in workspace",
                    "fileCount", String.valueOf(files.size()));

            String topPreview = files.stream().limit(20).collect(Collectors.joining(", "));
            emitStepCompleted(run, step,
                    "fileCount", String.valueOf(files.size()),
                    "topFiles", topPreview);
            return files;
        } catch (IOException e) {
            log.error("Workspace scan failed for run {}: {}", run.getId(), e.getMessage(), e);
            emitLog(run, "step", step, "message", "Scan failed: " + e.getMessage());
            transitionState(run, TaskRunState.FAILED, "Workspace scan failed: " + e.getMessage());
            return List.of();
        }
    }

    // ── step 2: identify & read key files ──────────────────────────────

    private Map<String, String> readKeyFiles(TaskRun run, TaskHandle handle,
                                             Path workspaceRoot, List<String> scannedFiles) {
        String step = "read_key_files";
        emitStepStart(run, step);

        if (scannedFiles.isEmpty()) {
            emitLog(run, "step", step, "message", "No files to inspect, skipping key file identification");
            emitStepCompleted(run, step);
            return Map.of();
        }

        emitLog(run, "step", step, "message", "Identifying project key files...");

        List<String> matchedKeys = scannedFiles.stream()
                .filter(f -> KEY_FILE_PATTERNS.contains(Paths.get(f).getFileName().toString()))
                .limit(5)
                .collect(Collectors.toList());

        if (matchedKeys.isEmpty()) {
            emitLog(run, "step", step, "message", "No known key files matched; scanning for source directories instead");

            // fallback: report top-level directories
            List<String> topDirs = scannedFiles.stream()
                    .filter(f -> !f.contains("/") && !f.contains("\\"))
                    .limit(20)
                    .collect(Collectors.toList());
            emitLog(run, "step", step, "message", "Top-level entries: " + String.join(", ", topDirs));
            emitStepCompleted(run, step, "matchedFiles", "0");
            return Map.of();
        }

        emitLog(run, "step", step, "message",
                "Identified " + matchedKeys.size() + " key files: " + String.join(", ", matchedKeys));

        if (handle.isCancelled()) return Map.of();

        Map<String, String> contents = new LinkedHashMap<>();
        for (String relativePath : matchedKeys) {
            if (handle.isCancelled()) break;
            Path filePath = workspaceRoot.resolve(relativePath);
            try {
                if (Files.size(filePath) > 500_000) {
                    emitLog(run, "step", step, "message",
                            "Skipping large file: " + relativePath + " (" + Files.size(filePath) + " bytes)");
                    continue;
                }
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                String truncated = content.length() > FILE_READ_MAX_CHARS
                        ? content.substring(0, FILE_READ_MAX_CHARS) + "\n... (truncated)"
                        : content;
                contents.put(relativePath, truncated);

                emitLog(run,
                        "step", step,
                        "message", "Read " + relativePath + " (" + content.length() + " chars)",
                        "file", relativePath,
                        "size", String.valueOf(content.length()));
            } catch (IOException e) {
                emitLog(run, "step", step, "message",
                        "Failed to read " + relativePath + ": " + e.getMessage());
            }
        }

        emitStepCompleted(run, step, "filesRead", String.valueOf(contents.size()));
        return contents;
    }

    // ── step: execute command (category or plan override) ──────────────

    private String executeCommand(TaskRun run, TaskHandle handle, Path workspaceRoot, String planOverride) {
        String step = "execute_command";
        emitStepStart(run, step);

        String category = run.getCommandCategory();
        String command = chooseCommand(category, planOverride);

        if (command.isBlank()) {
            emitLog(run, "step", step, "message",
                    "No command available — category=" + (category != null ? category : "null")
                            + " override=" + (planOverride.isEmpty() ? "none" : planOverride));
            emitStepCompleted(run, step, "skipped", "true");
            return "";
        }

        emitLog(run,
                "step", step,
                "message", "Executing: " + command + "  [category: " + (category != null ? category : "") + "]",
                "command", command,
                "category", category != null ? category : "");

        if (handle.isCancelled()) return "";

        try {
            String shell = detectShell();
            List<String> processCmd = shell.contains("pwsh") || shell.contains("powershell")
                    ? List.of(shell, "-NoProfile", "-Command", command)
                    : List.of("sh", "-lc", command);

            ProcessBuilder builder = new ProcessBuilder(processCmd);
            builder.directory(workspaceRoot.toFile());
            builder.redirectErrorStream(true);

            Process process = builder.start();
            boolean timedOut = false;
            String output;

            try {
                timedOut = !process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (timedOut) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
                output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                return "Command interrupted by task cancellation";
            }

            int exitCode = timedOut ? -1 : process.exitValue();
            String truncated = output.length() > OUTPUT_TRUNCATE_CHARS
                    ? output.substring(0, OUTPUT_TRUNCATE_CHARS) + "\n... (output truncated)"
                    : output;

            emitLog(run,
                    "step", step,
                    "message", "Command finished (exit=" + exitCode + (timedOut ? ", TIMED OUT" : "") + ")",
                    "exitCode", String.valueOf(exitCode),
                    "timedOut", String.valueOf(timedOut),
                    "output", truncated);

            emitStepCompleted(run, step,
                    "exitCode", String.valueOf(exitCode),
                    "timedOut", String.valueOf(timedOut));

            return output;
        } catch (IOException e) {
            log.error("Command execution failed for run {}: {}", run.getId(), e.getMessage(), e);
            emitLog(run, "step", step, "message", "Command execution error: " + e.getMessage());
            emitStepCompleted(run, step, "error", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private String chooseCommand(String category, String planOverride) {
        if (planOverride != null && !planOverride.isBlank()) {
            return planOverride;
        }
        if (category == null || category.isBlank()) {
            return "";
        }
        return resolveCategoryCommand(category);
    }

    // ── summary and completion ─────────────────────────────────────────

    private void buildSummaryAndComplete(TaskRun run, TaskPlan plan, WfContext ctx) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intentLabel", plan.intentLabel());
        summary.put("planSummary", plan.summary());
        summary.put("stepCount", plan.stepCount());
        summary.put("workspaceRoot", ctx.workspaceRoot().toString());
        summary.put("scannedFileCount", ctx.scannedFiles().size());

        if (!ctx.scannedFiles().isEmpty()) {
            summary.put("topEntries", ctx.scannedFiles().stream().limit(15).collect(Collectors.toList()));
        }

        summary.put("keyFilesIdentified", ctx.keyFileContents().size());
        if (!ctx.keyFileContents().isEmpty()) {
            summary.put("keyFileNames", new ArrayList<>(ctx.keyFileContents().keySet()));
        }

        String cmdOutput = ctx.lastCommandOutput();
        if (cmdOutput != null && !cmdOutput.isEmpty()) {
            int previewLen = Math.min(cmdOutput.length(), 500);
            summary.put("commandOutputPreview", cmdOutput.substring(0, previewLen));
        }

        String summaryJson;
        try {
            summaryJson = objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            summaryJson = "{\"error\":\"failed to serialize summary\"}";
        }

        transitionState(run, TaskRunState.COMPLETED, summaryJson);
    }

    // ── state transition ───────────────────────────────────────────────

    private void transitionState(TaskRun run, TaskRunState targetState, String summary) {
        TaskRun managed = taskRunRepository.findById(run.getId()).orElse(null);
        if (managed == null) {
            log.warn("Cannot transition state for run {}: not found in database", run.getId());
            return;
        }

        managed.setState(targetState);
        managed.setUpdatedAt(LocalDateTime.now());
        if (summary != null && !summary.isBlank()) {
            managed.setRuntimeSnapshotJson(summary);
        }
        if (targetState == TaskRunState.COMPLETED || targetState == TaskRunState.FAILED
                || targetState == TaskRunState.STOPPED) {
            managed.setCompletedAt(LocalDateTime.now());
        }
        taskRunRepository.save(managed);

        TaskEventType eventType = targetState == TaskRunState.COMPLETED
                ? TaskEventType.TASK_COMPLETED
                : TaskEventType.TASK_FAILED;
        taskEventStreamService.appendEvent(run, eventType, Map.of(
                "summary", summary != null ? summary : ""
        ));
    }

    // ── event helpers ──────────────────────────────────────────────────

    private void emitStepStart(TaskRun run, String step) {
        taskEventStreamService.appendEvent(run, TaskEventType.STEP_STARTED,
                Map.of("step", step));
    }

    private void emitStepCompleted(TaskRun run, String step, String... kvPairs) {
        Map<String, Object> payload = buildPayload("step", step, kvPairs);
        taskEventStreamService.appendEvent(run, TaskEventType.STEP_COMPLETED, payload);
    }

    private void emitLog(TaskRun run, String... kvPairs) {
        Map<String, Object> payload = buildPayload(null, null, kvPairs);
        taskEventStreamService.appendEvent(run, TaskEventType.LOG, payload);
    }

    private Map<String, Object> buildPayload(String extraKey, String extraValue, String... kvPairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (extraKey != null) {
            payload.put(extraKey, extraValue);
        }
        if (kvPairs != null) {
            for (int i = 0; i + 1 < kvPairs.length; i += 2) {
                payload.put(kvPairs[i], kvPairs[i + 1]);
            }
        }
        return payload;
    }

    // ── filesystem helpers ─────────────────────────────────────────────

    private boolean isIgnoredPath(Path path, Path root) {
        if (path.equals(root)) {
            return false;
        }

        Path fileName = path.getFileName();
        if (fileName != null) {
            String name = fileName.toString();
            if (name.isEmpty()) return false;

            // ignore hidden files (except .gitignore, .env*, .eslintrc, .prettierrc)
            if (name.startsWith(".") && !name.equals(".gitignore")
                    && !name.startsWith(".env") && !name.equals(".eslintrc.json")
                    && !name.equals(".eslintrc.js") && !name.equals(".prettierrc")) {
                return true;
            }

            if (Files.isDirectory(path) && IGNORED_DIR_NAMES.contains(name)) {
                return true;
            }

            // ignore binary / large common patterns
            if (!Files.isDirectory(path)) {
                return name.endsWith(".exe") || name.endsWith(".dll")
                        || name.endsWith(".so") || name.endsWith(".dylib")
                        || name.endsWith(".jar") || name.endsWith(".war")
                        || name.endsWith(".class") || name.endsWith(".bin")
                        || name.endsWith(".png") || name.endsWith(".jpg")
                        || name.endsWith(".jpeg") || name.endsWith(".gif")
                        || name.endsWith(".ico") || name.endsWith(".svg");
            }
        }
        return false;
    }

    private String relativize(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.getFileName().toString();
        }
    }

    // ── command helpers ────────────────────────────────────────────────

    private String resolveCategoryCommand(String category) {
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "npm" -> "npm --version && npm ls --depth=0 2>&1 || npm --version";
            case "mvn", "maven" -> "mvn --version 2>&1 || echo 'mvn not found'";
            case "git" -> "git status --short 2>&1 && git branch --show-current 2>&1 || git --version";
            case "python", "python3" -> "python --version 2>&1 || python3 --version 2>&1";
            case "pip" -> "pip --version 2>&1 || pip3 --version 2>&1";
            case "java" -> "java -version 2>&1";
            case "cargo" -> "cargo --version 2>&1";
            case "go" -> "go version 2>&1";
            case "rustc" -> "rustc --version 2>&1";
            case "node" -> "node --version 2>&1";
            case "yarn" -> "yarn --version 2>&1 || echo 'yarn not found'";
            case "pnpm" -> "pnpm --version 2>&1 || echo 'pnpm not found'";
            case "docker" -> "docker --version 2>&1 && docker ps 2>&1 || docker --version";
            case "shell" -> "echo 'Shell ready at: '$(pwd) && ls -la 2>&1";
            case "powershell", "pwsh" -> "Get-Location; Get-ChildItem -Name 2>&1";
            default -> normalized + " --version 2>&1 || echo 'command not found'";
        };
    }

    private String detectShell() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            return Files.exists(Paths.get("C:\\Program Files\\PowerShell\\7\\pwsh.exe"))
                    ? "pwsh"
                    : "powershell";
        }
        return canExecute("pwsh", "-NoProfile", "-Command", "echo ok") ? "pwsh" : "sh";
    }

    private boolean canExecute(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ── lifecycle ──────────────────────────────────────────────────────

    private void cancel(Long taskRunId) {
        TaskHandle handle = activeRuns.remove(taskRunId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private static final class TaskHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private CompletableFuture<Void> future;

        void setFuture(CompletableFuture<Void> future) {
            this.future = future;
        }

        boolean isCancelled() {
            return cancelled.get() || Thread.currentThread().isInterrupted();
        }

        void cancel() {
            cancelled.set(true);
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
