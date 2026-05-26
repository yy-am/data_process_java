package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 目标列选值确认项。
 *
 * @param targetColumn 由该确认项填充的目标列名
 * @param question 展示给用户的问题文案
 * @param options 允许选择的选项列表
 * @param selectedValue 用户确认后的选中值；待确认阶段为空
 */
public record OptionConfirmation(
        String targetColumn,
        String question,
        List<OptionItem> options,
        String selectedValue
) {
}
