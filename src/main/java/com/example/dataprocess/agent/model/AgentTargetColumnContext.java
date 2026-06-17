package com.example.dataprocess.agent.model;

import com.example.dataprocess.domain.model.ActualColumnMapping;

import java.util.List;

/**
 * Target-column context used by the agent to generate one ProcessingPlanDsl column.
 *
 * @param targetColumn standard target column
 * @param ruleType processing rule type
 * @param sourceColumn rule-side source field or semantic source
 * @param bindingDisplayName display name resolved during field binding
 * @param bindingStatus final field binding status
 * @param actualColumnMappings Excel headers and staging elastic columns allowed for this target column
 * @param ruleGuide processing guide used to generate expressionSql
 * @param example processing example used as semantic reference
 * @param confirmedValue value confirmed or entered by the user
 * @param confirmationType confirmation decision type, if any
 * @param reason short reason from field binding or confirmation
 */
public record AgentTargetColumnContext(
        String targetColumn,
        String ruleType,
        String sourceColumn,
        String bindingDisplayName,
        FieldBindingStatus bindingStatus,
        List<ActualColumnMapping> actualColumnMappings,
        String ruleGuide,
        String example,
        String confirmedValue,
        ConfirmationType confirmationType,
        String reason
) {
}
