package com.example.dataprocess.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 单个目标列的加工计划。
 *
 * @param targetColumn 目标列名
 * @param operation 白名单加工操作
 * @param sourceHeaders 源字段类操作使用的上传表头
 * @param constantValue CONSTANT 操作使用的常量值
 * @param expression CASE_WHEN 操作使用的结构化表达式
 */
public record ProcessingPlanColumn(
        String targetColumn,
        ProcessingPlanOperation operation,
        List<String> sourceHeaders,
        String constantValue,
        Map<String, Object> expression
) {
}
