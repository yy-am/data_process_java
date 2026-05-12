package com.example.dataprocess.domain.model;

/**
 * 源字段候选项对象。
 */
public record SourceFieldCandidate(
        String fieldCode,
        String displayName,
        Double confidence
) {
}
