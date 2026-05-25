package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputConfirmation;
import com.example.dataprocess.domain.model.MappingConfirmation;
import com.example.dataprocess.domain.model.OptionConfirmation;
import com.example.dataprocess.domain.model.OptionItem;
import com.example.dataprocess.domain.model.ProcessingRuleDocument;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import com.example.dataprocess.domain.model.SourceFieldCandidate;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.example.dataprocess.interfaces.restful.request.InputConfirmationDto;
import com.example.dataprocess.interfaces.restful.request.MappingConfirmationDto;
import com.example.dataprocess.interfaces.restful.request.OptionConfirmationDto;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 结构化用户确认服务。
 *
 * <p>一期确认逻辑完全走固定 JSON 契约，不做开放式多轮对话。
 * 所有确认字段的生成规则、校验规则和候选项规则都在这里显式定义。</p>
 */
@Service
public class StructuredConfirmationService {

    private static final String USER_CONFIRM_RULE_TYPE = "USER_CONFIRM";

    private final ProcessingRuleService processingRuleService;

    public StructuredConfirmationService(ProcessingRuleService processingRuleService) {
        this.processingRuleService = processingRuleService;
    }

    /**
     * 基于模板识别结果构建固定 JSON 结构的确认请求。
     */
    public UserConfirmationItems buildUserConfirmationItems(TaskSession session) {
        TemplateRecognitionResult recognitionResult = requireTemplateRecognitionResult(session);
        ProcessingRuleDocument ruleDocument = processingRuleService.loadRuleDocument(
                recognitionResult.presetTemplateCode()
        );

        List<String> unresolvedFields = recognitionResult.unresolvedTargetFields() == null
                ? List.of()
                : recognitionResult.unresolvedTargetFields();

        List<MappingConfirmation> mappingConfirmations = new ArrayList<>();
        List<OptionConfirmation> optionConfirmations = new ArrayList<>();
        List<InputConfirmation> inputConfirmations = new ArrayList<>();

        for (String unresolvedField : unresolvedFields) {
            mappingConfirmations.add(buildMappingConfirmation(unresolvedField, session.sourceHeaders()));
        }

        for (ProcessingRuleItem ruleItem : ruleDocument.ruleItems()) {
            if (!USER_CONFIRM_RULE_TYPE.equals(ruleItem.ruleType())) {
                continue;
            }
            if (!ruleItem.options().isEmpty()) {
                optionConfirmations.add(buildOptionConfirmation(ruleItem));
            } else {
                inputConfirmations.add(buildInputConfirmation(ruleItem));
            }
        }

        return new UserConfirmationItems(
                session.taskId(),
                recognitionResult.presetTemplateCode(),
                recognitionResult.standardTemplateCode(),
                List.copyOf(mappingConfirmations),
                List.copyOf(optionConfirmations),
                List.copyOf(inputConfirmations)
        );
    }

    /**
     * 将前端提交的确认 JSON 转换为通过校验的领域结果。
     */
    public UserConfirmationResult applyConfirmationRequest(UserConfirmationItems pendingItems, UserConfirmationRequest request) {
        if (!pendingItems.taskId().equals(request.taskId())) {
            throw new IllegalArgumentException("用户确认请求中的 taskId 与待确认任务不一致。");
        }
        if (!pendingItems.presetTemplateCode().equals(request.presetTemplateCode())) {
            throw new IllegalArgumentException("用户确认请求中的 presetTemplateCode 与待确认模板不一致。");
        }
        if (!pendingItems.standardTemplateCode().equals(request.standardTemplateCode())) {
            throw new IllegalArgumentException("用户确认请求中的 standardTemplateCode 与待确认标准模板不一致。");
        }

        Map<String, MappingConfirmation> mappingQuestions = pendingItems.mappingConfirmations().stream()
                .collect(Collectors.toMap(MappingConfirmation::targetFieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, OptionConfirmation> optionQuestions = pendingItems.optionConfirmations().stream()
                .collect(Collectors.toMap(OptionConfirmation::fieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, InputConfirmation> inputQuestions = pendingItems.inputConfirmations().stream()
                .collect(Collectors.toMap(InputConfirmation::fieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        validateDecisionCoverage(
                mappingQuestions.keySet(),
                request.mappingConfirmations().stream().map(MappingConfirmationDto::targetFieldCode).toList(),
                "字段映射确认"
        );
        validateDecisionCoverage(
                optionQuestions.keySet(),
                request.optionConfirmations().stream().map(OptionConfirmationDto::fieldCode).toList(),
                "枚举字段确认"
        );
        validateDecisionCoverage(
                inputQuestions.keySet(),
                request.inputConfirmations().stream().map(InputConfirmationDto::fieldCode).toList(),
                "手工输入字段确认"
        );

        List<MappingConfirmation> mappingConfirmations = request.mappingConfirmations().stream()
                .map(decision -> validateAndConvertMappingConfirmation(mappingQuestions.get(decision.targetFieldCode()), decision))
                .toList();
        List<OptionConfirmation> optionConfirmations = request.optionConfirmations().stream()
                .map(decision -> validateAndConvertOptionConfirmation(optionQuestions.get(decision.fieldCode()), decision))
                .toList();
        List<InputConfirmation> inputConfirmations = request.inputConfirmations().stream()
                .map(decision -> validateAndConvertInputConfirmation(inputQuestions.get(decision.fieldCode()), decision))
                .toList();

        return new UserConfirmationResult(
                request.taskId(),
                request.presetTemplateCode(),
                request.standardTemplateCode(),
                mappingConfirmations,
                optionConfirmations,
                inputConfirmations
        );
    }

    /**
     * 显式校验待确认字段与用户提交字段是否一一对应。
     */
    private void validateDecisionCoverage(Set<String> expectedFieldCodes, List<String> actualFieldCodeList, String decisionType) {
        Set<String> actualFieldCodes = new java.util.LinkedHashSet<>(actualFieldCodeList);
        if (actualFieldCodeList.size() != actualFieldCodes.size()) {
            throw new IllegalArgumentException(decisionType + "中存在重复字段提交: " + actualFieldCodeList);
        }
        if (!expectedFieldCodes.equals(actualFieldCodes)) {
            throw new IllegalArgumentException(
                    decisionType + "字段集合不匹配，期望 " + expectedFieldCodes + "，实际收到 " + actualFieldCodes
            );
        }
    }

    /**
     * 校验字段映射结果只能从候选源字段中选择。
     */
    private MappingConfirmation validateAndConvertMappingConfirmation(MappingConfirmation question, MappingConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("收到未知的字段映射确认项: " + decision.targetFieldCode());
        }
        Set<String> candidateCodes = question.candidates().stream()
                .map(SourceFieldCandidate::fieldCode)
                .collect(Collectors.toSet());
        for (String selectedSourceField : decision.selectedSourceFields()) {
            if (!candidateCodes.contains(selectedSourceField)) {
                throw new IllegalArgumentException("字段映射确认包含非法源字段: " + selectedSourceField);
            }
        }
        return new MappingConfirmation(
                question.targetFieldCode(),
                question.targetFieldName(),
                question.question(),
                question.candidates(),
                List.copyOf(decision.selectedSourceFields())
        );
    }

    /**
     * 校验枚举字段结果只能从候选值中选择。
     */
    private OptionConfirmation validateAndConvertOptionConfirmation(OptionConfirmation question, OptionConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("收到未知的枚举字段确认项: " + decision.fieldCode());
        }
        Set<String> allowedValues = question.options().stream()
                .map(OptionItem::code)
                .collect(Collectors.toSet());
        if (!allowedValues.contains(decision.selectedValue())) {
            throw new IllegalArgumentException("枚举字段确认包含非法选项: " + decision.selectedValue());
        }
        return new OptionConfirmation(
                question.fieldCode(),
                question.fieldName(),
                question.question(),
                question.options(),
                decision.selectedValue()
        );
    }

    /**
     * 校验手工输入字段结果必须有明确值。
     */
    private InputConfirmation validateAndConvertInputConfirmation(InputConfirmation question, InputConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("收到未知的手工输入确认项: " + decision.fieldCode());
        }
        String value = decision.inputValue() == null ? "" : decision.inputValue().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("手工输入字段不能为空: " + decision.fieldCode());
        }
        return new InputConfirmation(
                question.fieldCode(),
                question.fieldName(),
                question.question(),
                question.hint(),
                value
        );
    }

    /**
     * 构造字段映射确认项。
     *
     * <p>一期不做复杂语义推理，只把现有表头全部作为候选项显式展示，
     * 再用简单的名称相似度给出排序分数，避免在后端埋黑盒规则。</p>
     */
    private MappingConfirmation buildMappingConfirmation(String targetFieldCode, List<String> sourceHeaders) {
        List<SourceFieldCandidate> candidates = sourceHeaders.stream()
                .map(header -> new SourceFieldCandidate(header, header, computeCandidateScore(targetFieldCode, header)))
                .sorted(Comparator.comparing(SourceFieldCandidate::confidence).reversed())
                .toList();

        return new MappingConfirmation(
                targetFieldCode,
                "目标字段 " + targetFieldCode,
                "请选择目标字段 " + targetFieldCode + " 对应的源列。",
                candidates,
                List.of()
        );
    }

    /**
     * 计算候选字段分数。
     */
    private double computeCandidateScore(String targetFieldCode, String sourceHeader) {
        String normalizedTarget = targetFieldCode.trim().toLowerCase();
        String normalizedHeader = sourceHeader.trim().toLowerCase();
        if (normalizedTarget.equals(normalizedHeader)) {
            return 1.0D;
        }
        if (normalizedHeader.contains(normalizedTarget) || normalizedTarget.contains(normalizedHeader)) {
            return 0.8D;
        }
        return 0.5D;
    }

    /**
     * 根据规则构造枚举确认项。
     */
    private OptionConfirmation buildOptionConfirmation(ProcessingRuleItem ruleItem) {
        return new OptionConfirmation(
                resolveConfirmationFieldCode(ruleItem),
                "目标列 " + ruleItem.targetColumn(),
                "请确认目标列 " + ruleItem.targetColumn() + " 的取值。",
                ruleItem.options().stream()
                        .map(option -> new OptionItem(option, option))
                        .toList(),
                null
        );
    }

    /**
     * 根据规则构造输入确认项。
     */
    private InputConfirmation buildInputConfirmation(ProcessingRuleItem ruleItem) {
        return new InputConfirmation(
                resolveConfirmationFieldCode(ruleItem),
                "目标列 " + ruleItem.targetColumn(),
                "请填写目标列 " + ruleItem.targetColumn() + " 的值。",
                ruleItem.inputHint().isBlank() ? "请输入明确值" : ruleItem.inputHint(),
                null
        );
    }

    /**
     * 统一解析确认字段编码，优先使用规则中显式声明的字段名。
     */
    private String resolveConfirmationFieldCode(ProcessingRuleItem ruleItem) {
        if (ruleItem.userInputField() != null && !ruleItem.userInputField().isBlank()) {
            return ruleItem.userInputField();
        }
        return ruleItem.targetColumn();
    }

    /**
     * 显式要求模板识别结果必须先存在。
     */
    private TemplateRecognitionResult requireTemplateRecognitionResult(TaskSession session) {
        if (session.templateRecognitionResult() == null) {
            throw new IllegalStateException("生成确认请求前必须先完成模板识别。");
        }
        return session.templateRecognitionResult();
    }
}
