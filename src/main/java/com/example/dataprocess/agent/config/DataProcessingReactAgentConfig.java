package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.example.dataprocess.agent.tool.DataProcessingAgentToolMethods;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * ReactAgent configuration for the decoupled data-processing agent.
 */
@Configuration
public class DataProcessingReactAgentConfig {

    private static final String DATA_PROCESSING_SKILL_NAME = "data-processing-agent-skill";

    private static final Path SOURCE_SKILLS_PATH = Path.of("src", "main", "resources", "agent", "skills")
            .toAbsolutePath();

    @Bean("dataProcessingReactAgent")
    public ReactAgent dataProcessingReactAgent(
            ChatModel chatModel,
            DataProcessingAgentToolMethods toolMethods
    ) {
        ToolCallback[] dataProcessingTools = ToolCallbacks.from(toolMethods);
        SkillRegistry skillRegistry = buildSkillRegistry();
        SkillsAgentHook skillsAgentHook = SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .groupedTools(Map.of(DATA_PROCESSING_SKILL_NAME, List.of(dataProcessingTools)))
                .build();

        return ReactAgent.builder()
                .name("data-processing-react-agent")
                .description("Process parsed Excel data up to user confirmation through tool-calling ReAct steps.")
                .model(chatModel)
                .instruction("""
                        You are the data-processing ReAct agent.
                        You must first call `read_skill` with skill_name `data-processing-agent-skill`.
                        Then execute the workflow exactly as described by that skill.
                        The current test scope stops at USER_CONFIRMATION_REQUIRED or USER_CONFIRMED.
                        Do not enter temporary-table loading, SQL fragment generation, SQL assembly, or result-table execution yet.
                        The final answer must be strict JSON matching DataProcessingAgentResponse. Do not output Markdown.
                        """)
                .hooks(
                        skillsAgentHook,
                        new ToolCallbackDeduplicationHook(),
                        ModelCallLimitHook.builder().runLimit(12).build()
                )
                .interceptors(ToolErrorInterceptor.builder().build())
                .toolExecutionTimeout(Duration.ofSeconds(30))
                .build();
    }

    private SkillRegistry buildSkillRegistry() {
        if (!Files.exists(SOURCE_SKILLS_PATH)) {
            throw new IllegalStateException("Skill directory not found: " + SOURCE_SKILLS_PATH);
        }

        SkillRegistry skillRegistry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(SOURCE_SKILLS_PATH.toString())
                .build();
        if (skillRegistry.contains(DATA_PROCESSING_SKILL_NAME)) {
            return skillRegistry;
        }

        throw new IllegalStateException("Skill not loaded: " + DATA_PROCESSING_SKILL_NAME
                + ". Checked skill directory: " + SOURCE_SKILLS_PATH);
    }
}
