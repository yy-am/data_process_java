package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.ProcessingRuleDocument;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加工规则文档服务。
 *
 * <p>当前约定每个预置用户模板对应一个独立规则文件，文件名固定为
 * {@code rules/{presetTemplateCode}.md}。这里负责读取并解析该文档，供用户确认和 DSL 生成共用。</p>
 */
@Service
public class ProcessingRuleService {

    private static final String RULES_RESOURCE_PREFIX = "rules/";
    private static final String RULES_RESOURCE_SUFFIX = ".md";

    private final MarkdownResourceService markdownResourceService;

    public ProcessingRuleService(MarkdownResourceService markdownResourceService) {
        this.markdownResourceService = markdownResourceService;
    }

    /**
     * 按预置用户模板编码读取规则文档。
     */
    public ProcessingRuleDocument loadRuleDocument(String presetTemplateCode) {
        String resourcePath = RULES_RESOURCE_PREFIX + presetTemplateCode + RULES_RESOURCE_SUFFIX;
        String markdown = markdownResourceService.readUtf8Resource(resourcePath);

        String parsedPresetTemplateCode = null;
        Map<String, String> documentAttributes = new LinkedHashMap<>();
        List<ProcessingRuleItem> ruleItems = new ArrayList<>();
        String currentTargetColumn = null;
        Map<String, String> currentRuleAttributes = new LinkedHashMap<>();

        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("# ")) {
                continue;
            }
            if (line.startsWith("## presetTemplateCode: ")) {
                parsedPresetTemplateCode = line.substring("## presetTemplateCode: ".length()).trim();
                continue;
            }
            if (line.startsWith("### targetColumn: ")) {
                flushRuleItem(currentTargetColumn, currentRuleAttributes, ruleItems);
                currentTargetColumn = line.substring("### targetColumn: ".length()).trim();
                currentRuleAttributes = new LinkedHashMap<>();
                continue;
            }
            if (line.startsWith("- ")) {
                int separatorIndex = line.indexOf(':');
                if (separatorIndex < 0) {
                    throw new IllegalStateException("加工规则存在无法解析的属性行: " + line);
                }
                String key = line.substring(2, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                if (currentTargetColumn == null) {
                    documentAttributes.put(key, value);
                } else {
                    currentRuleAttributes.put(key, value);
                }
            }
        }

        flushRuleItem(currentTargetColumn, currentRuleAttributes, ruleItems);

        if (parsedPresetTemplateCode == null || parsedPresetTemplateCode.isBlank()) {
            throw new IllegalStateException("加工规则文档缺少 presetTemplateCode: " + resourcePath);
        }
        if (!parsedPresetTemplateCode.equals(presetTemplateCode)) {
            throw new IllegalStateException("加工规则文件名与文档中的 presetTemplateCode 不一致: " + presetTemplateCode);
        }

        return new ProcessingRuleDocument(
                parsedPresetTemplateCode,
                requireAttribute(documentAttributes, "presetTemplateName", presetTemplateCode),
                requireAttribute(documentAttributes, "standardTemplateCode", presetTemplateCode),
                documentAttributes.getOrDefault("说明", ""),
                List.copyOf(ruleItems)
        );
    }

    /**
     * 回写当前解析中的目标列规则。
     */
    private void flushRuleItem(
            String targetColumn,
            Map<String, String> attributes,
            List<ProcessingRuleItem> ruleItems
    ) {
        if (targetColumn == null || targetColumn.isBlank()) {
            return;
        }
        ruleItems.add(new ProcessingRuleItem(
                targetColumn,
                requireAttribute(attributes, "ruleType", targetColumn),
                splitCsv(attributes.get("sourceColumns")),
                attributes.getOrDefault("说明", ""),
                attributes.getOrDefault("ruleGuide", ""),
                attributes.getOrDefault("example", ""),
                attributes.getOrDefault("userInputField", ""),
                splitCsv(attributes.get("options")),
                attributes.getOrDefault("inputHint", "")
        ));
    }

    /**
     * 读取必填属性，缺失时显式报错。
     */
    private String requireAttribute(Map<String, String> attributes, String key, String code) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("规则文档缺少必填字段 " + key + "，目标编码: " + code);
        }
        return value;
    }

    /**
     * 解析逗号分隔字段列表。
     */
    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
