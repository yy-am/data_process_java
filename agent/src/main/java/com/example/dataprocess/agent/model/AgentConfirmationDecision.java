package com.example.dataprocess.agent.model;

/**
 * One frontend confirmation decision.
 */
public record AgentConfirmationDecision(
        String confirmationKey,
        ConfirmationType confirmationType,
        String targetColumn,
        String selectedHeader,
        String selectedValue,
        String inputValue
) {
}
