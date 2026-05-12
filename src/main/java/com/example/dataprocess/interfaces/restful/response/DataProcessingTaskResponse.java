package com.example.dataprocess.interfaces.restful.response;

import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.WorkflowStage;

/**
 * 数据加工任务提交响应。
 */
public record DataProcessingTaskResponse(
        WorkflowStage stage,
        TemplateRecognitionResult templateRecognitionResult,
        UserConfirmationItems userConfirmationItems
) {
}
