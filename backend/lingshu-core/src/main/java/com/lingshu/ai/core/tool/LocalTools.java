package com.lingshu.ai.core.tool;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class LocalTools {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalTools.class);

    /**
     * 命令输出最大字符数。
     * 超出部分会被截断，并附加提示信息，防止大量输出撑爆 LLM 上下文，
     * 导致 ReAct 循环永不终止（exceeded 100 sequential tool invocations）。
     */
    private static final int MAX_OUTPUT_CHARS = 4000;

    @Tool("Reads the content of a local file")
    public String readLocalFile(String path) {
        log.info("Executing tool: readLocalFile with path: {}", path);
        try {
            String content = Files.readString(Paths.get(path));
            if (content.length() > MAX_OUTPUT_CHARS) {
                log.warn("readLocalFile: output truncated from {} to {} chars", content.length(), MAX_OUTPUT_CHARS);
                return content.substring(0, MAX_OUTPUT_CHARS)
                        + "\n\n[⚠️ 文件内容已截断，原始大小 " + content.length()
                        + " 字符，仅展示前 " + MAX_OUTPUT_CHARS + " 字符。如需查看更多内容，请缩小读取范围或分段读取。]";
            }
            return content;
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("Executes a terminal command and returns the output. " +
          "Output is capped at " + MAX_OUTPUT_CHARS + " characters to keep context manageable. " +
          "Once you have the information needed to answer the user, stop calling tools and provide your final response.")
    public String executeCommand(String command) {
        log.info("Executing tool: executeCommand with command: {}", command);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            // Use cmd /c for Windows to handle built-in commands and pipes
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }

            processBuilder.redirectErrorStream(true); // Combine stdout and stderr
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            boolean truncated = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Check before appending each line to avoid massive single-line outputs
                    if (output.length() + line.length() + 1 > MAX_OUTPUT_CHARS) {
                        output.append(line, 0, Math.max(0, MAX_OUTPUT_CHARS - output.length()));
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
                if (truncated || result.length() >= MAX_OUTPUT_CHARS) {
                    result = result.substring(0, Math.min(result.length(), MAX_OUTPUT_CHARS));
                }
                return result + "\n[⚠️ 命令执行超时（30秒），已强制终止。以上为截止超时前的输出。]";
            }

            String result = output.toString();
            if (truncated) {
                log.warn("executeCommand: output truncated at {} chars for command: {}", MAX_OUTPUT_CHARS, command);
                return result + "\n\n[⚠️ 命令输出已截断至 " + MAX_OUTPUT_CHARS
                        + " 字符。如需查看完整结果，请使用更精确的命令（如 grep、head、tail 等）缩小输出范围。]";
            }

            return result;
        } catch (Exception e) {
            log.error("Error executing command: {}", command, e);
            return "Error executing command: " + e.getMessage();
        }
    }
}
