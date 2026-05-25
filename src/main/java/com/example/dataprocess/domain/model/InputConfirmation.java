package com.example.dataprocess.domain.model;

/**
 * 手工输入确认项。
 *
 * <p>这个类同时表示待确认题目和用户确认后的结果。
 * 待确认阶段 {@code inputValue} 为空，确认完成后带上用户输入的值。</p>
 *
 * @param fieldCode 字段编码
 * @param fieldName 字段名称
 * @param question 展示给用户的问题文案
 * @param hint 输入提示
 * @param inputValue 用户最终输入的值；待确认阶段可为空
 */
public record InputConfirmation(
        String fieldCode,
        String fieldName,
        String question,
        String hint,
        String inputValue
) {
}
