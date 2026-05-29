package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.example.dataprocess.agent.tool.DataProcessingAgentToolMethods;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    private static final String SKILLS_SYSTEM_PROMPT_TEMPLATE = """
            ## Skills System

            You have access to a project skill registry.

            ### Available Skills

            {skills_list}

            ### How to Use Skills

            Before doing domain work, choose the matching skill and call `read_skill` with its exact skill name.
            After reading the skill, follow the SKILL.md instructions strictly and use only the tools made available for that skill.

            {skills_load_instructions}
            """;

    @Bean("dataProcessingReactAgent")
    public ReactAgent dataProcessingReactAgent(
            ChatModel chatModel,
            DataProcessingAgentToolMethods toolMethods
    ) {
        ToolCallback[] dataProcessingTools = ToolCallbacks.from(toolMethods);
        SkillRegistry skillRegistry = ClasspathSkillRegistry.builder()
                .classpathPath("agent/skills")
                .basePath(Path.of(System.getProperty("java.io.tmpdir"), "data-processing-agent-skills").toString())
                .systemPromptTemplate(new SystemPromptTemplate(SKILLS_SYSTEM_PROMPT_TEMPLATE))
                .build();
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
                .saver(new MemorySaver())
                .toolExecutionTimeout(Duration.ofSeconds(30))
                .build();
    }
}
