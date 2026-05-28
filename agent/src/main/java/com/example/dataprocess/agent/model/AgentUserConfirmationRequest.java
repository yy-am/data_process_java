package com.example.dataprocess.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Confirmation payload submitted after USER_CONFIRMATION_REQUIRED.
 */
public record AgentUserConfirmationRequest(
        @NotBlank String taskId,
        @NotNull List<AgentConfirmationDecision> decisions
) {
}
