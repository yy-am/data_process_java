package com.example.dataprocess.domain.model;

/**
 * 源字段候选项。
 *
 * @param fieldCode 源字段编码
 * @param displayName 源字段展示名称
 * @param confidence 当前候选与目标字段的匹配置信度
 */
public record SourceFieldCandidate(
        String fieldCode,
        String displayName,
        Double confidence
) {
}
