package com.example.dataprocess.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the agent skill markdown from the decoupled agent directory.
 */
@Component
public class AgentSkillTool {

    private static final String SKILL_RESOURCE_PATH = "agent/data-processing-agent-skill.md";

    public String loadSkill() {
        try (var inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(SKILL_RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("未找到 Agent Skill 资源: " + SKILL_RESOURCE_PATH);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Agent Skill 失败: " + SKILL_RESOURCE_PATH, ex);
        }
    }
}
