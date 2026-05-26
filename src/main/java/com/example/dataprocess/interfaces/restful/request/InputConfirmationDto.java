package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户提交的目标列手工输入确认结果。
 *
 * @param targetColumn 由该确认项填充的目标列名
 * @param inputValue 用户填写的值
 */
public record InputConfirmationDto(
        @NotBlank String targetColumn,
        @NotBlank String inputValue
) {
}
