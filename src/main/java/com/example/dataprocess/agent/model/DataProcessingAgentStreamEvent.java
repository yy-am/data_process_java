package com.example.dataprocess.agent.model;

import java.util.Map;

/**
 * Stable SSE event contract for the data-processing agent frontend.
 */
public record DataProcessingAgentStreamEvent(
        String event,
        String taskId,
        AgentWorkflowStage stage,
        String skillStage,
        String node,
        String message,
        DataProcessingAgentResponse response,
        Map<String, Object> detail
) {
}
