package com.lingshu.ai.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinWorkspaceToolProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void executeCommand_shouldReportSeparateStdoutAndStderr() throws Exception {
        Assumptions.assumeTrue(canExecuteProviderShell());
        BuiltinWorkspaceToolProvider provider = new BuiltinWorkspaceToolProvider(
                Files.createTempDirectory("workspace-tool-provider"),
                Set.of("execute_command")
        );

        Map<String, Object> result = execute(provider,
                """
                {
                  "command":"Write-Output 'alpha'; [Console]::Error.Write('beta')"
                }
                """);

        String stdout = (String) result.get("stdout");
        assertEquals("beta", result.get("stderr"));
        assertFalse((Boolean) result.get("timedOut"));
        assertFalse((Boolean) result.get("truncated"));
        assertTrue(stdout.contains("alpha"));
        assertFalse(stdout.contains("beta"));
        assertEquals(stdout + "beta", result.get("output"));
    }

    @Test
    void executeCommand_shouldReportTimeoutAndTruncationTruthfully() throws Exception {
        Assumptions.assumeTrue(canExecuteProviderShell());
        BuiltinWorkspaceToolProvider provider = new BuiltinWorkspaceToolProvider(
                Files.createTempDirectory("workspace-tool-provider"),
                Set.of("execute_command")
        );

        Map<String, Object> result = execute(provider,
                """
                {
                  "command":"Write-Output ('o' * 10000); [Console]::Error.Write(('e' * 10000)); Start-Sleep -Seconds 5"
                }
                """);

        assertTrue((Boolean) result.get("timedOut"));
        assertTrue((Boolean) result.get("truncated"));
        assertTrue(((String) result.get("stdout")).length() < 10000);
        assertTrue(((String) result.get("stderr")).length() < 10000);
        assertEquals(((String) result.get("stdout")) + ((String) result.get("stderr")), result.get("output"));
    }

    @Test
    void writeFile_shouldPreserveExactContent() throws Exception {
        Path workspaceRoot = Files.createTempDirectory("workspace-tool-provider");
        BuiltinWorkspaceToolProvider provider = new BuiltinWorkspaceToolProvider(workspaceRoot, Set.of("write_file"));
        String content = "  keep leading and trailing  \nline two\n";

        Map<String, Object> result = invokeTool(provider, "writeFile",
                """
                {
                  "path":"notes.txt",
                  "content":"  keep leading and trailing  \\nline two\\n"
                }
                """);

        assertTrue((Boolean) result.get("success"));
        assertEquals(content, Files.readString(workspaceRoot.resolve("notes.txt")));
    }

    @Test
    void writeFile_shouldAllowNewNestedPathInsideWorkspace() throws Exception {
        Path workspaceRoot = Files.createTempDirectory("workspace-tool-provider");
        BuiltinWorkspaceToolProvider provider = new BuiltinWorkspaceToolProvider(workspaceRoot, Set.of("write_file"));

        Map<String, Object> result = invokeTool(provider, "writeFile",
                """
                {
                  "path":"new/subdir/output.txt",
                  "content":"created"
                }
                """);

        assertTrue((Boolean) result.get("success"));
        assertEquals("created", Files.readString(workspaceRoot.resolve("new").resolve("subdir").resolve("output.txt")));
    }

    @Test
    void constructor_shouldRespectExplicitWorkspaceRoot() throws Exception {
        Path workspaceRoot = Files.createTempDirectory("workspace-tool-provider-explicit");
        BuiltinWorkspaceToolProvider provider = new BuiltinWorkspaceToolProvider(workspaceRoot, Set.of("write_file"));

        Map<String, Object> result = invokeTool(provider, "writeFile",
                """
                {
                  "path":"explicit-root.txt",
                  "content":"ok"
                }
                """);

        assertTrue((Boolean) result.get("success"));
        assertTrue(Files.exists(workspaceRoot.resolve("explicit-root.txt")));
        assertNotEquals(workspaceRoot.resolve("explicit-root.txt").toAbsolutePath().normalize(),
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve("explicit-root.txt"));
    }

    @Test
    void safeResolvePath_shouldRejectSymlinkEscape() throws Exception {
        Assumptions.assumeTrue(canCreateSymlink());
        Path workspaceRoot = Files.createTempDirectory("workspace-tool-provider-root");
        Path outsideDir = Files.createTempDirectory("workspace-tool-provider-outside");
        Path outsideFile = outsideDir.resolve("secret.txt");
        Files.writeString(outsideFile, "secret");
        Path link = workspaceRoot.resolve("escape");
        Files.createSymbolicLink(link, outsideDir);
        BuiltinWorkspaceToolProvider provider = new BuiltinWorkspaceToolProvider(workspaceRoot, Set.of("read_file"));

        Map<String, Object> result = invokeTool(provider, "readFile",
                """
                {
                  "path":"escape/secret.txt"
                }
                """);

        assertFalse((Boolean) result.get("success"));
        assertEquals("path must be inside workspace root", result.get("error"));
    }

    private Map<String, Object> execute(BuiltinWorkspaceToolProvider provider, String argumentsJson) throws Exception {
        return invokeTool(provider, "executeCommand", argumentsJson);
    }

    private Map<String, Object> invokeTool(BuiltinWorkspaceToolProvider provider, String methodName, String argumentsJson) throws Exception {
        Method executeCommand = BuiltinWorkspaceToolProvider.class
                .getDeclaredMethod(methodName, ToolExecutionRequest.class, Object.class);
        executeCommand.setAccessible(true);
        String result = (String) executeCommand.invoke(provider, ToolExecutionRequest.builder()
                .id("call-1")
                .name("execute_command")
                .arguments(argumentsJson)
                .build(), null);
        return OBJECT_MAPPER.readValue(result, new TypeReference<>() {
        });
    }

    private boolean canCreateSymlink() {
        try {
            Path root = Files.createTempDirectory("workspace-tool-provider-symlink-check");
            Path target = Files.createTempDirectory("workspace-tool-provider-symlink-target");
            Path link = root.resolve("link");
            Files.createSymbolicLink(link, target);
            return Files.isSymbolicLink(link);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean canExecuteProviderShell() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) {
            if (Files.exists(Paths.get("C:\\Program Files\\PowerShell\\7\\pwsh.exe"))) {
                return true;
            }
            return canRunCommand("powershell", "-NoProfile", "-Command", "$PSVersionTable.PSVersion.ToString()");
        }
        return canRunCommand("pwsh", "-NoProfile", "-Command", "$PSVersionTable.PSVersion.ToString()");
    }

    private boolean canRunCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
