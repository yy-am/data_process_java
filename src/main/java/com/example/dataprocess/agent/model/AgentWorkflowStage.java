package com.example.dataprocess.agent.model;

/**
 * 数据加工 Agent 可恢复阶段。
 *
 * <p>阶段只表达外部可观察、可恢复的业务边界。工具内部的校验、组装和持久化细节不单独拆成阶段。</p>
 */
public enum AgentWorkflowStage {
    /**
     * 已收到任务，但尚未完成解析文件摘要加载。
     */
    RECEIVED,

    /**
     * 已加载解析后的 Excel 摘要，可以进入模板识别。
     */
    TASK_CONTEXT_READY,

    /**
     * 已完成模板识别结果保存，但模板上下文可能尚未完整加载。保留该阶段用于兼容旧流程。
     */
    TEMPLATE_RECOGNIZED,

    /**
     * 已完成模板识别、标准模板、加工规则、必填字段和值集元数据加载。
     */
    TEMPLATE_CONTEXT_READY,

    /**
     * 已接收并校验字段绑定计划。通常会马上继续分析确认项。
     */
    FIELD_BINDING_PLAN_READY,

    /**
     * 已完成用户确认项分析。保留该阶段用于表达确认项生成完成后的中间状态。
     */
    CONFIRMATION_ANALYZED,

    /**
     * 需要前端提交用户确认。
     */
    USER_CONFIRMATION_REQUIRED,

    /**
     * 用户确认已完成，或无需用户确认；可以进入确认后的 SQL 生成流程。
     */
    USER_CONFIRMED,

    /**
     * 确认后的加工上下文已校验并归一化，可以调用工具准备 SQL 生成上下文。
     */
    POST_CONFIRMATION_CONTEXT_READY,

    /**
     * 原始 Excel 数据已写入临时表，SQL 生成上下文已准备好。
     */
    SQL_GENERATION_CONTEXT_READY,

    /**
     * 已完成 ProcessingPlanDsl 校验和完整 INSERT SELECT SQL 拼接，尚未执行落表。
     */
    PROCESSING_SQL_RENDERED,

    /**
     * 结果表写入已执行。后续可直接进入完成态。
     */
    RESULT_TABLE_WRITTEN,

    /**
     * 任务失败。
     */
    FAILED,

    /**
     * 任务完整完成。
     */
    COMPLETED
}
