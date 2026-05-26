package com.example.dataprocess.domain.model;

/**
 * 用户确认准备结果。
 *
 * @param processingRule 本次任务确定的完整加工规则
 * @param vagueBindingRecoResult 完整字段绑定识别结果
 * @param userConfirmationItems 需要展示给用户的结构化确认项
 */
public record UserConfirmationPreparationResult(
        ProcessingRule processingRule,
        VagueBindingRecoResult vagueBindingRecoResult,
        UserConfirmationItems userConfirmationItems
) {
}
