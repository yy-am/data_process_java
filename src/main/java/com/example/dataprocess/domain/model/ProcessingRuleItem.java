package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 单个目标列的加工规则定义。
 *
 * @param targetColumn 目标列名
 * @param ruleType 规则类型
 * @param sourceColumns 源列名列表
 * @param description 规则说明
 * @param ruleGuide 规则指导语
 * @param example 规则示例
 * @param userInputField 用户确认字段名
 * @param options 用户可选项列表
 * @param inputHint 用户输入提示
 */
public record ProcessingRuleItem(
        String targetColumn,
        String ruleType,
        List<String> sourceColumns,
        String description,
        String ruleGuide,
        String example,
        String userInputField,
        List<String> options,
        String inputHint
) {
}
