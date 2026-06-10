package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.example.dataprocess.agent.service.AgentStreamEventPublisher;
import com.example.dataprocess.agent.tool.AgentStateTool;
import com.example.dataprocess.agent.tool.DataProcessingAgentToolMethods;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
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

    private static final String CLASSPATH_SKILLS_PATH = "agent/skills";

    private static final Path SKILL_CACHE_PATH = Path.of(
            System.getProperty("java.io.tmpdir"),
            "data-processing-agent-skill-cache"
    );

    @Bean("dataProcessingReactAgent")
    public ReactAgent dataProcessingReactAgent(
            ChatModel chatModel,
            DataProcessingAgentToolMethods toolMethods,
            AgentStreamEventPublisher eventPublisher,
            AgentStateTool stateTool,
            ObjectMapper objectMapper
    ) {
        ToolCallback[] dataProcessingTools = ToolCallbacks.from(toolMethods);
        SkillRegistry skillRegistry = buildSkillRegistry();
        SkillsAgentHook skillsAgentHook = SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .groupedTools(Map.of(DATA_PROCESSING_SKILL_NAME, List.of(dataProcessingTools)))
                .build();
        List<ToolCallback> knownToolCallbacks = new ArrayList<>(List.of(dataProcessingTools));
        knownToolCallbacks.addAll(skillsAgentHook.getTools());
        ToolCallMessageValidator toolCallMessageValidator =
                new ToolCallMessageValidator(objectMapper, knownToolCallbacks);

        return ReactAgent.builder()
                .name("data-processing-react-agent")
                .description("通过 ReAct 工具调用推进已解析 Excel 的完整数据加工流程。")
                .model(chatModel)
                .instruction("""
                        语言规则是最高优先级规则之一。
                        除工具名称、枚举值、字段名、JSON key、SQL 标识符和代码标识符外，所有自然语言内容必须使用简体中文。
                        这包括你的分析、计划、步骤说明、错误说明、确认项问题描述、工具参数中的说明性文本，以及最终响应中的自然语言。
                        不得使用英文描述运行过程。

                        你是数据加工 ReAct Agent。
                        你必须先调用 `read_skill`，并传入 skill_name `data-processing-agent-skill`。
                        读取 skill 后，必须严格按照该 skill 描述的运行流程、步骤顺序和分支规则执行。
                        你的职责是推进工具调用和状态流转；面向前端的 DataProcessingAgentResponse 由服务层根据工具响应或任务状态统一组装。
                        当流程到达等待用户确认、SQL 已渲染、任务失败、任务完成或 skill 要求停止的位置时，不要自行补充或改写最终响应字段。
                        """)
                .hooks(
                        skillsAgentHook,
                        new EnsureToolCallIdHook(),
                        new StreamingToolCallRepairHook(toolCallMessageValidator),
                        new ToolCallbackDeduplicationHook(),
                        ModelCallLimitHook.builder().runLimit(12).build()
                )
                .interceptors(
                        new AgentExecutionToolStreamInterceptor(eventPublisher, stateTool),
                        new BlankToolInputNormalizingInterceptor(),
                        ToolErrorInterceptor.builder().build()
                )
                .toolExecutionTimeout(Duration.ofSeconds(30))
                .build();
    }

    private SkillRegistry buildSkillRegistry() {
        SkillRegistry skillRegistry = ClasspathSkillRegistry.builder()
                .classpathPath(CLASSPATH_SKILLS_PATH)
                .basePath(SKILL_CACHE_PATH.toString())
                .build();
        if (skillRegistry.contains(DATA_PROCESSING_SKILL_NAME)) {
            return skillRegistry;
        }

        throw new IllegalStateException("Skill not loaded: " + DATA_PROCESSING_SKILL_NAME
                + ". Checked classpath: " + CLASSPATH_SKILLS_PATH);
    }

}
