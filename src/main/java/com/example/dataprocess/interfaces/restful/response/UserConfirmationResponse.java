package com.example.dataprocess.interfaces.restful.response;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.WorkflowStage;

import java.util.List;
import java.util.Map;

/**
 * 用户确认提交后的返回结果。
 *
 * @param stage 当前工作流阶段
 * @param finalDsl 最终生成的 DSL
 * @param transformedPreviewRows DSL 试运行后的预览数据
 */
public record UserConfirmationResponse(
        WorkflowStage stage,
        FinalDsl finalDsl,
        List<Map<String, String>> transformedPreviewRows
) {
}
