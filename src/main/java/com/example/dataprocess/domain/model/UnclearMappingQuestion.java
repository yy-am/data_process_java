package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 映射不明确时返回给用户的确认问题。
 */
public record UnclearMappingQuestion(
        String targetFieldCode,
        String targetFieldName,
        String question,
        List<SourceFieldCandidate> candidates
) {
}
