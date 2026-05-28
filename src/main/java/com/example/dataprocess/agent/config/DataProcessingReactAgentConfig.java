package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.example.dataprocess.agent.tool.DataProcessingAgentToolMethods;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ReactAgent configuration for the decoupled data-processing agent.
 */
@Configuration
public class DataProcessingReactAgentConfig {

    @Bean("dataProcessingReactAgent")
    public ReactAgent dataProcessingReactAgent(
            ChatModel chatModel,
            DataProcessingAgentToolMethods toolMethods
    ) {
        FileSystemSkillRegistry skillRegistry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory("src/main/resources/agent")
                .autoLoad(true)
                .build();
        SkillsAgentHook skillsHook = SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .autoReload(true)
                .build();

        return ReactAgent.builder()
                .name("data-processing-react-agent")
                .description("Process parsed Excel data up to user confirmation through tool-calling ReAct steps.")
                .model(chatModel)
                .instruction("""
                        你是数据加工 ReAct Agent。
                        运行前必须读取并遵守 skill: data-processing-agent-skill。
                        必须通过工具推进任务状态、读取文件摘要、加载模板规则、校验模板识别、校验字段绑定、生成并校验确认项。
                        当前测试范围只到 USER_CONFIRMATION_REQUIRED 或 USER_CONFIRMED，不得进入临时表落库和 SQL 执行。
                        最终回答必须是 DataProcessingAgentResponse 结构的严格 JSON，不要输出 Markdown。
                        """)
                .hooks(skillsHook, ModelCallLimitHook.builder().runLimit(12).build())
                .tools(skillsHook.getTools())
                .methodTools(toolMethods)
                .interceptors(ToolErrorInterceptor.builder().build())
                .saver(new MemorySaver())
                .toolExecutionTimeout(Duration.ofSeconds(30))
                .build();
    }
}
