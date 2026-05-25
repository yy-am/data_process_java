package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 枚举选择确认项。
 *
 * <p>这个类同时表示待确认题目和用户确认后的结果。
 * 待确认阶段 {@code selectedValue} 为空，确认完成后带上用户选择的值。</p>
 *
 * @param fieldCode 字段编码
 * @param fieldName 字段名称
 * @param question 展示给用户的问题文案
 * @param options 当前字段允许选择的选项列表
 * @param selectedValue 用户最终选择的值；待确认阶段可为空
 */
public record OptionConfirmation(
        String fieldCode,
        String fieldName,
        String question,
        List<OptionItem> options,
        String selectedValue
) {
}
