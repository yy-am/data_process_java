package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.TemplateBundle;
import com.example.dataprocess.domain.model.PresetUserTemplateDefinition;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.StandardTemplateDefinition;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.infrastructure.service.ProcessingRuleLoader;
import com.example.dataprocess.infrastructure.service.TemplateCatalogService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool facade for template catalog and processing rules.
 */
@Component
public class TemplateRuleTool {

    private final TemplateCatalogService templateCatalogService;
    private final ProcessingRuleLoader processingRuleLoader;

    public TemplateRuleTool(
            TemplateCatalogService templateCatalogService,
            ProcessingRuleLoader processingRuleLoader
    ) {
        this.templateCatalogService = templateCatalogService;
        this.processingRuleLoader = processingRuleLoader;
    }

    public String loadTemplateCatalog() {
        return templateCatalogService.readTemplateCatalogMarkdown();
    }

    public TemplateRecognitionResult validateTemplateRecognition(TemplateRecognitionResult result) {
        if (result == null) {
            throw new IllegalArgumentException("模板识别结果不能为空。");
        }
        if (isBlank(result.presetTemplateCode())) {
            throw new IllegalArgumentException("模板识别结果缺少 presetTemplateCode。");
        }
        if (isBlank(result.standardTemplateCode())) {
            throw new IllegalArgumentException("模板识别结果缺少 standardTemplateCode。");
        }

        List<PresetUserTemplateDefinition> presetTemplates = templateCatalogService.readPresetTemplateCatalog();
        Map<String, PresetUserTemplateDefinition> presetTemplateMap = presetTemplates.stream()
                .collect(Collectors.toMap(PresetUserTemplateDefinition::presetTemplateCode, item -> item));
        PresetUserTemplateDefinition presetTemplate = presetTemplateMap.get(result.presetTemplateCode());
        if (presetTemplate == null) {
            throw new IllegalArgumentException("预置模板不存在: " + result.presetTemplateCode());
        }
        if (!presetTemplate.standardTemplateCode().equals(result.standardTemplateCode())) {
            throw new IllegalArgumentException("模板识别结果中的 standardTemplateCode 与目录关系不一致。");
        }
        if (!presetTemplate.sceneCode().equals(result.sceneCode())) {
            throw new IllegalArgumentException("模板识别结果中的 sceneCode 与目录关系不一致。");
        }
        if (!presetTemplate.countryCode().equals(result.countryCode())) {
            throw new IllegalArgumentException("模板识别结果中的 countryCode 与目录关系不一致。");
        }

        return new TemplateRecognitionResult(
                result.presetTemplateCode(),
                result.standardTemplateCode(),
                result.sceneCode(),
                result.countryCode(),
                result.confidence(),
                Boolean.FALSE,
                result.reason()
        );
    }

    public TemplateBundle loadTemplateBundle(String presetTemplateCode) {
        PresetUserTemplateDefinition presetTemplate = templateCatalogService.getRequiredPresetTemplate(presetTemplateCode);
        StandardTemplateDefinition standardTemplate = templateCatalogService.getRequiredStandardTemplate(
                presetTemplate.standardTemplateCode()
        );
        ProcessingRule processingRule = processingRuleLoader.load(presetTemplateCode);
        return new TemplateBundle(presetTemplate, standardTemplate, processingRule);
    }

    public Map<String, Object> describeTemplateBundle(TemplateBundle bundle) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("presetTemplate", bundle.presetTemplate());
        value.put("standardTemplate", bundle.standardTemplate());
        value.put("processingRule", bundle.processingRule());
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
