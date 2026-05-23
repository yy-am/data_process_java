package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.HeaderAlias;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TemplateCatalogItem;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模板识别服务。
 *
 * <p>一期保持为单步 AI 服务。它只做一次模板识别调用，
 * 不引入 skill，不做多轮对话，也不对模型结果做隐式补值。</p>
 */
@Service
public class TemplateRecognitionService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final TemplateCatalogService templateCatalogService;
    private final HeaderAliasService headerAliasService;

    public TemplateRecognitionService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            TemplateCatalogService templateCatalogService,
            HeaderAliasService headerAliasService
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.templateCatalogService = templateCatalogService;
        this.headerAliasService = headerAliasService;
    }

    /**
     * 执行一次模板识别。
     */
    public TemplateRecognitionResult recognize(InputSnapshot inputSnapshot) {
        List<TemplateCatalogItem> templateCatalog = templateCatalogService.readTemplateCatalog(inputSnapshot.inputType());
        List<HeaderAlias> headerAliases = headerAliasService.lookupHeaderAliases(inputSnapshot.normalizedHeaders());

        TemplateRecognitionResult result = ChatClient.create(chatModel)
                .prompt()
                .system(buildSystemPrompt())
                .user(buildUserPrompt(inputSnapshot, templateCatalog, headerAliases))
                .call()
                .entity(TemplateRecognitionResult.class);

        return normalizeAndValidateResult(result, templateCatalog);
    }

    /**
     * 构造模板识别系统提示词。
     */
    private String buildSystemPrompt() {
        return """
                你是数据加工流程中的模板识别服务。
                你的职责只有三件事：
                1. 从给定模板目录中选择最匹配的模板；
                2. 判断当前结果是否需要用户确认；
                3. 返回结构化 JSON。

                必须遵守以下约束：
                - 只能从给定模板目录中选择 templateCode。
                - 不能编造目录外模板。
                - 只输出 TemplateRecognitionResult 对应的 JSON 字段。
                - unresolvedTargetFields 必须明确列出仍需用户确认的目标字段。
                - 如果存在不确定字段，needUserConfirm 必须为 true。
                """;
    }

    /**
     * 构造模板识别用户提示词。
     */
    private String buildUserPrompt(
            InputSnapshot inputSnapshot,
            List<TemplateCatalogItem> templateCatalog,
            List<HeaderAlias> headerAliases
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSnapshot", inputSnapshot);
        payload.put("templateCatalog", templateCatalog);
        payload.put("headerAliases", headerAliases);

        return """
                请基于下面的上下文做模板识别，返回纯 JSON：
                %s
                """.formatted(writeJson(payload));
    }

    /**
     * 对模型结果做最小归一化和显式校验。
     *
     * <p>这里只统一空集合和空布尔值，并校验 templateCode 是否来自当前模板目录。
     * 不回填 sceneCode，不追加任何隐藏业务规则。</p>
     */
    private TemplateRecognitionResult normalizeAndValidateResult(
            TemplateRecognitionResult result,
            List<TemplateCatalogItem> templateCatalog
    ) {
        if (result == null) {
            throw new IllegalStateException("模板识别服务未返回结果。");
        }
        if (result.templateCode() == null || result.templateCode().isBlank()) {
            throw new IllegalStateException("模板识别结果缺少 templateCode。");
        }

        Set<String> allowedTemplateCodes = templateCatalog.stream()
                .map(TemplateCatalogItem::templateCode)
                .collect(Collectors.toSet());
        if (!allowedTemplateCodes.contains(result.templateCode())) {
            throw new IllegalStateException("模板识别结果返回了目录外的 templateCode: " + result.templateCode());
        }

        return new TemplateRecognitionResult(
                result.templateCode(),
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
