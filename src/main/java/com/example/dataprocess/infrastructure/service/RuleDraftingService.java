package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.PresetUserTemplateDefinition;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.StandardTemplateDefinition;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则 DSL 生成服务。
 *
 * <p>一期保持为单步 AI 服务。它只根据当前快照、模板识别结果、
 * 用户确认结果和显式规则上下文生成 DSL，不引入 agent，也不偷偷修补模型输出。</p>
 *
 * <p>当前运行时提示词来自资源目录：
 * {@code src/main/resources/prompts/rule-drafting-prompt.md}。</p>
 */
@Service
public class RuleDraftingService {

    private static final String PROMPT_RESOURCE_PATH = "prompts/rule-drafting-prompt.md";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final TemplateCatalogService templateCatalogService;
    private final ProcessingRuleLoader processingRuleLoader;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;

    public RuleDraftingService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            TemplateCatalogService templateCatalogService,
            ProcessingRuleLoader processingRuleLoader,
            PromptTemplateService promptTemplateService
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.templateCatalogService = templateCatalogService;
        this.processingRuleLoader = processingRuleLoader;
        this.systemPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "System Prompt");
        this.userPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "User Prompt 模板");
    }

    /**
     * 根据模板识别结果和用户确认结果生成 DSL。
     */
    public FinalDsl draft(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            UserConfirmationResult userConfirmationResult
    ) {
        if (templateRecognitionResult == null) {
            throw new IllegalStateException("生成 DSL 前必须先完成模板识别。");
        }
        PresetUserTemplateDefinition presetTemplate = templateCatalogService.getRequiredPresetTemplate(
                templateRecognitionResult.presetTemplateCode()
        );
        StandardTemplateDefinition standardTemplate = templateCatalogService.getRequiredStandardTemplate(
                templateRecognitionResult.standardTemplateCode()
        );
        ProcessingRule processingRule = processingRuleLoader.load(
                templateRecognitionResult.presetTemplateCode()
        );

        FinalDsl finalDsl = ChatClient.create(chatModel)
                .prompt()
                .system(systemPromptTemplate)
                .user(buildUserPrompt(
                        inputSnapshot,
                        templateRecognitionResult,
                        userConfirmationResult,
                        presetTemplate,
                        standardTemplate,
                        processingRule
                ))
                .call()
                .entity(FinalDsl.class);

        validateDraftResult(finalDsl, templateRecognitionResult.presetTemplateCode());
        return finalDsl;
    }

    /**
     * 构造 DSL 生成用户提示词。
     */
    private String buildUserPrompt(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            UserConfirmationResult userConfirmationResult,
            PresetUserTemplateDefinition presetTemplate,
            StandardTemplateDefinition standardTemplate,
            ProcessingRule processingRule
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSnapshot", inputSnapshot);
        payload.put("templateRecognitionResult", templateRecognitionResult);
        payload.put("userConfirmationResult", userConfirmationResult);
        payload.put("presetTemplate", presetTemplate);
        payload.put("standardTemplate", standardTemplate);
        payload.put("processingRule", processingRule);

        return userPromptTemplate.replace("{payload-json}", writeJson(payload));
    }

    /**
     * 对 DSL 生成结果做最小显式校验。
     */
    private void validateDraftResult(FinalDsl finalDsl, String expectedPresetTemplateCode) {
        if (finalDsl == null) {
            throw new IllegalStateException("DSL 生成服务未返回结果。");
        }
        if (finalDsl.presetTemplateCode() == null || !finalDsl.presetTemplateCode().equals(expectedPresetTemplateCode)) {
            throw new IllegalStateException("DSL 结果中的 presetTemplateCode 与当前模板不一致。");
        }
        if (finalDsl.dslContent() == null || finalDsl.dslContent().isBlank()) {
            throw new IllegalStateException("DSL 结果缺少 dslContent。");
        }
    }

    /**
     * 序列化模型上下文。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化 DSL 生成上下文失败。", ex);
        }
    }
}
