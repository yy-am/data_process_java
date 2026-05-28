package com.example.dataprocess.domain.model;

/**
 * 用户实际上传字段与弹性域字段之间的映射关系。
 *
 * @param actualColumn 用户上传 Excel 中的实际字段名，用于帮助 AI 理解业务含义
 * @param elasticColumn 原始弹性域表中的真实字段名，用于生成可执行 SQL 表达式片段
 */
public record ActualColumnMapping(
        String actualColumn,
        String elasticColumn
) {
}
