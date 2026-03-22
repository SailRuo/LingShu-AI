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

    @Tool("Reads the content of a local file")
    public String readLocalFile(String path) {
        log.info("Executing tool: readLocalFile with path: {}", path);
        try {
            return Files.readString(Paths.get(path));
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("Executes a terminal command and returns the output")
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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return output.toString() + "\n[Command timed out after 30 seconds]";
            }
            
            return output.toString();
        } catch (Exception e) {
            log.error("Error executing command: {}", command, e);
            return "Error executing command: " + e.getMessage();
        }
    }
}
