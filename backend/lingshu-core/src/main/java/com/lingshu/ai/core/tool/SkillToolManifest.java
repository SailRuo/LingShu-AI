package com.lingshu.ai.core.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillToolManifest {

    private static final Pattern TOOLS_BLOCK = Pattern.compile("^tools:\\s*$");
    private static final Pattern TOOL_ITEM = Pattern.compile("^\\s*-\\s*([A-Za-z0-9_\\-]+)\\s*$");

    private SkillToolManifest() {
    }

    public static Set<String> parseRequiredTools(Path skillDir) {
        if (skillDir == null) {
            return Set.of();
        }

        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            return Set.of();
        }

        try {
            List<String> lines = Files.readAllLines(skillFile);
            boolean inFrontMatter = false;
            boolean toolsSection = false;
            Set<String> tools = new LinkedHashSet<>();

            for (String line : lines) {
                if (line.trim().equals("---")) {
                    inFrontMatter = !inFrontMatter;
                    toolsSection = false;
                    continue;
                }

                if (!inFrontMatter) {
                    continue;
                }

                if (TOOLS_BLOCK.matcher(line).matches()) {
                    toolsSection = true;
                    continue;
                }

                if (toolsSection) {
                    Matcher item = TOOL_ITEM.matcher(line);
                    if (item.matches()) {
                        tools.add(item.group(1));
                        continue;
                    }
                    if (!line.startsWith(" ") && !line.startsWith("\t") && !line.isBlank()) {
                        toolsSection = false;
                    }
                }
            }
            return tools;
        } catch (Exception e) {
            return Set.of();
        }
    }
}
