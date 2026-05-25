package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.PresetUserTemplateDefinition;
import com.example.dataprocess.domain.model.StandardTemplateDefinition;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Performs a single template recognition call against the uploaded input snapshot.
 */
@Service
public class TemplateRecognitionService {

    private static final String PROMPT_RESOURCE_PATH = "prompts/template-recognition-prompt.md";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final TemplateCatalogService templateCatalogService;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;

    public TemplateRecognitionService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            TemplateCatalogService templateCatalogService,
            PromptTemplateService promptTemplateService
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.templateCatalogService = templateCatalogService;
        this.systemPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "System Prompt");
        this.userPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "User Prompt Template");
    }

    public TemplateRecognitionResult recognize(InputSnapshot inputSnapshot) {
        List<PresetUserTemplateDefinition> presetTemplates = templateCatalogService.readPresetTemplateCatalog();
        List<StandardTemplateDefinition> standardTemplates = presetTemplates.stream()
                .map(PresetUserTemplateDefinition::standardTemplateCode)
                .distinct()
                .map(templateCatalogService::getRequiredStandardTemplate)
                .toList();

        TemplateRecognitionResult result = ChatClient.create(chatModel)
                .prompt()
                .system(systemPromptTemplate)
                .user(buildUserPrompt(inputSnapshot, presetTemplates, standardTemplates))
                .call()
                .entity(TemplateRecognitionResult.class);

        return normalizeAndValidateResult(result, presetTemplates);
    }

    private String buildUserPrompt(
            InputSnapshot inputSnapshot,
            List<PresetUserTemplateDefinition> presetTemplates,
            List<StandardTemplateDefinition> standardTemplates
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSnapshot", inputSnapshot);
        payload.put("presetTemplates", presetTemplates);
        payload.put("standardTemplates", standardTemplates);

        return userPromptTemplate.replace("{payload-json}", writeJson(payload));
    }

    private TemplateRecognitionResult normalizeAndValidateResult(
            TemplateRecognitionResult result,
            List<PresetUserTemplateDefinition> presetTemplates
    ) {
        if (result == null) {
            throw new IllegalStateException("Template recognition did not return a result.");
        }
        if (isBlank(result.presetTemplateCode())) {
            throw new IllegalStateException("Template recognition result is missing presetTemplateCode.");
        }
        if (isBlank(result.standardTemplateCode())) {
            throw new IllegalStateException("Template recognition result is missing standardTemplateCode.");
        }

        Map<String, PresetUserTemplateDefinition> presetTemplateMap = presetTemplates.stream()
                .collect(Collectors.toMap(
                        PresetUserTemplateDefinition::presetTemplateCode,
                        template -> template
                ));
        PresetUserTemplateDefinition matchedTemplate = presetTemplateMap.get(result.presetTemplateCode());
        if (matchedTemplate == null) {
            throw new IllegalStateException(
                    "Template recognition returned a presetTemplateCode outside the catalog: "
                            + result.presetTemplateCode()
            );
        }
        if (!matchedTemplate.standardTemplateCode().equals(result.standardTemplateCode())) {
            throw new IllegalStateException("standardTemplateCode does not match the catalog mapping.");
        }
        if (!matchedTemplate.sceneCode().equals(result.sceneCode())) {
            throw new IllegalStateException("sceneCode does not match the catalog mapping.");
        }
        if (!matchedTemplate.countryCode().equals(result.countryCode())) {
            throw new IllegalStateException("countryCode does not match the catalog mapping.");
        }

        return new TemplateRecognitionResult(
                result.presetTemplateCode(),
                result.standardTemplateCode(),
                result.sceneCode(),
                result.countryCode(),
                result.confidence(),
                Boolean.TRUE.equals(result.needUserConfirm()),
                result.reason()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize template recognition payload.", ex);
        }
    }
}
