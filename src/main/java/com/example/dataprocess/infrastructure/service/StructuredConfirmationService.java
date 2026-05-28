package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputConfirmation;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.MappingConfirmation;
import com.example.dataprocess.domain.model.OptionConfirmation;
import com.example.dataprocess.domain.model.OptionItem;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import com.example.dataprocess.domain.model.SourceFieldCandidate;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.UserConfirmationPreparationResult;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.example.dataprocess.domain.model.VagueBindingRecoItem;
import com.example.dataprocess.domain.model.VagueBindingRecoResult;
import com.example.dataprocess.domain.model.VagueBindingRecoStatus;
import com.example.dataprocess.interfaces.restful.request.InputConfirmationDto;
import com.example.dataprocess.interfaces.restful.request.MappingConfirmationDto;
import com.example.dataprocess.interfaces.restful.request.OptionConfirmationDto;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 结构化用户确认服务。
 *
 * <p>它负责把字段绑定识别中的不确定项、以及 USER_CONFIRM 规则，转换为前端可展示的确认项；
 * 用户提交确认后，再把提交内容校验并转换成领域侧确认结果。</p>
 */
@Service
public class StructuredConfirmationService {

    private static final String USER_CONFIRM_RULE_TYPE = "USER_CONFIRM";

    private final ProcessingRuleLoader processingRuleLoader;
    private final VagueBindingRecoService vagueBindingRecoService;

    public StructuredConfirmationService(
            ProcessingRuleLoader processingRuleLoader,
            VagueBindingRecoService vagueBindingRecoService
    ) {
        this.processingRuleLoader = processingRuleLoader;
        this.vagueBindingRecoService = vagueBindingRecoService;
    }

    /**
     * 生成本轮用户确认所需的所有结构化题目，同时保留完整字段绑定识别结果。
     */
    public UserConfirmationPreparationResult buildUserConfirmation(TaskSession session) {
        TemplateRecognitionResult recognitionResult = requireTemplateRecognitionResult(session);
        ProcessingRule processingRule = processingRuleLoader.load(recognitionResult.presetTemplateCode());
        InputSnapshot inputSnapshot = new InputSnapshot(
                session.taskId(),
                session.inputType(),
                session.sourceHeaders(),
                session.sampleRows()
        );
        VagueBindingRecoResult vagueBindingRecoResult = vagueBindingRecoService.recognize(
                inputSnapshot,
                recognitionResult,
                processingRule
        );

        // 字段绑定只有在模型无法唯一判断时才生成映射确认项。
        List<MappingConfirmation> mappingConfirmations = vagueBindingRecoResult.items().stream()
                .filter(item -> item.status() == VagueBindingRecoStatus.NEEDS_CONFIRMATION)
                .map(this::buildMappingConfirmation)
                .toList();

        // USER_CONFIRM 且带 options 的规则，生成用户选值确认项。
        List<OptionConfirmation> optionConfirmations = processingRule.ruleItems().stream()
                .filter(ruleItem -> USER_CONFIRM_RULE_TYPE.equals(ruleItem.ruleType()))
                .filter(ruleItem -> !ruleItem.options().isEmpty())
                .map(this::buildOptionConfirmation)
                .toList();

        // USER_CONFIRM 且没有 options 的规则，生成用户手工输入确认项。
        // todo，此处待修改为读取必填字段配置。
        List<InputConfirmation> inputConfirmations = processingRule.ruleItems().stream()
                .filter(ruleItem -> USER_CONFIRM_RULE_TYPE.equals(ruleItem.ruleType()))
                .filter(ruleItem -> ruleItem.options().isEmpty())
                .map(this::buildInputConfirmation)
                .toList();

        // 确认项给前端展示；完整 vagueBindingRecoResult 写回 state，供后续 DSL 上下文生成使用。
        UserConfirmationItems userConfirmationItems = new UserConfirmationItems(
                session.taskId(),
                recognitionResult.presetTemplateCode(),
                recognitionResult.standardTemplateCode(),
                List.copyOf(mappingConfirmations),
                List.copyOf(optionConfirmations),
                List.copyOf(inputConfirmations)
        );
        return new UserConfirmationPreparationResult(processingRule, vagueBindingRecoResult, userConfirmationItems);
    }

    /**
     * 校验用户提交的确认结果，并转换成后续工作流使用的领域对象。
     */
    public UserConfirmationResult applyConfirmationRequest(UserConfirmationItems pendingItems, UserConfirmationRequest request) {
        if (!pendingItems.taskId().equals(request.taskId())) {
            throw new IllegalArgumentException("taskId in the confirmation request does not match the pending task.");
        }
        if (!pendingItems.presetTemplateCode().equals(request.presetTemplateCode())) {
            throw new IllegalArgumentException("presetTemplateCode in the confirmation request does not match.");
        }
        if (!pendingItems.standardTemplateCode().equals(request.standardTemplateCode())) {
            throw new IllegalArgumentException("standardTemplateCode in the confirmation request does not match.");
        }

        // 构建待确认题目的索引，用于校验用户提交是否完整、是否越权提交未知字段。
        Map<String, MappingConfirmation> mappingQuestions = pendingItems.mappingConfirmations().stream()
                .collect(Collectors.toMap(MappingConfirmation::targetFieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, OptionConfirmation> optionQuestions = pendingItems.optionConfirmations().stream()
                .collect(Collectors.toMap(OptionConfirmation::targetColumn, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, InputConfirmation> inputQuestions = pendingItems.inputConfirmations().stream()
                .collect(Collectors.toMap(InputConfirmation::targetColumn, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        // 三类确认都要求“必须全部回答、不能重复、不能多交未知项”。
        validateDecisionCoverage(
                mappingQuestions.keySet(),
                request.mappingConfirmations().stream().map(MappingConfirmationDto::targetFieldCode).toList(),
                "mapping confirmation"
        );
        validateDecisionCoverage(
                optionQuestions.keySet(),
                request.optionConfirmations().stream().map(OptionConfirmationDto::targetColumn).toList(),
                "option confirmation"
        );
        validateDecisionCoverage(
                inputQuestions.keySet(),
                request.inputConfirmations().stream().map(InputConfirmationDto::targetColumn).toList(),
                "input confirmation"
        );

        // 覆盖性校验通过后，再逐项做候选值/选项值/输入值校验并转换。
        List<MappingConfirmation> mappingConfirmations = request.mappingConfirmations().stream()
                .map(decision -> validateAndConvertMappingConfirmation(mappingQuestions.get(decision.targetFieldCode()), decision))
                .toList();
        List<OptionConfirmation> optionConfirmations = request.optionConfirmations().stream()
                .map(decision -> validateAndConvertOptionConfirmation(optionQuestions.get(decision.targetColumn()), decision))
                .toList();
        List<InputConfirmation> inputConfirmations = request.inputConfirmations().stream()
                .map(decision -> validateAndConvertInputConfirmation(inputQuestions.get(decision.targetColumn()), decision))
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
     * 校验某类确认提交项与待确认题目集合完全一致。
     */
    private void validateDecisionCoverage(Set<String> expectedKeys, List<String> actualKeyList, String decisionType) {
        Set<String> actualKeys = new java.util.LinkedHashSet<>(actualKeyList);
        if (actualKeyList.size() != actualKeys.size()) {
            throw new IllegalArgumentException(decisionType + " contains duplicate keys: " + actualKeyList);
        }
        if (!expectedKeys.equals(actualKeys)) {
            throw new IllegalArgumentException(
                    decisionType + " key set mismatch, expected " + expectedKeys + ", actual " + actualKeys
            );
        }
    }

    /**
     * 校验字段映射确认：用户选择的源表头必须来自候选集合。
     */
    private MappingConfirmation validateAndConvertMappingConfirmation(MappingConfirmation question, MappingConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("Unknown mapping confirmation item: " + decision.targetFieldCode());
        }
        // 候选集合来自模型识别出的 uploaded header，用户不能提交候选外字段。
        Set<String> candidateCodes = question.candidates().stream()
                .map(SourceFieldCandidate::fieldCode)
                .collect(Collectors.toSet());
        for (String selectedSourceField : decision.selectedSourceFields()) {
            if (!candidateCodes.contains(selectedSourceField)) {
                throw new IllegalArgumentException("Mapping confirmation contains an illegal source field: " + selectedSourceField);
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
     * 校验选值确认：用户选择的值必须在规则声明的 options 范围内。
     */
    private OptionConfirmation validateAndConvertOptionConfirmation(OptionConfirmation question, OptionConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("Unknown option confirmation item: " + decision.targetColumn());
        }
        // options 是规则文档允许的目标字段取值范围。
        Set<String> allowedValues = question.options().stream()
                .map(OptionItem::code)
                .collect(Collectors.toSet());
        if (!allowedValues.contains(decision.selectedValue())) {
            throw new IllegalArgumentException("Option confirmation contains an illegal option: " + decision.selectedValue());
        }
        return new OptionConfirmation(
                question.targetColumn(),
                question.question(),
                question.options(),
                decision.selectedValue()
        );
    }

    /**
     * 校验输入确认：用户输入值必须非空，并统一 trim 后写入结果。
     */
    private InputConfirmation validateAndConvertInputConfirmation(InputConfirmation question, InputConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("Unknown input confirmation item: " + decision.targetColumn());
        }
        // 输入类确认暂时只做非空校验，后续可按 targetColumn 增加类型/格式校验。
        String value = decision.inputValue() == null ? "" : decision.inputValue().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Input confirmation cannot be empty: " + decision.targetColumn());
        }
        return new InputConfirmation(
                question.targetColumn(),
                question.question(),
                question.hint(),
                value
        );
    }

    /**
     * 把 NEEDS_CONFIRMATION 的字段绑定识别项转换成前端映射确认题目。
     */
    private MappingConfirmation buildMappingConfirmation(VagueBindingRecoItem item) {
        // 候选项直接使用上传表头，后续用户提交时也按这个 header 值校验。
        List<SourceFieldCandidate> candidates = item.candidateHeaders().stream()
                .map(header -> new SourceFieldCandidate(header, header, 1.0D))
                .toList();
        return new MappingConfirmation(
                buildMappingConfirmationCode(item),
                "Rule source field " + item.sourceColumn(),
                "Please confirm which uploaded header should bind to source field "
                        + item.sourceColumn()
                        + " for target column "
                        + item.targetColumn()
                        + ".",
                candidates,
                List.of()
        );
    }

    /**
     * 生成映射确认项的稳定编码，避免同一目标字段有多个 sourceColumn 时发生冲突。
     */
    private String buildMappingConfirmationCode(VagueBindingRecoItem item) {
        return item.targetColumn() + "::" + item.sourceColumn();
    }

    /**
     * 把 USER_CONFIRM + options 规则转换成目标字段选值确认题目。
     */
    private OptionConfirmation buildOptionConfirmation(ProcessingRuleItem ruleItem) {
        return new OptionConfirmation(
                ruleItem.targetColumn(),
                "Please confirm the value for target column " + ruleItem.targetColumn() + ".",
                ruleItem.options().stream()
                        .map(option -> new OptionItem(option, option))
                        .toList(),
                null
        );
    }

    /**
     * 把 USER_CONFIRM 无 options 规则转换成目标字段手工输入确认题目。
     */
    private InputConfirmation buildInputConfirmation(ProcessingRuleItem ruleItem) {
        return new InputConfirmation(
                ruleItem.targetColumn(),
                "Please enter the value for target column " + ruleItem.targetColumn() + ".",
                ruleItem.inputHint().isBlank() ? "Enter a concrete value." : ruleItem.inputHint(),
                null
        );
    }

    /**
     * 确保模板识别已完成，否则无法定位对应规则和确认项。
     */
    private TemplateRecognitionResult requireTemplateRecognitionResult(TaskSession session) {
        if (session.templateRecognitionResult() == null) {
            throw new IllegalStateException("Template recognition must complete before building confirmation items.");
        }
        return session.templateRecognitionResult();
    }
}
