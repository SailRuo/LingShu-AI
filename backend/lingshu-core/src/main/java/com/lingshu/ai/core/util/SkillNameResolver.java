package com.lingshu.ai.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class SkillNameResolver {

    private SkillNameResolver() {
    }

    public static String resolve(String toolName, String arguments, ObjectMapper objectMapper) {
        if (toolName == null) {
            return "";
        }
        if (!"activate_skill".equals(toolName) && !"read_skill_resource".equals(toolName)) {
            return "";
        }

        String skillName = extract(arguments, objectMapper, "skill_name");
        if (skillName.isBlank()) {
            skillName = extract(arguments, objectMapper, "skillName");
        }
        if (skillName.isBlank()) {
            skillName = extract(arguments, objectMapper, "skill");
        }
        if (skillName.isBlank()) {
            skillName = extract(arguments, objectMapper, "name");
        }
        return skillName;
    }

    private static String extract(String arguments, ObjectMapper objectMapper, String key) {
        if (arguments == null || arguments.isBlank() || objectMapper == null) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(arguments);
            if (node == null || !node.isObject()) {
                return "";
            }
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                return "";
            }
            return value.asText("").trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
