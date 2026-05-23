package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 用户确认后的字段映射结果。
 *
 * @param targetFieldCode 目标字段编码
 * @param selectedSourceFields 用户选定的源字段列表
 */
public record FieldMappingDecision(
        String targetFieldCode,
        List<String> selectedSourceFields
) {
}
