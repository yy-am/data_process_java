package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.FieldBindingItem;
import com.example.dataprocess.agent.model.FieldBindingPlan;
import com.example.dataprocess.agent.model.FieldBindingStatus;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool facade for validating the agent-inferred field binding plan.
 */
@Component
public class FieldBindingValidationTool {

    private static final Set<String> FIELD_BINDING_RULE_TYPES = Set.of("DIRECT_MAPPING", "EXPR");

    public FieldBindingPlan validateFieldBindingPlan(
            FieldBindingPlan plan,
            List<String> inputHeaders,
            ProcessingRule processingRule
    ) {
        if (plan == null || plan.items() == null) {
            throw new IllegalArgumentException("字段绑定计划不能为空。");
        }

        Set<String> availableHeaders = Set.copyOf(inputHeaders);
        List<ProcessingRuleItem> sourceDependentRules = processingRule.ruleItems().stream()
                .filter(item -> FIELD_BINDING_RULE_TYPES.contains(item.ruleType()))
                .filter(item -> !item.sourceColumns().isEmpty())
                .toList();

        Set<String> expectedKeys = sourceDependentRules.stream()
                .flatMap(item -> item.sourceColumns().stream().map(sourceColumn -> key(item, sourceColumn)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualKeys = plan.items().stream()
                .map(item -> item.targetColumn() + "|" + item.ruleType() + "|" + item.sourceColumn())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!expectedKeys.equals(actualKeys)) {
            throw new IllegalArgumentException("字段绑定计划覆盖范围不匹配，期望 " + expectedKeys + "，实际 " + actualKeys);
        }

        Map<String, ProcessingRuleItem> ruleByTargetColumn = sourceDependentRules.stream()
                .collect(Collectors.toMap(ProcessingRuleItem::targetColumn, item -> item, (left, right) -> left));

        List<FieldBindingItem> normalizedItems = plan.items().stream()
                .map(item -> validateItem(item, ruleByTargetColumn, availableHeaders))
                .toList();
        return new FieldBindingPlan(List.copyOf(normalizedItems));
    }

    private FieldBindingItem validateItem(
            FieldBindingItem item,
            Map<String, ProcessingRuleItem> ruleByTargetColumn,
            Set<String> availableHeaders
    ) {
        if (item == null) {
            throw new IllegalArgumentException("字段绑定计划存在空项。");
        }
        ProcessingRuleItem ruleItem = ruleByTargetColumn.get(item.targetColumn());
        if (ruleItem == null) {
            throw new IllegalArgumentException("字段绑定计划包含未知目标列: " + item.targetColumn());
        }
        if (!ruleItem.ruleType().equals(item.ruleType())) {
            throw new IllegalArgumentException("字段绑定计划 ruleType 与规则不一致: " + item.targetColumn());
        }
        if (!ruleItem.sourceColumns().contains(item.sourceColumn())) {
            throw new IllegalArgumentException("字段绑定计划包含规则未声明的 sourceColumn: " + item.sourceColumn());
        }
        if (item.status() == null) {
            throw new IllegalArgumentException("字段绑定计划缺少状态: " + item.targetColumn());
        }

        List<String> candidateHeaders = item.candidateHeaders() == null ? List.of() : List.copyOf(item.candidateHeaders());
        for (String candidateHeader : candidateHeaders) {
            if (!availableHeaders.contains(candidateHeader)) {
                throw new IllegalArgumentException("候选列不存在于上传 Excel 表头: " + candidateHeader);
            }
        }
        if (item.selectedHeader() != null && !availableHeaders.contains(item.selectedHeader())) {
            throw new IllegalArgumentException("选中列不存在于上传 Excel 表头: " + item.selectedHeader());
        }

        return switch (item.status()) {
            case EXACT_MAPPING -> validateExactMapping(item, candidateHeaders);
            case FUZZY_MAPPING -> validateFuzzyMapping(item, candidateHeaders);
            case MISSING -> validateMissing(item, candidateHeaders);
        };
    }

    private FieldBindingItem validateExactMapping(FieldBindingItem item, List<String> candidateHeaders) {
        if (item.selectedHeader() == null || item.selectedHeader().isBlank()) {
            throw new IllegalArgumentException("EXACT_MAPPING 字段绑定必须包含 selectedHeader。");
        }
        if (!candidateHeaders.isEmpty()) {
            throw new IllegalArgumentException("EXACT_MAPPING 字段绑定不能包含 candidateHeaders。");
        }
        return new FieldBindingItem(
                item.targetColumn(),
                item.ruleType(),
                item.sourceColumn(),
                FieldBindingStatus.EXACT_MAPPING,
                item.selectedHeader(),
                List.of(),
                item.reason()
        );
    }

    private FieldBindingItem validateFuzzyMapping(FieldBindingItem item, List<String> candidateHeaders) {
        if (item.selectedHeader() != null) {
            throw new IllegalArgumentException("FUZZY_MAPPING 字段绑定不能包含 selectedHeader。");
        }
        if (candidateHeaders.size() < 2) {
            throw new IllegalArgumentException("FUZZY_MAPPING 字段绑定至少需要两个 candidateHeaders。");
        }
        return new FieldBindingItem(
                item.targetColumn(),
                item.ruleType(),
                item.sourceColumn(),
                FieldBindingStatus.FUZZY_MAPPING,
                null,
                candidateHeaders,
                item.reason()
        );
    }

    private FieldBindingItem validateMissing(FieldBindingItem item, List<String> candidateHeaders) {
        if (item.selectedHeader() != null) {
            throw new IllegalArgumentException("MISSING 字段绑定不能包含 selectedHeader。");
        }
        if (!candidateHeaders.isEmpty()) {
            throw new IllegalArgumentException("MISSING 字段绑定不能包含 candidateHeaders。");
        }
        return new FieldBindingItem(
                item.targetColumn(),
                item.ruleType(),
                item.sourceColumn(),
                FieldBindingStatus.MISSING,
                null,
                List.of(),
                item.reason()
        );
    }

    private String key(ProcessingRuleItem item, String sourceColumn) {
        return item.targetColumn() + "|" + item.ruleType() + "|" + sourceColumn;
    }
}
