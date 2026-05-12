package com.example.dataprocess.domain.model;

/**
 * 源表头与规范字段之间的别名映射。
 *
 * @param sourceHeader 原始表头名称或别名
 * @param canonicalFieldCode 归一化后的标准字段编码
 * @param language 别名适用的语言或区域
 */
public record HeaderAlias(
        String sourceHeader,
        String canonicalFieldCode,
        String language
) {
}
