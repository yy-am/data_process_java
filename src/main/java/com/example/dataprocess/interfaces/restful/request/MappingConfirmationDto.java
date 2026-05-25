package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 用户对字段映射确认项的提交结果。
 *
 * @param targetFieldCode 目标字段编码
 * @param selectedSourceFields 用户选中的源字段列表
 */
public record MappingConfirmationDto(
        @NotBlank String targetFieldCode,
        @NotEmpty List<String> selectedSourceFields
) {
}
