package com.example.dataprocess.domain.model;

/**
 * 第一版可执行加工 DSL 的操作白名单。
 */
public enum ProcessingPlanOperation {
    /**
     * 直接字段映射，表达式必须等于一个弹性域字段名。
     */
    DIRECT_MAPPING,

    /**
     * 明确 CASE WHEN 规则，表达式必须是 CASE WHEN 片段。
     */
    CASE_WHEN,

    /**
     * 常量或用户确认值，表达式必须是 SQL 字面量。
     */
    CONSTANT
}
