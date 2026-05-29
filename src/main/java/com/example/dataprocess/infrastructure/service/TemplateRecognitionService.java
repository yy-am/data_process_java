package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.PresetUserTemplateDefinition;
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
 * 模板识别服务。
 *
 * <p>它把用户上传文件的输入快照和完整模板目录 Markdown 原文交给 AI，
 * 由 AI 从目录中选择最匹配的预置用户模板。</p>
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

    /**
     * 根据输入快照和完整模板目录识别预置用户模板。
     */
    public TemplateRecognitionResult recognize(InputSnapshot inputSnapshot) {
        String templateCatalogMarkdown = templateCatalogService.readTemplateCatalogMarkdown();
        List<PresetUserTemplateDefinition> presetTemplates = templateCatalogService.readPresetTemplateCatalog();

        TemplateRecognitionResult result = ChatClient.create(chatModel)
                .prompt()
                .system(systemPromptTemplate)
                .user(buildUserPrompt(inputSnapshot, templateCatalogMarkdown))
                .call()
                .entity(TemplateRecognitionResult.class);

        return normalizeAndValidateResult(result, presetTemplates);
    }

    /**
     * 构造模板识别提示词，模板目录保持 Markdown 原文，不提前改写成结构化列表。
     */
    private String buildUserPrompt(
            InputSnapshot inputSnapshot,
            String templateCatalogMarkdown
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSnapshot", inputSnapshot);
        payload.put("templateCatalogMarkdown", templateCatalogMarkdown);

        return userPromptTemplate.replace("{payload-json}", writeJson(payload));
    }

    /**
     * 校验 AI 返回结果必须来自模板目录解析出的预置模板关系。
     */
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
        if (!matchedTemplate.companyCode().equals(result.companyCode())) {
            throw new IllegalStateException("companyCode does not match the catalog mapping.");
        }

        return new TemplateRecognitionResult(
                result.presetTemplateCode(),
                result.standardTemplateCode(),
                result.sceneCode(),
                result.companyCode(),
                result.confidence(),
                Boolean.TRUE.equals(result.needUserConfirm()),
                result.reason()
        );
    }

    /**
     * 判断字符串是否为空白。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 将模板识别上下文序列化为 JSON。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize template recognition payload.", ex);
        }
    }
}
