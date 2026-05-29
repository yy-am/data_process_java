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
                .description("通过 ReAct 工具调用推进已解析 Excel 的数据加工流程，当前执行到用户确认阶段为止。")
                .model(chatModel)
                .instruction("""
                        你是数据加工 ReAct Agent。
                        你必须先调用 `read_skill`，并传入 skill_name `data-processing-agent-skill`。
                        读取 skill 后，必须严格按照该 skill 描述的运行流程、步骤顺序和分支规则执行。
                        当前测试范围只允许推进到 USER_CONFIRMATION_REQUIRED 或 USER_CONFIRMED。
                        当前阶段不得进入临时表落库、SQL 片段生成、SQL 拼接或结果表写入。
                        最终回答必须是严格符合 DataProcessingAgentResponse 结构的 JSON，不要输出 Markdown。
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
