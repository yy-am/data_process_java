package com.example.dataprocess.infrastructure.runtime;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 模板识别技能运行时适配层。
 */
@Component
public class TemplateRecognitionSkillRuntime {

    private static final String SKILL_ID = "template-recognition";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final SkillRegistry skillRegistry;
    private final Map<String, List<ToolCallback>> groupedTools;
    private final SkillExecutionStateHolder stateHolder;

    public TemplateRecognitionSkillRuntime(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            SkillRegistry skillRegistry,
            Map<String, List<ToolCallback>> groupedTools,
            SkillExecutionStateHolder stateHolder
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.skillRegistry = skillRegistry;
        this.groupedTools = groupedTools;
        this.stateHolder = stateHolder;
    }

    public TemplateRecognitionResult execute(DataProcessingGraphState state) {
        String statePayload = serializeState(state);
        try {
            stateHolder.setCurrentState(state);
            return ChatClient.create(chatModel)
                    .prompt()
                    .system(buildSystemPrompt())
                    .user(buildUserPrompt(statePayload))
                    .toolCallbacks(requiredToolCallbacks())
                    .call()
                    .entity(TemplateRecognitionResult.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute template-recognition skill.", ex);
        } finally {
            stateHolder.clear();
        }
    }

    private String buildSystemPrompt() {
        return """
                You are executing the TemplateRecognitionSkillNode of a StateGraph workflow.
                Use the following skill definition as the only execution contract.

                %s
                """.formatted(readSkillContent());
    }

    private String buildUserPrompt(String statePayload) {
        return """
                Current graph state JSON:
                %s

                Follow the skill contract strictly.
                Use only the bound tools for this node.
                Return only the required structured JSON object.
                """.formatted(statePayload);
    }

    private List<ToolCallback> requiredToolCallbacks() {
        List<ToolCallback> callbacks = groupedTools.get(SKILL_ID);
        if (callbacks == null || callbacks.isEmpty()) {
            throw new IllegalStateException("No grouped tools configured for skill: " + SKILL_ID);
        }
        return callbacks;
    }

    private String readSkillContent() {
        try {
            return skillRegistry.readSkillContent(SKILL_ID);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read skill content for " + SKILL_ID, ex);
        }
    }

    private String serializeState(DataProcessingGraphState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize graph state for template-recognition skill.", ex);
        }
    }
}
