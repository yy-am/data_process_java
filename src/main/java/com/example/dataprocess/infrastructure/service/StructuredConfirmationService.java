package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.FieldMappingDecision;
import com.example.dataprocess.domain.model.InputFieldDecision;
import com.example.dataprocess.domain.model.OptionFieldDecision;
import com.example.dataprocess.domain.model.OptionItem;
import com.example.dataprocess.domain.model.RequiredInputQuestion;
import com.example.dataprocess.domain.model.RequiredOptionQuestion;
import com.example.dataprocess.domain.model.SourceFieldCandidate;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.TemplateCatalogItem;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UnclearMappingQuestion;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.example.dataprocess.interfaces.restful.request.FieldMappingDecisionDto;
import com.example.dataprocess.interfaces.restful.request.InputFieldDecisionDto;
import com.example.dataprocess.interfaces.restful.request.OptionFieldDecisionDto;
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

    private static final String PERIOD_FIELD_CODE = "period";
    private static final String D_FIELD_CODE = "D";

    private final TemplateCatalogService templateCatalogService;

    public StructuredConfirmationService(TemplateCatalogService templateCatalogService) {
        this.templateCatalogService = templateCatalogService;
    }

    /**
     * 基于模板识别结果构建固定 JSON 结构的确认请求。
     */
    public UserConfirmationItems buildUserConfirmationItems(TaskSession session) {
        TemplateRecognitionResult recognitionResult = requireTemplateRecognitionResult(session);
        TemplateCatalogItem catalogItem = templateCatalogService.getRequiredTemplate(
                session.inputType(),
                recognitionResult.templateCode()
        );

        List<String> unresolvedFields = recognitionResult.unresolvedTargetFields() == null
                ? List.of()
                : recognitionResult.unresolvedTargetFields();

        List<UnclearMappingQuestion> unclearMappings = new ArrayList<>();
        List<RequiredOptionQuestion> requiredOptionFields = new ArrayList<>();
        List<RequiredInputQuestion> requiredInputFields = new ArrayList<>();

        for (String targetField : catalogItem.targetFields()) {
            if (PERIOD_FIELD_CODE.equals(targetField)) {
                requiredOptionFields.add(buildPeriodQuestion());
                continue;
            }
            if (D_FIELD_CODE.equals(targetField)) {
                requiredInputFields.add(buildFieldDQuestion());
                continue;
            }
            if (unresolvedFields.contains(targetField)) {
                unclearMappings.add(buildMappingQuestion(targetField, session.sourceHeaders()));
            }
        }

        if (unclearMappings.isEmpty() && requiredOptionFields.isEmpty() && requiredInputFields.isEmpty()) {
            throw new IllegalStateException("模板识别要求人工确认，但后端未生成任何确认项。");
        }

        return new UserConfirmationItems(
                session.taskId(),
                recognitionResult.templateCode(),
                List.copyOf(unclearMappings),
                List.copyOf(requiredOptionFields),
                List.copyOf(requiredInputFields)
        );
    }

    /**
     * 将前端提交的确认 JSON 转换为通过校验的领域结果。
     */
    public UserConfirmationResult applyConfirmationRequest(UserConfirmationItems pendingItems, UserConfirmationRequest request) {
        if (!pendingItems.taskId().equals(request.taskId())) {
            throw new IllegalArgumentException("用户确认请求中的 taskId 与待确认任务不一致。");
        }
        if (!pendingItems.templateCode().equals(request.templateCode())) {
            throw new IllegalArgumentException("用户确认请求中的 templateCode 与待确认模板不一致。");
        }

        Map<String, UnclearMappingQuestion> mappingQuestions = pendingItems.unclearMappings().stream()
                .collect(Collectors.toMap(UnclearMappingQuestion::targetFieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, RequiredOptionQuestion> optionQuestions = pendingItems.requiredOptionFields().stream()
                .collect(Collectors.toMap(RequiredOptionQuestion::fieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, RequiredInputQuestion> inputQuestions = pendingItems.requiredInputFields().stream()
                .collect(Collectors.toMap(RequiredInputQuestion::fieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        validateDecisionCoverage(
                mappingQuestions.keySet(),
                request.mappingDecisions().stream().map(FieldMappingDecisionDto::targetFieldCode).toList(),
                "字段映射确认"
        );
        validateDecisionCoverage(
                optionQuestions.keySet(),
                request.optionFieldDecisions().stream().map(OptionFieldDecisionDto::fieldCode).toList(),
                "枚举字段确认"
        );
        validateDecisionCoverage(
                inputQuestions.keySet(),
                request.inputFieldDecisions().stream().map(InputFieldDecisionDto::fieldCode).toList(),
                "手工输入字段确认"
        );

        List<FieldMappingDecision> mappingDecisions = request.mappingDecisions().stream()
                .map(decision -> validateAndConvertMappingDecision(mappingQuestions.get(decision.targetFieldCode()), decision))
                .toList();
        List<OptionFieldDecision> optionFieldDecisions = request.optionFieldDecisions().stream()
                .map(decision -> validateAndConvertOptionDecision(optionQuestions.get(decision.fieldCode()), decision))
                .toList();
        List<InputFieldDecision> inputFieldDecisions = request.inputFieldDecisions().stream()
                .map(decision -> validateAndConvertInputDecision(inputQuestions.get(decision.fieldCode()), decision))
                .toList();

        return new UserConfirmationResult(
                request.taskId(),
                request.templateCode(),
                mappingDecisions,
                optionFieldDecisions,
                inputFieldDecisions
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
    private FieldMappingDecision validateAndConvertMappingDecision(UnclearMappingQuestion question, FieldMappingDecisionDto decision) {
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
        return new FieldMappingDecision(
                decision.targetFieldCode(),
                List.copyOf(decision.selectedSourceFields())
        );
    }

    /**
     * 校验枚举字段结果只能从候选值中选择。
     */
    private OptionFieldDecision validateAndConvertOptionDecision(RequiredOptionQuestion question, OptionFieldDecisionDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("收到未知的枚举字段确认项: " + decision.fieldCode());
        }
        Set<String> allowedValues = question.options().stream()
                .map(OptionItem::value)
                .collect(Collectors.toSet());
        if (!allowedValues.contains(decision.selectedValue())) {
            throw new IllegalArgumentException("枚举字段确认包含非法选项: " + decision.selectedValue());
        }
        return new OptionFieldDecision(decision.fieldCode(), decision.selectedValue());
    }

    /**
     * 校验手工输入字段结果必须有明确值。
     */
    private InputFieldDecision validateAndConvertInputDecision(RequiredInputQuestion question, InputFieldDecisionDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("收到未知的手工输入确认项: " + decision.fieldCode());
        }
        String value = decision.inputValue() == null ? "" : decision.inputValue().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("手工输入字段不能为空: " + decision.fieldCode());
        }
        return new InputFieldDecision(decision.fieldCode(), value);
    }

    /**
     * 构造字段映射问题。
     *
     * <p>一期不做复杂语义推理，只把现有表头全部作为候选项显式展示，
     * 再用简单的名称相似度给出排序分数，避免在后端埋黑盒规则。</p>
     */
    private UnclearMappingQuestion buildMappingQuestion(String targetFieldCode, List<String> sourceHeaders) {
        List<SourceFieldCandidate> candidates = sourceHeaders.stream()
                .map(header -> new SourceFieldCandidate(header, header, computeCandidateScore(targetFieldCode, header)))
                .sorted(Comparator.comparing(SourceFieldCandidate::confidence).reversed())
                .toList();

        return new UnclearMappingQuestion(
                targetFieldCode,
                "目标字段 " + targetFieldCode,
                "请选择目标字段 " + targetFieldCode + " 对应的源列。",
                candidates
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
     * 构造 period 枚举确认问题。
     */
    private RequiredOptionQuestion buildPeriodQuestion() {
        return new RequiredOptionQuestion(
                PERIOD_FIELD_CODE,
                "期间",
                "请选择 period 字段的值。",
                List.of(
                        new OptionItem("2026-04", "2026-04"),
                        new OptionItem("2026-05", "2026-05"),
                        new OptionItem("2026-06", "2026-06")
                )
        );
    }

    /**
     * 构造 D 手工输入问题。
     */
    private RequiredInputQuestion buildFieldDQuestion() {
        return new RequiredInputQuestion(
                D_FIELD_CODE,
                "字段 D",
                "请输入字段 D 的值。",
                "例如：manual-fill"
        );
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
