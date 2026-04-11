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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BuiltinWorkspaceToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(BuiltinWorkspaceToolProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> BUILTIN_TOOLS = Set.of("execute_command", "read_file", "write_file");

    private final Path workspaceRoot;
    private final Set<String> enabledTools;

    public BuiltinWorkspaceToolProvider(Path workspaceRoot, Set<String> enabledTools) {
        this.workspaceRoot = resolveWorkspaceRoot(workspaceRoot);
        Set<String> normalizedTools = normalizeEnabledTools(enabledTools);
        this.enabledTools = normalizedTools.isEmpty() ? Set.of("execute_command") : normalizedTools;
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

        if (command.isBlank()) {
            return errorJson("command is required");
        }

        Path workingDir = workdirValue.isBlank() ? workspaceRoot : safeResolvePath(workdirValue, true);
        if (workingDir == null) {
            return errorJson("workdir must be inside workspace root");
        }

        String shell = detectShell();
        List<String> processCommand = shell.contains("pwsh")
                ? List.of("pwsh", "-NoProfile", "-Command", command)
                : List.of("powershell", "-NoProfile", "-Command", command);

        try {
            ProcessBuilder builder = new ProcessBuilder(processCommand);
            builder.directory(workingDir.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            byte[] outputBytes = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            String output = new String(outputBytes, StandardCharsets.UTF_8);

            Map<String, Object> response = new HashMap<>();
            response.put("success", exitCode == 0);
            response.put("exitCode", exitCode);
            response.put("workingDir", workingDir.toString());
            response.put("command", command);
            response.put("output", output);
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
        String content = stringArg(args, "content");
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
                .description("Execute a PowerShell or shell command inside the workspace. Returns exit code and combined output as JSON.")
                .parameters(JsonObjectSchema.builder()
                        .description("Command execution request")
                        .addStringProperty("command", "The command to run.")
                        .addStringProperty("workdir", "Optional working directory inside the workspace.")
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
        Object value = args.get(name);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Path resolveWorkspaceRoot(Path configuredRoot) {
        if (configuredRoot != null && Files.exists(configuredRoot.resolve(".lingshu").resolve("skills"))) {
            return configuredRoot;
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
            if (!candidate.startsWith(workspaceRoot.normalize().toAbsolutePath())) {
                return null;
            }
            if (!directoryAllowed && Files.isDirectory(candidate)) {
                return null;
            }
            return candidate;
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
        return "pwsh";
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
}
