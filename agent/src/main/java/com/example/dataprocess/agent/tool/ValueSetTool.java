package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.ValueSetMetadata;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool facade for value-set metadata used by option confirmations.
 */
@Component
public class ValueSetTool {

    private static final String USER_CONFIRM_OPTION_RULE_TYPE = "USER_CONFIRM_OPTION";

    public List<ValueSetMetadata> loadValueSetMetadata(ProcessingRule processingRule) {
        return processingRule.ruleItems().stream()
                .filter(item -> USER_CONFIRM_OPTION_RULE_TYPE.equals(item.ruleType()))
                .map(this::toMetadata)
                .toList();
    }

    private ValueSetMetadata toMetadata(ProcessingRuleItem item) {
        return new ValueSetMetadata(
                item.targetColumn(),
                item.userInputField().isBlank() ? item.targetColumn() : item.userInputField(),
                List.copyOf(item.options())
        );
    }
}
