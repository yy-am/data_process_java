package com.example.dataprocess.agent.model;

import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Unified agent run request for first run and confirmation resume.
 */
public record DataProcessingAgentRequest(
        @NotBlank String taskId,
        @Valid DataProcessingTaskRequest taskRequest,
        @Valid AgentUserConfirmationRequest userConfirmationRequest
) {
}
