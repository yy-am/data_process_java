package com.example.dataprocess.infrastructure.runtime;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技能运行时健康检查，校验技能文件和工具绑定是否一致。
 */
@Component
public class SkillRuntimeHealthCheck {

    private static final List<String> ALLOWED_TOOLS_HEADINGS = List.of(
            "## Allowed Tools",
            "## 允许使用的工具"
    );

    private final Map<String, List<String>> skillAllowedTools;

    public SkillRuntimeHealthCheck(Map<String, List<String>> skillAllowedTools) {
        this.skillAllowedTools = skillAllowedTools;
    }

    @PostConstruct
    void validateSkillDefinitions() {
        for (Map.Entry<String, List<String>> entry : skillAllowedTools.entrySet()) {
            String skillId = entry.getKey();
            String location = "skills/" + skillId + "/SKILL.md";
            String content = readResourceContent(location);
            String expectedName = "name: " + skillId;
            if (!content.startsWith("---") || !content.contains(expectedName)) {
                throw new IllegalStateException(
                        "Skill front matter must declare name matching groupedTools key: " + skillId
                );
            }
            List<String> declaredAllowedTools = extractAllowedTools(content);
            if (!declaredAllowedTools.equals(entry.getValue())) {
                throw new IllegalStateException(
                        "Skill allowed tools mismatch for %s, expected %s but found %s"
                                .formatted(skillId, entry.getValue(), declaredAllowedTools)
                );
            }
        }
    }

    private List<String> extractAllowedTools(String content) {
        List<String> toolNames = new ArrayList<>();
        boolean inAllowedTools = false;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (ALLOWED_TOOLS_HEADINGS.contains(line)) {
                inAllowedTools = true;
                continue;
            }
            if (inAllowedTools && line.startsWith("## ")) {
                break;
            }
            if (inAllowedTools && line.startsWith("- `") && line.endsWith("`")) {
                toolNames.add(line.substring(3, line.length() - 1));
            }
        }
        return List.copyOf(toolNames);
    }

    private String readResourceContent(String location) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(location)) {
            if (inputStream == null) {
                throw new IllegalStateException("Skill resource not found: " + location);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read skill resource: " + location, ex);
        }
    }
}
