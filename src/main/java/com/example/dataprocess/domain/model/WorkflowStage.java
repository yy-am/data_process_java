package com.example.dataprocess.domain.model;

/**
 * 一期数据加工流程阶段枚举。
 *
 * <p>当前只覆盖从接收请求到生成 DSL 结束的状态，
 * 不包含全量数据加工和导出阶段。</p>
 */
public enum WorkflowStage {
    RECEIVED,
    INPUT_SNAPSHOT_BUILT,
    TEMPLATE_RECOGNIZED,
    USER_CONFIRMATION_REQUIRED,
    USER_CONFIRMED,
    DSL_DRAFTED,
    COMPLETED
}
