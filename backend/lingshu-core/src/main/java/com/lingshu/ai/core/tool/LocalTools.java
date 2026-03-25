package com.lingshu.ai.core.tool;

import com.lingshu.ai.core.service.SystemLogService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LocalTools {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalTools.class);

    /**
     * 工具输出最大字符数。
     * 防止大段输出反复灌入上下文，导致 ReAct 死循环。
     */
    private static final int MAX_OUTPUT_CHARS = 4000;

    /**
     * 单轮会话内，对“相同工具 + 相同输入”的重复调用阈值。
     * 一旦超过阈值，直接返回阻断信息，强制模型停止继续重复调用。
     */
    private static final int MAX_REPEAT_CALLS = 2;

    /**
     * 记录工具调用次数：
     * key = toolName + "::" + normalizedInput
     */
    private static final ConcurrentHashMap<String, AtomicInteger> TOOL_CALL_COUNTS = new ConcurrentHashMap<>();

    private final SystemLogService systemLogService;

    public LocalTools(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @Tool("""
            Reads the content of a local file.
            Do not repeatedly read the same path unless you are narrowing to a different target.
            If you already have enough information, stop calling tools and provide the final answer.
            """)
    public String readLocalFile(String path) {
        log.info("Executing tool: readLocalFile with path: {}", path);

        String normalizedPath = normalizeInput(path);
        if (shouldBlockRepeatedCall("readLocalFile", normalizedPath)) {
            String blocked = """
                    [TOOL_GUARD]
                    检测到你正在重复读取同一个文件路径，已阻止本次调用。
                    这通常意味着你陷入了工具调用循环。
                    如果你已经获得足够信息，请立即停止继续调用工具，并直接给出最终答复。
                    如果确实需要更多信息，请改为读取不同文件，或缩小目标范围后再继续。
                    """;
            systemLogService.warn("工具循环保护触发: readLocalFile | path=" + safePreview(path), "TOOL");
            return blocked;
        }

        systemLogService.info("ReAct工具调用: readLocalFile | path=" + safePreview(path), "TOOL");

        try {
            String content = Files.readString(Paths.get(path));
            String result = maybeTruncate(content, "文件内容");
            systemLogService.debug(
                    "ReAct工具结果: readLocalFile | chars=" + result.length() + " | preview=" + safePreview(result),
                    "TOOL");
            log.debug("ReAct工具结果: readLocalFile | chars={} | preview={}", result.length(), safePreview(result));

            return result;
        } catch (Exception e) {
            String error = "Error reading file: " + e.getMessage();
            systemLogService.error("ReAct工具失败: readLocalFile | " + e.getMessage(), "TOOL");
            log.error("ReAct工具失败: readLocalFile | {}", e.getMessage());
            return error;
        }
    }

    @Tool("""
            Executes a Windows terminal command and returns the output.
            This tool is for Windows commands only.
            Use commands compatible with cmd.exe on Windows, such as 'dir', 'tasklist', 'systeminfo', 'echo %USERNAME%'.
            Do not use Linux or macOS commands like 'ls', 'free', 'ps aux', or 'whoami' unless they are explicitly available in the current environment.
            The tool has exactly one argument: command.
            Tool arguments must always be valid JSON in the shape {"command":"..."}.
            When the Windows command itself contains double quotes, escape them inside JSON as \\\".
            Prefer simpler commands that avoid nested quoting when possible.
            For launching a Windows app, prefer PowerShell format like:
            {"command":"powershell -NoProfile -Command \"Start-Process -FilePath 'C:\\Program Files\\App\\app.exe'\""}
            Avoid malformed JSON such as {"command": "start "" "C:\\Program Files\\App\\app.exe""}.
            Output is capped to keep context manageable.
            Never repeat the same command over and over.
            If the command output already answers the question, stop tool usage and provide the final answer.
            """)
    public String executeCommand(String command) {
        String osName = System.getProperty("os.name");
        log.info("Executing tool: executeCommand with command: {} on {}", command, osName);

        String normalizedCommand = normalizeInput(command);
        if (shouldBlockRepeatedCall("executeCommand", normalizedCommand)) {
            String blocked = """
                    [TOOL_GUARD]
                    检测到你正在重复执行同一条命令，已阻止本次调用。
                    这通常意味着你陷入了 ReAct 工具循环。
                    请不要继续重复相同命令。
                    如果已有足够信息，请立即停止工具调用并给出最终答复；
                    如果信息不足，请改为执行更具体、更窄范围的命令。
                    """;
            systemLogService.warn("工具循环保护触发: executeCommand | command=" + safePreview(command), "TOOL");
            return blocked;
        }

        systemLogService.info("ReAct工具调用: executeCommand | command=" + safePreview(command), "TOOL");

        try {
            boolean isWindows = osName.toLowerCase().contains("win");
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (isWindows) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 识别字符集：Windows 下尝试识别 chcp 编码
            java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;
            if (isWindows) {
                try {
                    // 动态获取当前活动代码页
                    Process chcp = Runtime.getRuntime().exec("cmd.exe /c chcp");
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(chcp.getInputStream()))) {
                        String line = br.readLine();
                        if (line != null && line.contains(":")) {
                            String cp = line.split(":")[1].trim();
                            if ("936".equals(cp))
                                charset = java.nio.charset.Charset.forName("GBK");
                            else if ("65001".equals(cp))
                                charset = java.nio.charset.StandardCharsets.UTF_8;
                        }
                    }
                } catch (Exception e) {
                    log.warn("无法检测 Windows 代码页，回退到 GBK: {}", e.getMessage());
                    charset = java.nio.charset.Charset.forName("GBK"); // Windows 中文环境默认回退
                }
            }

            StringBuilder output = new StringBuilder();
            boolean truncated = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() + 1 > MAX_OUTPUT_CHARS) {
                        int remain = Math.max(0, MAX_OUTPUT_CHARS - output.length());
                        if (remain > 0) {
                            output.append(line, 0, Math.min(remain, line.length()));
                        }
                        truncated = true;
                        break;
                    }
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String result = output.toString();
                if (result.length() > MAX_OUTPUT_CHARS) {
                    result = result.substring(0, MAX_OUTPUT_CHARS);
                }
                String timedOut = result + "\n[⚠️ 命令执行超时（30秒），已强制终止。以上为截止超时前的输出。]";
                systemLogService.warn("ReAct工具超时: executeCommand | command=" + safePreview(command), "TOOL");
                return timedOut;
            }

            String result = output.toString();
            if (truncated) {
                result = result + "\n\n[⚠️ 命令输出已截断至 " + MAX_OUTPUT_CHARS
                        + " 字符。如需更多信息，请改用更精确的命令（如 grep、head、tail、限定目录或限定文件名）。]";
            }

            systemLogService.debug(
                    "ReAct工具结果: executeCommand | preview=" + safePreview(result),
                    "TOOL");
            return result;
        } catch (Exception e) {
            log.error("Error executing command: {}", command, e);
            systemLogService.error("ReAct工具失败: executeCommand | " + e.getMessage(), "TOOL");
            return "Error executing command: " + e.getMessage();
        }
    }

    private boolean shouldBlockRepeatedCall(String toolName, String normalizedInput) {
        String key = toolName + "::" + normalizedInput;
        int count = TOOL_CALL_COUNTS
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
        return count > MAX_REPEAT_CALLS;
    }

    private String normalizeInput(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("\\s+", " ");
    }

    private String maybeTruncate(String text, String label) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        log.warn("{} truncated from {} to {} chars", label, text.length(), MAX_OUTPUT_CHARS);
        return text.substring(0, MAX_OUTPUT_CHARS)
                + "\n\n[⚠️ " + label + "已截断，原始大小 "
                + text.length()
                + " 字符，仅展示前 "
                + MAX_OUTPUT_CHARS
                + " 字符。如需更多内容，请缩小范围后再次读取。]";
    }

    private String safePreview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String oneLine = text.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= 180) {
            return oneLine;
        }
        return oneLine.substring(0, 180) + "...";
    }
}
