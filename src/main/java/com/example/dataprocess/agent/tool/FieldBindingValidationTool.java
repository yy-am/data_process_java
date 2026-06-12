package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.FieldBindingItem;
import com.example.dataprocess.agent.model.FieldBindingPlan;
import com.example.dataprocess.agent.model.FieldBindingStatus;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool facade for validating the agent-inferred field binding plan.
 */
@Component
public class FieldBindingValidationTool {

    private static final Set<String> USER_CONFIRM_RULE_TYPES = Set.of("USER_CONFIRM_OPTION", "USER_CONFIRM_INPUT");

    public FieldBindingPlan validateFieldBindingPlan(
            FieldBindingPlan plan,
            List<String> inputHeaders,
            ProcessingRule processingRule
    ) {
        if (plan == null || plan.items() == null) {
            throw new IllegalArgumentException("字段绑定计划不能为空。");
        }

        Set<String> availableHeaders = Set.copyOf(inputHeaders);
        Map<String, ProcessingRuleItem> ruleByTargetColumn = processingRule.ruleItems().stream()
                .collect(Collectors.toMap(
                        ProcessingRuleItem::targetColumn,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

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
        if (item.status() == null) {
            throw new IllegalArgumentException("字段绑定计划缺少状态: " + item.targetColumn());
        }
        if (USER_CONFIRM_RULE_TYPES.contains(item.ruleType())
                && item.sourceColumn() != null
                && !item.sourceColumn().isBlank()
                && !availableHeaders.contains(item.sourceColumn())) {
            throw new IllegalArgumentException("用户确认类规则的 sourceColumn 必须来自上传 Excel 表头: " + item.sourceColumn());
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
            case CONFIRMED -> validateConfirmed(item, ruleItem, candidateHeaders);
            case NEEDS_CONFIRMATION -> validateNeedsConfirmation(item, ruleItem, candidateHeaders);
            case MISSING -> validateMissing(item, ruleItem, candidateHeaders);
        };
    }

    private FieldBindingItem validateConfirmed(
            FieldBindingItem item,
            ProcessingRuleItem ruleItem,
            List<String> candidateHeaders
    ) {
        if (item.selectedHeader() == null || item.selectedHeader().isBlank()) {
            throw new IllegalArgumentException("CONFIRMED 字段绑定必须包含 selectedHeader。");
        }
        if (!candidateHeaders.isEmpty()) {
            throw new IllegalArgumentException("CONFIRMED 字段绑定不能包含 candidateHeaders。");
        }
        return new FieldBindingItem(
                item.targetColumn(),
                item.ruleType(),
                item.sourceColumn(),
                resolveBindingDisplayName(item, ruleItem),
                FieldBindingStatus.CONFIRMED,
                item.selectedHeader(),
                List.of(),
                item.reason()
        );
    }

    private FieldBindingItem validateNeedsConfirmation(
            FieldBindingItem item,
            ProcessingRuleItem ruleItem,
            List<String> candidateHeaders
    ) {
        if (item.selectedHeader() != null) {
            throw new IllegalArgumentException("NEEDS_CONFIRMATION 字段绑定不能包含 selectedHeader。");
        }
        return new FieldBindingItem(
                item.targetColumn(),
                item.ruleType(),
                item.sourceColumn(),
                resolveBindingDisplayName(item, ruleItem),
                FieldBindingStatus.NEEDS_CONFIRMATION,
                null,
                candidateHeaders,
                item.reason()
        );
    }

    private FieldBindingItem validateMissing(
            FieldBindingItem item,
            ProcessingRuleItem ruleItem,
            List<String> candidateHeaders
    ) {
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
                resolveBindingDisplayName(item, ruleItem),
                FieldBindingStatus.MISSING,
                null,
                List.of(),
                item.reason()
        );
    }

    private String resolveBindingDisplayName(FieldBindingItem item, ProcessingRuleItem ruleItem) {
        if (item.bindingDisplayName() != null && !item.bindingDisplayName().isBlank()) {
            return item.bindingDisplayName();
        }
        if ("EXPR".equals(ruleItem.ruleType())
                && ruleItem.sourceColumns().size() > 1
                && ruleItem.ruleGuide() != null
                && !ruleItem.ruleGuide().isBlank()) {
            return ruleItem.ruleGuide();
        }
        return item.sourceColumn();
    }
}
