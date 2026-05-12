package com.example.dataprocess.domain.model;

/**
 * 表头别名定义对象。
 */
public record HeaderAlias(
        String sourceHeader,
        String canonicalFieldCode,
        String language
) {
}
