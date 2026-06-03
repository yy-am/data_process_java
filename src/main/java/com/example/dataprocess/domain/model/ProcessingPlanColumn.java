
package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 单个目标列的加工计划。
 *
 * @param targetColumn 目标列名
 * @param actualColumnMappings 该目标列使用的用户实际上传字段与弹性域字段映射列表
 * @param expressionSql AI 生成的 SQL 表达式片段，只能放在 SELECT 列表中使用
 */
public record ProcessingPlanColumn(
        String targetColumn,
        List<ActualColumnMapping> actualColumnMappings,
        String expressionSql
) {
}
