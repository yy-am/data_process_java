package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户对手工输入确认项的提交结果。
 *
 * @param fieldCode 字段编码
 * @param inputValue 用户填写的值
 */
public record InputConfirmationDto(
        @NotBlank String fieldCode,
        @NotBlank String inputValue
) {
}
