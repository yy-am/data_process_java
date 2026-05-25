package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputConfirmation;
import com.example.dataprocess.domain.model.InputSnapshot;
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
 * Builds structured confirmation payloads and validates user confirmation input.
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

    public UserConfirmationItems buildUserConfirmationItems(TaskSession session) {
        TemplateRecognitionResult recognitionResult = requireTemplateRecognitionResult(session);
        ProcessingRuleDocument ruleDocument = processingRuleLoader.load(recognitionResult.presetTemplateCode());
        InputSnapshot inputSnapshot = new InputSnapshot(
                session.taskId(),
                session.inputType(),
                session.sourceHeaders(),
                session.sampleRows()
        );
        VagueBindingRecoResult vagueBindingRecoResult = vagueBindingRecoService.recognize(
                inputSnapshot,
                recognitionResult,
                ruleDocument
        );

        List<MappingConfirmation> mappingConfirmations = vagueBindingRecoResult.items().stream()
                .filter(item -> item.status() == VagueBindingRecoStatus.NEEDS_CONFIRMATION)
                .map(this::buildMappingConfirmation)
                .toList();

        List<OptionConfirmation> optionConfirmations = ruleDocument.ruleItems().stream()
                .filter(ruleItem -> USER_CONFIRM_RULE_TYPE.equals(ruleItem.ruleType()))
                .filter(ruleItem -> !ruleItem.options().isEmpty())
                .map(this::buildOptionConfirmation)
                .toList();

        List<InputConfirmation> inputConfirmations = ruleDocument.ruleItems().stream()
                .filter(ruleItem -> USER_CONFIRM_RULE_TYPE.equals(ruleItem.ruleType()))
                .filter(ruleItem -> ruleItem.options().isEmpty())
                .map(this::buildInputConfirmation)
                .toList();

        return new UserConfirmationItems(
                session.taskId(),
                recognitionResult.presetTemplateCode(),
                recognitionResult.standardTemplateCode(),
                List.copyOf(mappingConfirmations),
                List.copyOf(optionConfirmations),
                List.copyOf(inputConfirmations)
        );
    }

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

        Map<String, MappingConfirmation> mappingQuestions = pendingItems.mappingConfirmations().stream()
                .collect(Collectors.toMap(MappingConfirmation::targetFieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, OptionConfirmation> optionQuestions = pendingItems.optionConfirmations().stream()
                .collect(Collectors.toMap(OptionConfirmation::fieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, InputConfirmation> inputQuestions = pendingItems.inputConfirmations().stream()
                .collect(Collectors.toMap(InputConfirmation::fieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        validateDecisionCoverage(
                mappingQuestions.keySet(),
                request.mappingConfirmations().stream().map(MappingConfirmationDto::targetFieldCode).toList(),
                "mapping confirmation"
        );
        validateDecisionCoverage(
                optionQuestions.keySet(),
                request.optionConfirmations().stream().map(OptionConfirmationDto::fieldCode).toList(),
                "option confirmation"
        );
        validateDecisionCoverage(
                inputQuestions.keySet(),
                request.inputConfirmations().stream().map(InputConfirmationDto::fieldCode).toList(),
                "input confirmation"
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

    private void validateDecisionCoverage(Set<String> expectedFieldCodes, List<String> actualFieldCodeList, String decisionType) {
        Set<String> actualFieldCodes = new java.util.LinkedHashSet<>(actualFieldCodeList);
        if (actualFieldCodeList.size() != actualFieldCodes.size()) {
            throw new IllegalArgumentException(decisionType + " contains duplicate field codes: " + actualFieldCodeList);
        }
        if (!expectedFieldCodes.equals(actualFieldCodes)) {
            throw new IllegalArgumentException(
                    decisionType + " field set mismatch, expected " + expectedFieldCodes + ", actual " + actualFieldCodes
            );
        }
    }

    private MappingConfirmation validateAndConvertMappingConfirmation(MappingConfirmation question, MappingConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("Unknown mapping confirmation item: " + decision.targetFieldCode());
        }
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

    private OptionConfirmation validateAndConvertOptionConfirmation(OptionConfirmation question, OptionConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("Unknown option confirmation item: " + decision.fieldCode());
        }
        Set<String> allowedValues = question.options().stream()
                .map(OptionItem::code)
                .collect(Collectors.toSet());
        if (!allowedValues.contains(decision.selectedValue())) {
            throw new IllegalArgumentException("Option confirmation contains an illegal option: " + decision.selectedValue());
        }
        return new OptionConfirmation(
                question.fieldCode(),
                question.fieldName(),
                question.question(),
                question.options(),
                decision.selectedValue()
        );
    }

    private InputConfirmation validateAndConvertInputConfirmation(InputConfirmation question, InputConfirmationDto decision) {
        if (question == null) {
            throw new IllegalArgumentException("Unknown input confirmation item: " + decision.fieldCode());
        }
        String value = decision.inputValue() == null ? "" : decision.inputValue().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Input confirmation cannot be empty: " + decision.fieldCode());
        }
        return new InputConfirmation(
                question.fieldCode(),
                question.fieldName(),
                question.question(),
                question.hint(),
                value
        );
    }

    private MappingConfirmation buildMappingConfirmation(VagueBindingRecoItem item) {
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

    private String buildMappingConfirmationCode(VagueBindingRecoItem item) {
        return item.targetColumn() + "::" + item.sourceColumn();
    }

    private OptionConfirmation buildOptionConfirmation(ProcessingRuleItem ruleItem) {
        return new OptionConfirmation(
                resolveConfirmationFieldCode(ruleItem),
                "Target column " + ruleItem.targetColumn(),
                "Please confirm the value for target column " + ruleItem.targetColumn() + ".",
                ruleItem.options().stream()
                        .map(option -> new OptionItem(option, option))
                        .toList(),
                null
        );
    }

    private InputConfirmation buildInputConfirmation(ProcessingRuleItem ruleItem) {
        return new InputConfirmation(
                resolveConfirmationFieldCode(ruleItem),
                "Target column " + ruleItem.targetColumn(),
                "Please enter the value for target column " + ruleItem.targetColumn() + ".",
                ruleItem.inputHint().isBlank() ? "Enter a concrete value." : ruleItem.inputHint(),
                null
        );
    }

    private String resolveConfirmationFieldCode(ProcessingRuleItem ruleItem) {
        if (ruleItem.userInputField() != null && !ruleItem.userInputField().isBlank()) {
            return ruleItem.userInputField();
        }
        return ruleItem.targetColumn();
    }

    private TemplateRecognitionResult requireTemplateRecognitionResult(TaskSession session) {
        if (session.templateRecognitionResult() == null) {
            throw new IllegalStateException("Template recognition must complete before building confirmation items.");
        }
        return session.templateRecognitionResult();
    }
}
