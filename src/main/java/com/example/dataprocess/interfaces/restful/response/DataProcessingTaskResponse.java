package com.example.dataprocess.interfaces.restful.response;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.WorkflowStage;

/**
 * 任务启动后的返回结果。
 *
 * @param stage 当前工作流阶段
 * @param templateRecognitionResult 模板识别结果
 * @param userConfirmationItems 需要前端继续展示的确认项
 * @param finalDsl 如果当前流程已经走到 DSL 生成完成，则返回最终 DSL
 */
public record DataProcessingTaskResponse(
        WorkflowStage stage,
        TemplateRecognitionResult templateRecognitionResult,
        UserConfirmationItems userConfirmationItems,
        FinalDsl finalDsl
) {
}
