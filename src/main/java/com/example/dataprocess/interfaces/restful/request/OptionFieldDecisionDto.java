package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户对枚举型字段的确认结果。
 *
 * @param fieldCode 待确认字段编码
 * @param selectedValue 用户选择的枚举值
 */
public record OptionFieldDecisionDto(
        @NotBlank String fieldCode,
        @NotBlank String selectedValue
) {
}
