package com.example.dataprocess.domain.model;

/**
 * 目标列手工输入确认项。
 *
 * @param targetColumn 由该确认项填充的目标列名
 * @param question 展示给用户的问题文案
 * @param hint 展示给用户的输入提示
 * @param inputValue 用户确认后的输入值；待确认阶段为空
 */
public record InputConfirmation(
        String targetColumn,
        String question,
        String hint,
        String inputValue
) {
}
