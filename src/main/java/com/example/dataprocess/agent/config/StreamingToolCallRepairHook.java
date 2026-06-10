package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Objects;

/**
 * Repairs fragmented streaming tool calls before they are executed or replayed
 * into the next model request.
 */
@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
public class StreamingToolCallRepairHook extends MessagesModelHook {

    private static final Logger log = LoggerFactory.getLogger(StreamingToolCallRepairHook.class);

    private final ToolCallMessageValidator toolCallMessageValidator;

    public StreamingToolCallRepairHook(ToolCallMessageValidator toolCallMessageValidator) {
        this.toolCallMessageValidator = Objects.requireNonNull(toolCallMessageValidator, "toolCallMessageValidator");
    }

    @Override
    public String getName() {
        return "streaming_tool_call_repair";
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of(JumpTo.model, JumpTo.tool);
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        return repairMessages(previousMessages, "beforeModel", false);
    }

    @Override
    public AgentCommand afterModel(List<Message> currentMessages, RunnableConfig config) {
        return repairMessages(currentMessages, "afterModel", true);
    }

    private AgentCommand repairMessages(List<Message> messages, String phase, boolean allowRetryJump) {
        ToolCallMessageValidator.ValidationResult result = toolCallMessageValidator.validate(messages);
        if (!result.changed()) {
            return new AgentCommand(messages);
        }

        log.warn(
                "Repaired streaming tool calls in phase={}, originalToolCallCount={}, normalizedToolCallCount={}, requestModelRetry={}, detailCount={}",
                phase,
                result.originalToolCallCount(),
                result.normalizedToolCallCount(),
                result.requestModelRetry(),
                result.details().size()
        );

        if (allowRetryJump && result.requestModelRetry()) {
            return new AgentCommand(JumpTo.model, result.messages());
        }
        return new AgentCommand(result.messages());
    }
}
