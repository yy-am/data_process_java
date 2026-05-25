package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户对枚举确认项的提交结果。
 *
 * @param fieldCode 字段编码
 * @param selectedValue 用户选择的值
 */
public record OptionConfirmationDto(
        @NotBlank String fieldCode,
        @NotBlank String selectedValue
) {
}
