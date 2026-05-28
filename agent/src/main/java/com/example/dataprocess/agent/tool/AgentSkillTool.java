package com.example.dataprocess.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the agent skill markdown from the decoupled agent directory.
 */
@Component
public class AgentSkillTool {

    private static final Path SKILL_PATH = Path.of("agent", "data-processing-agent-skill.md");

    public String loadSkill() {
        try {
            return Files.readString(SKILL_PATH, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Agent Skill 失败: " + SKILL_PATH.toAbsolutePath(), ex);
        }
    }
}
