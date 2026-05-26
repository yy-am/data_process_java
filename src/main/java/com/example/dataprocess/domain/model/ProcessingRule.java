package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 某个预置用户模板对应的完整加工规则。
 *
 * @param presetTemplateCode 预置用户模板编码
 * @param presetTemplateName 预置用户模板名称
 * @param standardTemplateCode 对应的标准模板编码
 * @param description 加工规则整体说明
 * @param ruleItems 目标列加工规则列表
 */
public record ProcessingRule(
        String presetTemplateCode,
        String presetTemplateName,
        String standardTemplateCode,
        String description,
        List<ProcessingRuleItem> ruleItems
) {
}
