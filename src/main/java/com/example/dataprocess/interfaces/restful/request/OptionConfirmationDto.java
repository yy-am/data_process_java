package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户提交的目标列选值确认结果。
 *
 * @param targetColumn 由该确认项填充的目标列名
 * @param selectedValue 用户选择的值
 */
public record OptionConfirmationDto(
        @NotBlank String targetColumn,
        @NotBlank String selectedValue
) {
}
