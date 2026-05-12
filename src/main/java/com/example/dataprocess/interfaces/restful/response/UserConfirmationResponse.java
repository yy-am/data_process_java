package com.example.dataprocess.interfaces.restful.response;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.WorkflowStage;

import java.util.List;
import java.util.Map;

/**
 * 用户确认提交后的响应结果。
 */
public record UserConfirmationResponse(
        WorkflowStage stage,
        FinalDsl finalDsl,
        List<Map<String, String>> transformedPreviewRows
) {
}
