package com.example.dataprocess.agent.model;

import java.util.List;

/**
 * Confirmation item returned to the frontend.
 */
public record AgentConfirmationItem(
        String confirmationKey,
        ConfirmationType confirmationType,
        String targetColumn,
        String sourceColumn,
        String question,
        List<String> candidateHeaders,
        String valueSetCode,
        List<String> optionValues,
        Boolean required,
        String hint,
        String reason
) {
}
