package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 手工输入字段确认请求项。
 */
public record InputFieldDecisionDto(
        @NotBlank String fieldCode,
        @NotBlank String inputValue
) {
}
