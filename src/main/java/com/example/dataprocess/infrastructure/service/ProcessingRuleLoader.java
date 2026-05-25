package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.ProcessingRuleDocument;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and parses rule documents for preset templates.
 */
@Component
public class ProcessingRuleLoader {

    private static final String RULES_RESOURCE_PREFIX = "rules/";
    private static final String RULES_RESOURCE_SUFFIX = ".md";

    private final MarkdownResourceService markdownResourceService;

    public ProcessingRuleLoader(MarkdownResourceService markdownResourceService) {
        this.markdownResourceService = markdownResourceService;
    }

    public ProcessingRuleDocument load(String presetTemplateCode) {
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
                    throw new IllegalStateException("Unable to parse rule line: " + line);
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
            throw new IllegalStateException("Rule document is missing presetTemplateCode: " + resourcePath);
        }
        if (!parsedPresetTemplateCode.equals(presetTemplateCode)) {
            throw new IllegalStateException("Rule document filename does not match presetTemplateCode: " + presetTemplateCode);
        }

        return new ProcessingRuleDocument(
                parsedPresetTemplateCode,
                requireAttribute(documentAttributes, "presetTemplateName", presetTemplateCode),
                requireAttribute(documentAttributes, "standardTemplateCode", presetTemplateCode),
                documentAttributes.getOrDefault("说明", ""),
                List.copyOf(ruleItems)
        );
    }

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

    private String requireAttribute(Map<String, String> attributes, String key, String code) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Rule document is missing required field " + key + " for " + code);
        }
        return value;
    }

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
