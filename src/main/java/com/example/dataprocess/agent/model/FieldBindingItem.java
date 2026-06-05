package com.example.dataprocess.agent.model;

import java.util.List;

/**
 * Binding decision for one processing-rule source field.
 */
public record FieldBindingItem(
        String targetColumn,
        String ruleType,
        String sourceColumn,
        String bindingDisplayName,
        FieldBindingStatus status,
        String selectedHeader,
        List<String> candidateHeaders,
        String reason
) {
}
