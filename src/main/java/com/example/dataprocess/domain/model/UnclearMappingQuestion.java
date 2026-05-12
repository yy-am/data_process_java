package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 字段映射不明确时返回给前端的确认问题。
 *
 * @param targetFieldCode 目标字段编码
 * @param targetFieldName 目标字段名称
 * @param question 提问文案
 * @param candidates 可供用户选择的源字段候选项
 */
public record UnclearMappingQuestion(
        String targetFieldCode,
        String targetFieldName,
        String question,
        List<SourceFieldCandidate> candidates
) {
}
