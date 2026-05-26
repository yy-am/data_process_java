package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 单个目标列的 DSL 生成上下文。
 *
 * @param targetColumn 目标列名
 * @param ruleType 规则类型，例如 DIRECT_MAPPING、CASE_WHEN 或 USER_CONFIRM
 * @param actualColumns 用户实际上传文件中参与该目标列生成的字段列表
 * @param ruleGuide 规则指导，主要用于 CASE_WHEN
 * @param example 规则示例，主要用于 CASE_WHEN
 * @param confirmedValue 用户确认值，主要用于 USER_CONFIRM
 */
public record TargetColumnGenerationContext(
        String targetColumn,
        String ruleType,
        List<String> actualColumns,
        String ruleGuide,
        String example,
        String confirmedValue
) {
}
