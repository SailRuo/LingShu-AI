package com.lingshu.ai.core.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Component
public class LocalTools {

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
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
