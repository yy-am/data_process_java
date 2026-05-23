package com.example.dataprocess.domain.model;

/**
 * 用户确认后的枚举字段结果。
 *
 * @param fieldCode 字段编码
 * @param selectedValue 用户选定的枚举值
 */
public record OptionFieldDecision(
        String fieldCode,
        String selectedValue
) {
}
