package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.AgentConfirmationDecision;
import com.example.dataprocess.agent.model.AgentConfirmationItem;
import com.example.dataprocess.agent.model.AgentUserConfirmationRequest;
import com.example.dataprocess.agent.model.ColumnNullInspectionResult;
import com.example.dataprocess.agent.model.ConfirmationType;
import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.model.FieldBindingItem;
import com.example.dataprocess.agent.model.FieldBindingStatus;
import com.example.dataprocess.agent.model.StandardRequiredFields;
import com.example.dataprocess.agent.model.ValueSetMetadata;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tool facade for building and validating structured user confirmations.
 */
@Component
public class ConfirmationTool {

    private static final String USER_CONFIRM_OPTION_RULE_TYPE = "USER_CONFIRM_OPTION";
    private static final String USER_CONFIRM_INPUT_RULE_TYPE = "USER_CONFIRM_INPUT";

    private final ParsedExcelFileTool parsedExcelFileTool;

    public ConfirmationTool(ParsedExcelFileTool parsedExcelFileTool) {
        this.parsedExcelFileTool = parsedExcelFileTool;
    }

    public List<AgentConfirmationItem> buildConfirmationItems(DataProcessingAgentState state) {
        ProcessingRule processingRule = state.templateBundle().processingRule();
        List<AgentConfirmationItem> items = new ArrayList<>();

        for (FieldBindingItem bindingItem : state.fieldBindingPlan().items()) {
            if (bindingItem.status() == FieldBindingStatus.FUZZY_MAPPING) {
                items.add(mappingConfirmation(bindingItem));
            }
        }

        Map<String, ValueSetMetadata> valueSetByTargetColumn = state.valueSetMetadata().stream()
                .collect(Collectors.toMap(ValueSetMetadata::targetColumn, Function.identity(), (left, right) -> left));
        for (ProcessingRuleItem ruleItem : processingRule.ruleItems()) {
            if (USER_CONFIRM_OPTION_RULE_TYPE.equals(ruleItem.ruleType())) {
                ValueSetMetadata valueSetMetadata = valueSetByTargetColumn.get(ruleItem.targetColumn());
                items.add(optionConfirmation(ruleItem, valueSetMetadata));
            }
            if (USER_CONFIRM_INPUT_RULE_TYPE.equals(ruleItem.ruleType())) {
                items.add(inputConfirmation(
                        key(ConfirmationType.INPUT_CONFIRMATION, ruleItem.targetColumn(), null),
                        ruleItem.targetColumn(),
                        ruleItem.inputHint(),
                        false,
                        "加工规则要求用户手工输入。"
                ));
            }
        }

        items.addAll(requiredFieldInputConfirmations(state));
        return validateConfirmationItems(items, state);
    }

    public List<AgentConfirmationItem> validateConfirmationItems(
            List<AgentConfirmationItem> items,
            DataProcessingAgentState state
    ) {
        Set<String> targetColumns = state.templateBundle().standardTemplate().standardColumns().stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> headers = Set.copyOf(state.parsedExcelSummary().sourceHeaders());
        Set<String> keys = new LinkedHashSet<>();

        for (AgentConfirmationItem item : items) {
            if (item.confirmationKey() == null || item.confirmationKey().isBlank()) {
                throw new IllegalArgumentException("确认项缺少 confirmationKey。");
            }
            if (!keys.add(item.confirmationKey())) {
                throw new IllegalArgumentException("确认项 key 重复: " + item.confirmationKey());
            }
            if (item.confirmationType() == null) {
                throw new IllegalArgumentException("确认项缺少 confirmationType: " + item.confirmationKey());
            }
            if (!targetColumns.contains(item.targetColumn())) {
                throw new IllegalArgumentException("确认项引用未知目标列: " + item.targetColumn());
            }
            if (item.confirmationType() == ConfirmationType.MAPPING_CONFIRMATION) {
                if (item.candidateHeaders() == null || item.candidateHeaders().size() < 2) {
                    throw new IllegalArgumentException("字段映射确认项候选列不足: " + item.confirmationKey());
                }
                for (String candidateHeader : item.candidateHeaders()) {
                    if (!headers.contains(candidateHeader)) {
                        throw new IllegalArgumentException("字段映射确认项引用未知 Excel 表头: " + candidateHeader);
                    }
                }
            }
            if (item.confirmationType() == ConfirmationType.OPTION_CONFIRMATION
                    && (item.valueSetCode() == null || item.valueSetCode().isBlank())) {
                throw new IllegalArgumentException("值集确认项缺少 valueSetCode: " + item.confirmationKey());
            }
        }
        return List.copyOf(items);
    }

    public List<AgentConfirmationDecision> validateUserConfirmationRequest(
            List<AgentConfirmationItem> pendingItems,
            AgentUserConfirmationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("用户确认提交不能为空。");
        }
        Map<String, AgentConfirmationItem> pendingByKey = pendingItems.stream()
                .collect(Collectors.toMap(AgentConfirmationItem::confirmationKey, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<String> actualKeys = request.decisions().stream()
                .map(AgentConfirmationDecision::confirmationKey)
                .toList();
        validateDecisionCoverage(pendingByKey.keySet(), actualKeys);

        return request.decisions().stream()
                .map(decision -> validateDecision(pendingByKey.get(decision.confirmationKey()), decision))
                .toList();
    }

    private List<AgentConfirmationItem> requiredFieldInputConfirmations(DataProcessingAgentState state) {
        StandardRequiredFields requiredFields = state.requiredFields();
        if (requiredFields == null || requiredFields.requiredColumns().isEmpty()) {
            return List.of();
        }

        List<AgentConfirmationItem> items = new ArrayList<>();
        for (String requiredColumn : requiredFields.requiredColumns()) {
            if (hasExplicitUserConfirmationRule(state.templateBundle().processingRule(), requiredColumn)) {
                continue;
            }

            List<FieldBindingItem> bindings = state.fieldBindingPlan().items().stream()
                    .filter(item -> requiredColumn.equals(item.targetColumn()))
                    .toList();
            if (bindings.isEmpty() || bindings.stream().anyMatch(item -> item.status() == FieldBindingStatus.MISSING)) {
                items.add(inputConfirmation(
                        key(ConfirmationType.INPUT_CONFIRMATION, requiredColumn, "required"),
                        requiredColumn,
                        "请输入必填字段 " + requiredColumn + " 的固定值。",
                        true,
                        "标准模板必填字段没有可靠可映射列。"
                ));
                continue;
            }

            List<String> selectedHeaders = bindings.stream()
                    .filter(item -> item.status() == FieldBindingStatus.EXACT_MAPPING)
                    .map(FieldBindingItem::selectedHeader)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
            if (!selectedHeaders.isEmpty()) {
                List<ColumnNullInspectionResult> nullResults = parsedExcelFileTool.inspectExcelColumnNulls(
                        state.parsedFileRef(),
                        selectedHeaders
                );
                if (nullResults.stream().anyMatch(ColumnNullInspectionResult::hasBlankValue)) {
                    items.add(inputConfirmation(
                            key(ConfirmationType.INPUT_CONFIRMATION, requiredColumn, "required_blank"),
                            requiredColumn,
                            "请输入必填字段 " + requiredColumn + " 的固定值。",
                            true,
                            "标准模板必填字段存在映射列，但映射列存在空值；用户输入值将整列全量覆盖。"
                    ));
                }
            }
        }
        return items;
    }

    private boolean hasExplicitUserConfirmationRule(ProcessingRule processingRule, String targetColumn) {
        return processingRule.ruleItems().stream()
                .filter(item -> targetColumn.equals(item.targetColumn()))
                .anyMatch(item -> USER_CONFIRM_OPTION_RULE_TYPE.equals(item.ruleType())
                        || USER_CONFIRM_INPUT_RULE_TYPE.equals(item.ruleType()));
    }

    private AgentConfirmationItem mappingConfirmation(FieldBindingItem item) {
        return new AgentConfirmationItem(
                key(ConfirmationType.MAPPING_CONFIRMATION, item.targetColumn(), item.sourceColumn()),
                ConfirmationType.MAPPING_CONFIRMATION,
                item.targetColumn(),
                item.sourceColumn(),
                "请确认目标列 " + item.targetColumn() + " 的规则源字段 " + item.sourceColumn() + " 应绑定哪个 Excel 原始列。",
                item.candidateHeaders(),
                null,
                List.of(),
                false,
                null,
                item.reason()
        );
    }

    private AgentConfirmationItem optionConfirmation(ProcessingRuleItem ruleItem, ValueSetMetadata metadata) {
        String valueSetCode = metadata == null ? ruleItem.targetColumn() : metadata.valueSetCode();
        List<String> optionValues = metadata == null ? ruleItem.options() : metadata.optionValues();
        return new AgentConfirmationItem(
                key(ConfirmationType.OPTION_CONFIRMATION, ruleItem.targetColumn(), null),
                ConfirmationType.OPTION_CONFIRMATION,
                ruleItem.targetColumn(),
                null,
                "请选择目标列 " + ruleItem.targetColumn() + " 的固定值。",
                List.of(),
                valueSetCode,
                List.copyOf(optionValues),
                false,
                null,
                "加工规则要求用户从值集中选择。"
        );
    }

    private AgentConfirmationItem inputConfirmation(
            String confirmationKey,
            String targetColumn,
            String hint,
            boolean required,
            String reason
    ) {
        return new AgentConfirmationItem(
                confirmationKey,
                ConfirmationType.INPUT_CONFIRMATION,
                targetColumn,
                null,
                "请输入目标列 " + targetColumn + " 的固定值。",
                List.of(),
                null,
                List.of(),
                required,
                hint == null || hint.isBlank() ? "请输入固定值。" : hint,
                reason
        );
    }

    private AgentConfirmationDecision validateDecision(AgentConfirmationItem item, AgentConfirmationDecision decision) {
        if (item == null) {
            throw new IllegalArgumentException("未知确认项: " + decision.confirmationKey());
        }
        if (decision.confirmationType() != item.confirmationType()) {
            throw new IllegalArgumentException("确认项类型不匹配: " + decision.confirmationKey());
        }
        if (!item.targetColumn().equals(decision.targetColumn())) {
            throw new IllegalArgumentException("确认项目标列不匹配: " + decision.confirmationKey());
        }

        return switch (item.confirmationType()) {
            case MAPPING_CONFIRMATION -> validateMappingDecision(item, decision);
            case OPTION_CONFIRMATION -> validateOptionDecision(item, decision);
            case INPUT_CONFIRMATION -> validateInputDecision(item, decision);
        };
    }

    private AgentConfirmationDecision validateMappingDecision(
            AgentConfirmationItem item,
            AgentConfirmationDecision decision
    ) {
        if (decision.selectedHeader() == null || decision.selectedHeader().isBlank()) {
            throw new IllegalArgumentException("字段映射确认缺少 selectedHeader: " + decision.confirmationKey());
        }
        if (!item.candidateHeaders().contains(decision.selectedHeader())) {
            throw new IllegalArgumentException("字段映射确认选择了候选外字段: " + decision.selectedHeader());
        }
        return decision;
    }

    private AgentConfirmationDecision validateOptionDecision(
            AgentConfirmationItem item,
            AgentConfirmationDecision decision
    ) {
        if (decision.selectedValue() == null || decision.selectedValue().isBlank()) {
            throw new IllegalArgumentException("值集确认缺少 selectedValue: " + decision.confirmationKey());
        }
        if (!item.optionValues().isEmpty() && !item.optionValues().contains(decision.selectedValue())) {
            throw new IllegalArgumentException("值集确认选择了非法值: " + decision.selectedValue());
        }
        return decision;
    }

    private AgentConfirmationDecision validateInputDecision(
            AgentConfirmationItem item,
            AgentConfirmationDecision decision
    ) {
        if (decision.inputValue() == null || decision.inputValue().trim().isEmpty()) {
            throw new IllegalArgumentException("输入确认不能为空: " + decision.confirmationKey());
        }
        return new AgentConfirmationDecision(
                decision.confirmationKey(),
                decision.confirmationType(),
                decision.targetColumn(),
                decision.selectedHeader(),
                decision.selectedValue(),
                decision.inputValue().trim()
        );
    }

    private void validateDecisionCoverage(Set<String> expectedKeys, List<String> actualKeyList) {
        Set<String> actualKeys = new LinkedHashSet<>(actualKeyList);
        if (actualKeyList.size() != actualKeys.size()) {
            throw new IllegalArgumentException("用户确认提交存在重复 key: " + actualKeyList);
        }
        if (!expectedKeys.equals(actualKeys)) {
            throw new IllegalArgumentException("用户确认提交覆盖范围不匹配，期望 " + expectedKeys + "，实际 " + actualKeys);
        }
    }

    private String key(ConfirmationType type, String targetColumn, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return type.name() + "::" + targetColumn;
        }
        return type.name() + "::" + targetColumn + "::" + suffix;
    }
}
