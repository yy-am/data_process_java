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
 * 模板识别服务。
 *
 * <p>一期保持为单步 AI 服务。它只做一次模板识别调用，
 * 不引入 skill，不做多轮对话，也不对模型结果做隐式补值。</p>
 *
 * <p>当前运行时提示词来自资源目录：
 * {@code src/main/resources/prompts/template-recognition-prompt.md}。</p>
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
        this.userPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "User Prompt 模板");
    }

    /**
     * 执行一次模板识别。
     */
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

    /**
     * 构造模板识别用户提示词。
     */
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

    /**
     * 对模型结果做最小归一化和显式校验。
     *
     * <p>这里只统一空集合和空布尔值，并校验预置用户模板与标准模板关系是否来自当前目录。
     * 不回填字段，不追加任何隐藏业务规则。</p>
     */
    private TemplateRecognitionResult normalizeAndValidateResult(
            TemplateRecognitionResult result,
            List<PresetUserTemplateDefinition> presetTemplates
    ) {
        if (result == null) {
            throw new IllegalStateException("模板识别服务未返回结果。");
        }
        if (result.presetTemplateCode() == null || result.presetTemplateCode().isBlank()) {
            throw new IllegalStateException("模板识别结果缺少 presetTemplateCode。");
        }
        if (result.standardTemplateCode() == null || result.standardTemplateCode().isBlank()) {
            throw new IllegalStateException("模板识别结果缺少 standardTemplateCode。");
        }

        Map<String, PresetUserTemplateDefinition> presetTemplateMap = presetTemplates.stream()
                .collect(Collectors.toMap(
                        PresetUserTemplateDefinition::presetTemplateCode,
                        template -> template
                ));
        PresetUserTemplateDefinition matchedTemplate = presetTemplateMap.get(result.presetTemplateCode());
        if (matchedTemplate == null) {
            throw new IllegalStateException("模板识别结果返回了目录外的 presetTemplateCode: " + result.presetTemplateCode());
        }
        if (!matchedTemplate.standardTemplateCode().equals(result.standardTemplateCode())) {
            throw new IllegalStateException("模板识别结果中的 standardTemplateCode 与目录维护关系不一致。");
        }
        if (result.sceneCode() == null || !matchedTemplate.sceneCode().equals(result.sceneCode())) {
            throw new IllegalStateException("模板识别结果中的 sceneCode 与目录维护关系不一致。");
        }
        if (result.countryCode() == null || !matchedTemplate.countryCode().equals(result.countryCode())) {
            throw new IllegalStateException("模板识别结果中的 countryCode 与目录维护关系不一致。");
        }

        return new TemplateRecognitionResult(
                result.presetTemplateCode(),
                result.standardTemplateCode(),
                result.sceneCode(),
                result.countryCode(),
                result.confidence(),
                Boolean.TRUE.equals(result.needUserConfirm()),
                result.reason(),
                result.unresolvedTargetFields() == null ? List.of() : List.copyOf(result.unresolvedTargetFields())
        );
    }

    /**
     * 序列化模型上下文。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化模板识别上下文失败。", ex);
        }
    }
}
