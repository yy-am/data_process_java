package com.example.dataprocess.interfaces.restful.response;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.WorkflowStage;

/**
 * 用户确认提交后的返回结果。
 *
 * @param stage 当前工作流阶段
 * @param finalDsl 最终生成的 DSL
 */
public record UserConfirmationResponse(
        WorkflowStage stage,
        FinalDsl finalDsl
) {
}
