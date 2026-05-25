package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.PresetUserTemplateDefinition;
import com.example.dataprocess.domain.model.StandardTemplateDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板目录服务。
 *
 * <p>当前运行时直接从 {@code resources/catalog/TEMPLATE_CATALOG.md} 读取模板目录。
 * 目录中同时维护两类事实：预置用户模板定义、标准模板定义。</p>
 */
@Service
public class TemplateCatalogService {

    private static final String TEMPLATE_CATALOG_RESOURCE_PATH = "catalog/TEMPLATE_CATALOG.md";
    private static final String STANDARD_SECTION_HEADING = "## 标准模板";
    private static final String PRESET_SECTION_HEADING = "## 预置用户模板";

    private final MarkdownResourceService markdownResourceService;

    public TemplateCatalogService(MarkdownResourceService markdownResourceService) {
        this.markdownResourceService = markdownResourceService;
    }

    /**
     * 读取全部预置用户模板定义。
     */
    public List<PresetUserTemplateDefinition> readPresetTemplateCatalog() {
        return parseCatalogDocument().presetTemplates();
    }

    /**
     * 按预置用户模板编码读取模板定义。
     */
    public PresetUserTemplateDefinition getRequiredPresetTemplate(String presetTemplateCode) {
        return readPresetTemplateCatalog().stream()
                .filter(item -> item.presetTemplateCode().equals(presetTemplateCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到预置用户模板定义: " + presetTemplateCode));
    }

    /**
     * 按标准模板编码读取标准模板定义。
     */
    public StandardTemplateDefinition getRequiredStandardTemplate(String standardTemplateCode) {
        return parseCatalogDocument().standardTemplates().stream()
                .filter(item -> item.standardTemplateCode().equals(standardTemplateCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到标准模板定义: " + standardTemplateCode));
    }

    /**
     * 解析模板目录文档。
     */
    private CatalogDocument parseCatalogDocument() {
        String markdown = markdownResourceService.readUtf8Resource(TEMPLATE_CATALOG_RESOURCE_PATH);
        List<StandardTemplateDefinition> standardTemplates = new ArrayList<>();
        List<PresetUserTemplateDefinition> presetTemplates = new ArrayList<>();

        String currentSection = "";
        String currentCode = null;
        Map<String, String> attributes = new LinkedHashMap<>();

        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("# ") || line.startsWith(">")) {
                continue;
            }
            if (STANDARD_SECTION_HEADING.equals(line)) {
                flushCatalogEntry(currentSection, currentCode, attributes, standardTemplates, presetTemplates);
                currentSection = STANDARD_SECTION_HEADING;
                currentCode = null;
                attributes = new LinkedHashMap<>();
                continue;
            }
            if (PRESET_SECTION_HEADING.equals(line)) {
                flushCatalogEntry(currentSection, currentCode, attributes, standardTemplates, presetTemplates);
                currentSection = PRESET_SECTION_HEADING;
                currentCode = null;
                attributes = new LinkedHashMap<>();
                continue;
            }
            if (line.startsWith("### standardTemplateCode: ")) {
                flushCatalogEntry(currentSection, currentCode, attributes, standardTemplates, presetTemplates);
                currentCode = line.substring("### standardTemplateCode: ".length()).trim();
                attributes = new LinkedHashMap<>();
                continue;
            }
            if (line.startsWith("### presetTemplateCode: ")) {
                flushCatalogEntry(currentSection, currentCode, attributes, standardTemplates, presetTemplates);
                currentCode = line.substring("### presetTemplateCode: ".length()).trim();
                attributes = new LinkedHashMap<>();
                continue;
            }
            if (line.startsWith("- ")) {
                int separatorIndex = line.indexOf(':');
                if (separatorIndex < 0) {
                    throw new IllegalStateException("模板目录存在无法解析的属性行: " + line);
                }
                String key = line.substring(2, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                attributes.put(key, value);
            }
        }

        flushCatalogEntry(currentSection, currentCode, attributes, standardTemplates, presetTemplates);
        return new CatalogDocument(List.copyOf(standardTemplates), List.copyOf(presetTemplates));
    }

    /**
     * 将当前解析中的目录项回写为结构化对象。
     */
    private void flushCatalogEntry(
            String section,
            String code,
            Map<String, String> attributes,
            List<StandardTemplateDefinition> standardTemplates,
            List<PresetUserTemplateDefinition> presetTemplates
    ) {
        if (code == null || code.isBlank()) {
            return;
        }
        if (STANDARD_SECTION_HEADING.equals(section)) {
            standardTemplates.add(new StandardTemplateDefinition(
                    code,
                    requireAttribute(attributes, "sceneCode", code),
                    requireAttribute(attributes, "countryCode", code),
                    splitCsv(requireAttribute(attributes, "standardColumns", code))
            ));
            return;
        }
        if (PRESET_SECTION_HEADING.equals(section)) {
            presetTemplates.add(new PresetUserTemplateDefinition(
                    code,
                    requireAttribute(attributes, "presetTemplateName", code),
                    requireAttribute(attributes, "sceneCode", code),
                    requireAttribute(attributes, "countryCode", code),
                    requireAttribute(attributes, "standardTemplateCode", code),
                    splitCsv(requireAttribute(attributes, "sourceColumns", code))
            ));
            return;
        }
        throw new IllegalStateException("模板目录项缺少所属分组，无法解析: " + code);
    }

    /**
     * 读取必填属性，缺失时显式报错。
     */
    private String requireAttribute(Map<String, String> attributes, String key, String code) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("模板目录项缺少必填字段 " + key + "，编码: " + code);
        }
        return value;
    }

    /**
     * 将逗号分隔的字段列表转换为字符串集合。
     */
    private List<String> splitCsv(String csv) {
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * 模板目录解析结果。
     */
    private record CatalogDocument(
            List<StandardTemplateDefinition> standardTemplates,
            List<PresetUserTemplateDefinition> presetTemplates
    ) {
    }
}
