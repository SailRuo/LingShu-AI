package com.lingshu.ai.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BuiltinWorkspaceToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(BuiltinWorkspaceToolProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> BUILTIN_TOOLS = Set.of("execute_command", "read_file", "write_file");
    private static final long COMMAND_TIMEOUT_MILLIS = 2_000L;
    private static final int COMMAND_OUTPUT_LIMIT = 4_000;

    private final Path workspaceRoot;
    private final Set<String> enabledTools;

    public BuiltinWorkspaceToolProvider(Path workspaceRoot, Set<String> enabledTools) {
        this.workspaceRoot = resolveWorkspaceRoot(workspaceRoot);
        this.enabledTools = normalizeEnabledTools(enabledTools);
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();

        if (enabledTools.contains("execute_command")) {
            tools.put(executeCommandSpec(), this::executeCommand);
        }
        if (enabledTools.contains("read_file")) {
            tools.put(readFileSpec(), this::readFile);
        }
        if (enabledTools.contains("write_file")) {
            tools.put(writeFileSpec(), this::writeFile);
        }

        if (tools.isEmpty()) {
            return null;
        }

        return ToolProviderResult.builder()
                .addAll(tools)
                .build();
    }

    private String executeCommand(dev.langchain4j.agent.tool.ToolExecutionRequest request, Object memoryId) {
        Map<String, Object> args = parseArguments(request.arguments());
        String command = stringArg(args, "command");
        String workdirValue = stringArg(args, "workdir");
        String requestedCommandCategory = stringArg(args, "commandCategory");

        if (command.isBlank()) {
            return errorJson("command is required");
        }

        String lowerCmd = command.toLowerCase(Locale.ROOT).trim();
        if (lowerCmd.startsWith("rm ") || lowerCmd.startsWith("rmdir ") || 
            lowerCmd.startsWith("del ") || lowerCmd.startsWith("erase ") ||
            lowerCmd.contains("remove-item") || lowerCmd.contains("clear-content") ||
            lowerCmd.contains("format ") || lowerCmd.contains("mkfs")) {
            return errorJson("Security restriction: command execution blocked due to dangerous keywords.");
        }

        Path workingDir = workdirValue.isBlank() ? workspaceRoot : safeResolvePath(workdirValue, true);
        if (workingDir == null) {
            return errorJson("workdir must be inside workspace root");
        }

        String shell = detectShell();
        List<String> processCommand = shell.contains("pwsh")
                ? List.of("pwsh", "-NoProfile", "-Command", command)
                : shell.contains("powershell")
                ? List.of("powershell", "-NoProfile", "-Command", command)
                : List.of("sh", "-lc", command);

        try {
            ProcessBuilder builder = new ProcessBuilder(processCommand);
            builder.directory(workingDir.toFile());
            Process process = builder.start();
            CompletableFuture<String> stdoutFuture = readStream(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStream(process.getErrorStream());
            boolean timedOut = !process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (timedOut) {
                process.destroyForcibly();
                process.waitFor();
            }
            int exitCode = process.exitValue();

            String stdout = stdoutFuture.join().replace("\u0000", "");
            String stderr = stderrFuture.join().replace("\u0000", "");
            OutputSlice stdoutSlice = truncateOutput(stdout);
            OutputSlice stderrSlice = truncateOutput(stderr);
            boolean truncated = stdoutSlice.truncated() || stderrSlice.truncated();
            String combinedOutput = stdoutSlice.value() + stderrSlice.value();

            Map<String, Object> response = new HashMap<>();
            response.put("success", exitCode == 0 && !timedOut);
            response.put("exitCode", exitCode);
            response.put("timedOut", timedOut);
            response.put("truncated", truncated);
            response.put("workingDir", workingDir.toString());
            response.put("command", command);
            response.put("commandCategory", detectCommandCategory(command, requestedCommandCategory));
            response.put("stdout", stdoutSlice.value());
            response.put("stderr", stderrSlice.value());
            response.put("output", combinedOutput);
            return toJson(response);
        } catch (Exception e) {
            log.error("execute_command failed: {}", e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    private String readFile(dev.langchain4j.agent.tool.ToolExecutionRequest request, Object memoryId) {
        Map<String, Object> args = parseArguments(request.arguments());
        String pathValue = stringArg(args, "path");
        if (pathValue.isBlank()) {
            return errorJson("path is required");
        }

        Path filePath = safeResolvePath(pathValue, false);
        if (filePath == null) {
            return errorJson("path must be inside workspace root");
        }

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("path", relativize(filePath));
            response.put("content", content);
            response.put("length", content.length());
            return toJson(response);
        } catch (IOException e) {
            log.error("read_file failed: {}", e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    private String writeFile(dev.langchain4j.agent.tool.ToolExecutionRequest request, Object memoryId) {
        Map<String, Object> args = parseArguments(request.arguments());
        String pathValue = stringArg(args, "path");
        String content = rawStringArg(args, "content");
        if (pathValue.isBlank()) {
            return errorJson("path is required");
        }

        Path filePath = safeResolvePath(pathValue, false);
        if (filePath == null) {
            return errorJson("path must be inside workspace root");
        }

        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("path", relativize(filePath));
            response.put("length", content.length());
            return toJson(response);
        } catch (IOException e) {
            log.error("write_file failed: {}", e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    private ToolSpecification executeCommandSpec() {
        return ToolSpecification.builder()
                .name("execute_command")
                .description("Execute local shell commands inside the workspace. This tool can perform any local command operation that is allowed by the built-in security whitelist (dangerous keywords are blocked). Returns exit code and combined output as JSON.")
                        .parameters(JsonObjectSchema.builder()
                        .description("Local command execution request (whitelisted and workspace-scoped).")
                        .addStringProperty("command", "The command to run. Prefer direct local commands for file/system operations within whitelist restrictions. For reading UTF-8 files on Windows, prefer: Get-Content -Raw -Encoding UTF8 <path>.")
                        .addStringProperty("workdir", "Optional working directory inside the workspace.")
                        .addStringProperty("commandCategory", "Optional normalized command category such as npm, mvn, git, python, powershell, or shell.")
                        .required(List.of("command"))
                        .additionalProperties(false)
                        .build())
                .build();
    }

    private ToolSpecification readFileSpec() {
        return ToolSpecification.builder()
                .name("read_file")
                .description("Read a UTF-8 text file from the workspace and return its contents as JSON.")
                .parameters(JsonObjectSchema.builder()
                        .description("File read request")
                        .addStringProperty("path", "Path to the file relative to the workspace or an absolute path within it.")
                        .required(List.of("path"))
                        .additionalProperties(false)
                        .build())
                .build();
    }

    private ToolSpecification writeFileSpec() {
        return ToolSpecification.builder()
                .name("write_file")
                .description("Write UTF-8 text to a file in the workspace. Creates parent directories if needed.")
                .parameters(JsonObjectSchema.builder()
                        .description("File write request")
                        .addStringProperty("path", "Path to the file relative to the workspace or an absolute path within it.")
                        .addStringProperty("content", "UTF-8 text content to write.")
                        .required(List.of("path", "content"))
                        .additionalProperties(false)
                        .build())
                .build();
    }

    private Map<String, Object> parseArguments(String arguments) {
        try {
            if (arguments == null || arguments.isBlank()) {
                return Map.of();
            }
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(arguments, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String stringArg(Map<String, Object> args, String name) {
        return rawStringArg(args, name).trim();
    }

    private String rawStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private Path resolveWorkspaceRoot(Path configuredRoot) {
        if (configuredRoot != null) {
            try {
                return configuredRoot.toAbsolutePath().normalize();
            } catch (Exception ignored) {
                // Fall through to discovery only when the explicit root is invalid.
            }
        }
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(current.resolve(".lingshu").resolve("skills"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve(".lingshu").resolve("skills"))) {
            return parent;
        }
        return current;
    }

    private Set<String> normalizeEnabledTools(Set<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tool : tools) {
            if (tool == null) {
                continue;
            }
            String value = tool.trim().toLowerCase(Locale.ROOT);
            if (BUILTIN_TOOLS.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private Path safeResolvePath(String rawPath, boolean directoryAllowed) {
        try {
            Path candidate = Paths.get(rawPath);
            if (!candidate.isAbsolute()) {
                candidate = workspaceRoot.resolve(candidate);
            }
            candidate = candidate.normalize().toAbsolutePath();
            Path rootBoundary = resolveBoundaryPath(workspaceRoot, true);
            Path candidateBoundary = resolveBoundaryPath(candidate, false);
            if (rootBoundary == null || candidateBoundary == null || !candidateBoundary.startsWith(rootBoundary)) {
                return null;
            }
            if (!directoryAllowed && Files.isDirectory(candidate)) {
                return null;
            }
            return candidateBoundary;
        } catch (Exception e) {
            return null;
        }
    }

    private String relativize(Path path) {
        try {
            return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toAbsolutePath().normalize().toString().replace('\\', '/');
        }
    }

    private String detectShell() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            return Files.exists(Paths.get("C:\\Program Files\\PowerShell\\7\\pwsh.exe")) ? "pwsh" : "powershell";
        }
        return canExecute("pwsh", "-NoProfile", "-Command", "echo ok") ? "pwsh" : "sh";
    }

    private String detectCommandCategory(String command, String requestedCommandCategory) {
        if (!requestedCommandCategory.isBlank()) {
            return requestedCommandCategory;
        }
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int firstSpace = trimmed.indexOf(' ');
        return (firstSpace >= 0 ? trimmed.substring(0, firstSpace) : trimmed).toLowerCase(Locale.ROOT);
    }

    private CompletableFuture<String> readStream(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return decodeProcessOutput(inputStream.readAllBytes());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read process stream", e);
            }
        });
    }

    private OutputSlice truncateOutput(String output) {
        if (output == null || output.length() <= COMMAND_OUTPUT_LIMIT) {
            return new OutputSlice(output == null ? "" : output, false);
        }
        return new OutputSlice(output.substring(0, COMMAND_OUTPUT_LIMIT), true);
    }

    private Path resolveBoundaryPath(Path path, boolean directoryAllowed) throws IOException {
        if (path == null) {
            return null;
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.exists(absolute)) {
            Path realPath = absolute.toRealPath();
            if (!directoryAllowed && Files.isDirectory(realPath)) {
                return null;
            }
            return realPath;
        }
        Path existingAncestor = nearestExistingAncestor(absolute);
        if (existingAncestor == null) {
            return null;
        }
        Path realAncestor = existingAncestor.toRealPath();
        Path relativeSuffix = realAncestor.relativize(realAncestor);
        Path unresolvedSuffix = existingAncestor.relativize(absolute);
        return realAncestor.resolve(relativeSuffix).resolve(unresolvedSuffix).normalize();
    }

    private Path nearestExistingAncestor(Path path) {
        Path current = path;
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        return current;
    }

    private boolean canExecute(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String decodeProcessOutput(byte[] outputBytes) {
        if (outputBytes == null || outputBytes.length == 0) {
            return "";
        }

        if (outputBytes.length >= 3
                && (outputBytes[0] & 0xFF) == 0xEF
                && (outputBytes[1] & 0xFF) == 0xBB
                && (outputBytes[2] & 0xFF) == 0xBF) {
            return new String(outputBytes, 3, outputBytes.length - 3, StandardCharsets.UTF_8);
        }

        if (outputBytes.length >= 2
                && (outputBytes[0] & 0xFF) == 0xFF
                && (outputBytes[1] & 0xFF) == 0xFE) {
            return new String(outputBytes, 2, outputBytes.length - 2, StandardCharsets.UTF_16LE);
        }

        if (outputBytes.length >= 2
                && (outputBytes[0] & 0xFF) == 0xFE
                && (outputBytes[1] & 0xFF) == 0xFF) {
            return new String(outputBytes, 2, outputBytes.length - 2, StandardCharsets.UTF_16BE);
        }

        String utf8 = new String(outputBytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }

        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            try {
                return new String(outputBytes, java.nio.charset.Charset.forName("GBK"));
            } catch (Exception ignored) {
                return utf8;
            }
        }

        return utf8;
    }

    private String toJson(Map<String, Object> response) {
        try {
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String errorJson(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error == null ? "unknown" : error);
        return toJson(response);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record OutputSlice(String value, boolean truncated) {
    }
}
