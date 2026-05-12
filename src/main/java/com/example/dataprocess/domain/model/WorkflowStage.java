package com.example.dataprocess.domain.model;

/**
 * 工作流阶段枚举。
 */
public enum WorkflowStage {
    RECEIVED,
    INPUT_SNAPSHOT_BUILT,
    TEMPLATE_RECOGNIZED,
    USER_CONFIRMATION_REQUIRED,
    USER_CONFIRMED,
    DSL_DRAFTED,
    DSL_VALIDATED,
    TRANSFORMED,
    COMPLETED
}
