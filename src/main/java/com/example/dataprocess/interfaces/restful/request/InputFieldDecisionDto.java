package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户对手工输入字段的确认结果。
 *
 * @param fieldCode 待输入字段编码
 * @param inputValue 用户填写的值
 */
public record InputFieldDecisionDto(
        @NotBlank String fieldCode,
        @NotBlank String inputValue
) {
}
