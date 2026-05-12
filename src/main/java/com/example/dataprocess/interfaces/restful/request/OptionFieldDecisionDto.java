package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 选项字段确认请求项。
 */
public record OptionFieldDecisionDto(
        @NotBlank String fieldCode,
        @NotBlank String selectedValue
) {
}
