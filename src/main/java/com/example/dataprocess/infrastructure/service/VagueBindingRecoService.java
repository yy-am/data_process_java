package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.ProcessingRuleDocument;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.VagueBindingRecoItem;
import com.example.dataprocess.domain.model.VagueBindingRecoResult;
import com.example.dataprocess.domain.model.VagueBindingRecoStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uses the model to recognize which uploaded headers should bind to rule source columns.
 */
@Service
public class VagueBindingRecoService {

    private static final String PROMPT_RESOURCE_PATH = "prompts/vague-binding-reco-prompt.md";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;

    public VagueBindingRecoService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.systemPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "System Prompt");
        this.userPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "User Prompt Template");
    }

    public VagueBindingRecoResult recognize(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            ProcessingRuleDocument processingRuleDocument
    ) {
        VagueBindingRecoResult result = ChatClient.create(chatModel)
                .prompt()
                .system(systemPromptTemplate)
                .user(buildUserPrompt(inputSnapshot, templateRecognitionResult, processingRuleDocument))
                .call()
                .entity(VagueBindingRecoResult.class);

        return normalizeAndValidate(result, inputSnapshot, templateRecognitionResult, processingRuleDocument);
    }

    private String buildUserPrompt(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            ProcessingRuleDocument processingRuleDocument
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSnapshot", inputSnapshot);
        payload.put("templateRecognitionResult", templateRecognitionResult);
        payload.put("processingRuleDocument", processingRuleDocument);

        return userPromptTemplate.replace("{payload-json}", writeJson(payload));
    }

    private VagueBindingRecoResult normalizeAndValidate(
            VagueBindingRecoResult result,
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            ProcessingRuleDocument processingRuleDocument
    ) {
        if (result == null) {
            throw new IllegalStateException("Vague binding recognition did not return a result.");
        }
        if (!inputSnapshot.taskId().equals(result.taskId())) {
            throw new IllegalStateException("Vague binding recognition returned a mismatched taskId.");
        }
        if (!templateRecognitionResult.presetTemplateCode().equals(result.presetTemplateCode())) {
            throw new IllegalStateException("Vague binding recognition returned a mismatched presetTemplateCode.");
        }

        List<ProcessingRuleItem> ruleItemsWithSources = processingRuleDocument.ruleItems().stream()
                .filter(ruleItem -> !ruleItem.sourceColumns().isEmpty())
                .toList();
        Set<String> expectedKeys = ruleItemsWithSources.stream()
                .flatMap(ruleItem -> ruleItem.sourceColumns().stream()
                        .map(sourceColumn -> buildExpectedKey(ruleItem, sourceColumn)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualKeys = result.items().stream()
                .map(this::buildActualKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!expectedKeys.equals(actualKeys)) {
            throw new IllegalStateException("Vague binding recognition coverage does not match rule dependencies.");
        }

        Set<String> availableHeaders = Set.copyOf(inputSnapshot.normalizedHeaders());
        Map<String, ProcessingRuleItem> ruleItemByTargetColumn = ruleItemsWithSources.stream()
                .collect(Collectors.toMap(ProcessingRuleItem::targetColumn, ruleItem -> ruleItem, (left, right) -> left));

        List<VagueBindingRecoItem> normalizedItems = result.items().stream()
                .map(item -> normalizeAndValidateItem(item, availableHeaders, ruleItemByTargetColumn))
                .toList();

        return new VagueBindingRecoResult(
                result.taskId(),
                result.presetTemplateCode(),
                List.copyOf(normalizedItems)
        );
    }

    private VagueBindingRecoItem normalizeAndValidateItem(
            VagueBindingRecoItem item,
            Set<String> availableHeaders,
            Map<String, ProcessingRuleItem> ruleItemByTargetColumn
    ) {
        if (item == null) {
            throw new IllegalStateException("Vague binding recognition returned a null item.");
        }
        ProcessingRuleItem ruleItem = ruleItemByTargetColumn.get(item.targetColumn());
        if (ruleItem == null) {
            throw new IllegalStateException("Unknown targetColumn returned by vague binding recognition: " + item.targetColumn());
        }
        if (!ruleItem.ruleType().equals(item.ruleType())) {
            throw new IllegalStateException("ruleType does not match the rule document for targetColumn: " + item.targetColumn());
        }
        if (!ruleItem.sourceColumns().contains(item.sourceColumn())) {
            throw new IllegalStateException("sourceColumn is not declared by the rule document: " + item.sourceColumn());
        }

        VagueBindingRecoStatus status = item.status();
        if (status == null) {
            throw new IllegalStateException("Vague binding recognition item is missing status.");
        }

        List<String> candidateHeaders = item.candidateHeaders() == null ? List.of() : List.copyOf(item.candidateHeaders());
        for (String candidateHeader : candidateHeaders) {
            if (!availableHeaders.contains(candidateHeader)) {
                throw new IllegalStateException("candidateHeaders contains a header outside the uploaded headers: " + candidateHeader);
            }
        }

        String selectedHeader = item.selectedHeader();
        if (selectedHeader != null && !availableHeaders.contains(selectedHeader)) {
            throw new IllegalStateException("selectedHeader is outside the uploaded headers: " + selectedHeader);
        }

        return switch (status) {
            case CONFIRMED -> {
                if (selectedHeader == null || selectedHeader.isBlank()) {
                    throw new IllegalStateException("CONFIRMED item must contain selectedHeader.");
                }
                if (!candidateHeaders.isEmpty()) {
                    throw new IllegalStateException("CONFIRMED item must not contain candidateHeaders.");
                }
                yield new VagueBindingRecoItem(
                        item.targetColumn(),
                        item.ruleType(),
                        item.sourceColumn(),
                        status,
                        selectedHeader,
                        List.of(),
                        item.reason()
                );
            }
            case NEEDS_CONFIRMATION -> {
                if (selectedHeader != null) {
                    throw new IllegalStateException("NEEDS_CONFIRMATION item must not contain selectedHeader.");
                }
                if (candidateHeaders.size() < 2) {
                    throw new IllegalStateException("NEEDS_CONFIRMATION item must contain at least two candidateHeaders.");
                }
                yield new VagueBindingRecoItem(
                        item.targetColumn(),
                        item.ruleType(),
                        item.sourceColumn(),
                        status,
                        null,
                        candidateHeaders,
                        item.reason()
                );
            }
            case MISSING -> {
                if (selectedHeader != null) {
                    throw new IllegalStateException("MISSING item must not contain selectedHeader.");
                }
                if (!candidateHeaders.isEmpty()) {
                    throw new IllegalStateException("MISSING item must not contain candidateHeaders.");
                }
                yield new VagueBindingRecoItem(
                        item.targetColumn(),
                        item.ruleType(),
                        item.sourceColumn(),
                        status,
                        null,
                        List.of(),
                        item.reason()
                );
            }
        };
    }

    private String buildExpectedKey(ProcessingRuleItem ruleItem, String sourceColumn) {
        return ruleItem.targetColumn() + "|" + ruleItem.ruleType() + "|" + sourceColumn;
    }

    private String buildActualKey(VagueBindingRecoItem item) {
        return item.targetColumn() + "|" + item.ruleType() + "|" + item.sourceColumn();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize vague binding recognition payload.", ex);
        }
    }
}
