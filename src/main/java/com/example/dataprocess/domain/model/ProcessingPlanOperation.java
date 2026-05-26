package com.example.dataprocess.domain.model;

/**
 * 第一版可执行加工 DSL 的操作白名单。
 */
public enum ProcessingPlanOperation {
    DIRECT_MAPPING,
    CASE_WHEN,
    CONSTANT
}
