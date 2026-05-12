package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 字段映射确认请求项。
 */
public record FieldMappingDecisionDto(
        @NotBlank String targetFieldCode,
        @NotEmpty List<String> selectedSourceFields
) {
}
