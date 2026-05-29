package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.ValueSetMetadata;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool facade for value-set metadata used by option confirmations.
 */
@Component
public class ValueSetTool {

    private static final String USER_CONFIRM_OPTION_RULE_TYPE = "USER_CONFIRM_OPTION";

    private static final Map<String, List<String>> DEFAULT_OPTIONS_BY_FIELD = Map.of(
            "company_code", List.of("COMPANY_A", "COMPANY_B"),
            "company_name", List.of("公司A", "公司B")
    );

    public List<ValueSetMetadata> loadValueSetMetadata(ProcessingRule processingRule) {
        return processingRule.ruleItems().stream()
                .filter(item -> USER_CONFIRM_OPTION_RULE_TYPE.equals(item.ruleType()))
                .map(this::toMetadata)
                .toList();
    }

    private ValueSetMetadata toMetadata(ProcessingRuleItem item) {
        String valueSetCode = item.userInputField().isBlank() ? item.targetColumn() : item.userInputField();
        return new ValueSetMetadata(
                item.targetColumn(),
                valueSetCode,
                optionValues(item, valueSetCode)
        );
    }

    private List<String> optionValues(ProcessingRuleItem item, String valueSetCode) {
        if (item.options() != null && !item.options().isEmpty()) {
            return List.copyOf(item.options());
        }
        return DEFAULT_OPTIONS_BY_FIELD.getOrDefault(valueSetCode, List.of());
    }
}
