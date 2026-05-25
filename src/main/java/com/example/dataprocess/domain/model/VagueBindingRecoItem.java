package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * One recognition item for binding a rule source column to an uploaded header.
 *
 * @param targetColumn target column produced by the rule item
 * @param ruleType rule type such as DIRECT_MAPPING or AI_DERIVED
 * @param sourceColumn source column declared in the rule item
 * @param status recognition status
 * @param selectedHeader selected uploaded header when the binding is clear
 * @param candidateHeaders candidate uploaded headers when user confirmation is needed
 * @param reason short explanation for the decision
 */
public record VagueBindingRecoItem(
        String targetColumn,
        String ruleType,
        String sourceColumn,
        VagueBindingRecoStatus status,
        String selectedHeader,
        List<String> candidateHeaders,
        String reason
) {
}
