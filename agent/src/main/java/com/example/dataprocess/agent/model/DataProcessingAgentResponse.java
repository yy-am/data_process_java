package com.example.dataprocess.agent.model;

import com.example.dataprocess.domain.model.TemplateRecognitionResult;

import java.util.List;
import java.util.Map;

/**
 * Agent run response.
 */
public record DataProcessingAgentResponse(
        AgentWorkflowStage stage,
        String taskId,
        String parsedFileRef,
        TemplateRecognitionResult templateRecognitionResult,
        List<AgentConfirmationItem> confirmationItems,
        List<AgentConfirmationDecision> userConfirmationResult,
        Map<String, Object> summary,
        String errorCode,
        String message
) {
}
