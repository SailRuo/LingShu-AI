package com.lingshu.ai.core.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.FileSystemSkill;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CliSkillProvider implements ToolProvider {

    private final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
    private final String skillName;

    public CliSkillProvider(FileSystemSkill skill) {
        this.skillName = skill.name();
        parseSkill(skill);
    }

    public static CliSkillProvider fromSkill(FileSystemSkill skill) {
        return new CliSkillProvider(skill);
    }

    private void parseSkill(FileSystemSkill skill) {
        String content = skill.content();
        Pattern pattern = Pattern.compile("\\|\\s*`([^`]+)`\\s*\\|\\s*([^|]+)\\|");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String fullCommand = matcher.group(1).trim();
            String description = matcher.group(2).trim();

            String toolName = fullCommand.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();

            ToolSpecification spec = ToolSpecification.builder()
                    .name(toolName)
                    .description(description + " (命令: " + fullCommand + ")")
                    .build();

            ToolExecutor executor = (request, memoryId) -> executeCommand(fullCommand, request);

            tools.put(spec, executor);
            log.info("已从 Skill [{}] 自动生成工具: {} -> {}", skillName, toolName, fullCommand);
        }
    }

    private String executeCommand(String baseCommand, ToolExecutionRequest request) {
        Map<String, Object> arguments = request.arguments() != null ? 
            dev.langchain4j.internal.Json.fromJson(request.arguments(), Map.class) : Collections.emptyMap();
        
        String extraArgs = (String) arguments.getOrDefault("args", "");
        String fullCommand = baseCommand + " " + extraArgs;

        log.info("正在执行 Skill [{}] 命令: {}", skillName, fullCommand);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd.exe", "/c", fullCommand);
            } else {
                processBuilder.command("sh", "-c", fullCommand);
            }

            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                StringBuilder error = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                }
                return "命令执行失败 (退出码 " + exitCode + "):\n" + error.toString();
            }

            return output.toString().trim();
        } catch (Exception e) {
            log.error("执行命令失败: {}", fullCommand, e);
            return "执行命令时发生异常: " + e.getMessage();
        }
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        if (tools.isEmpty()) {
            return null;
        }
        return ToolProviderResult.builder()
                .addAll(tools)
                .build();
    }
}
