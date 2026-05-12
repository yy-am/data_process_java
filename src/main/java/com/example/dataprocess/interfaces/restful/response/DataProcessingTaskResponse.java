package com.example.dataprocess.interfaces.restful.response;

import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.WorkflowStage;

/**
 * 任务启动后的返回结果。
 *
 * @param stage 当前工作流阶段
 * @param templateRecognitionResult 模板识别结果
 * @param userConfirmationItems 需要前端继续展示的确认项
 */
public record DataProcessingTaskResponse(
        WorkflowStage stage,
        TemplateRecognitionResult templateRecognitionResult,
        UserConfirmationItems userConfirmationItems
) {
}
