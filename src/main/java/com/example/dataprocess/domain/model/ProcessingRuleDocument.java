package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 预置用户模板对应的加工规则文档。
 *
 * @param presetTemplateCode 预置用户模板编码
 * @param presetTemplateName 预置用户模板名称
 * @param standardTemplateCode 对应标准模板编码
 * @param description 规则文档整体说明
 * @param ruleItems 目标列规则列表
 */
public record ProcessingRuleDocument(
        String presetTemplateCode,
        String presetTemplateName,
        String standardTemplateCode,
        String description,
        List<ProcessingRuleItem> ruleItems
) {
}
