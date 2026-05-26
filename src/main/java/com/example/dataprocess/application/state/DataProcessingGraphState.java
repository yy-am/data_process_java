package com.example.dataprocess.application.state;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.example.dataprocess.domain.model.VagueBindingRecoResult;
import com.example.dataprocess.domain.model.WorkflowStage;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;

import java.util.List;
import java.util.Map;

/**
 * StateGraph 运行时统一状态对象。
 *
 * @param taskId 当前任务 ID
 * @param inputType 输入来源类型
 * @param sourceHeaders Excel 解析出的源表头
 * @param sampleRows 用于模板识别和规则生成的样例行
 * @param inputSnapshot 标准化后的输入快照
 * @param templateRecognitionResult 模板识别结果
 * @param processingRule 本次任务确定的完整加工规则
 * @param vagueBindingRecoResult 完整字段绑定识别结果
 * @param userConfirmationItems 需要前端展示的结构化确认项
 * @param userConfirmationRequest 前端提交的原始确认请求
 * @param userConfirmationResult 后端校验通过后的确认结果
 * @param finalDsl 最终生成的 DSL
 * @param workflowStage 当前工作流阶段
 * @param currentNode 当前节点名称
 * @param retryCount 显式记录的重试次数
 * @param errorMessages 累积错误信息
 * @param traceLogs 累积执行轨迹
 */
public record DataProcessingGraphState(
        String taskId,
        String inputType,
        List<String> sourceHeaders,
        List<Map<String, String>> sampleRows,
        InputSnapshot inputSnapshot,
        TemplateRecognitionResult templateRecognitionResult,
        ProcessingRule processingRule,
        VagueBindingRecoResult vagueBindingRecoResult,
        UserConfirmationItems userConfirmationItems,
        UserConfirmationRequest userConfirmationRequest,
        UserConfirmationResult userConfirmationResult,
        FinalDsl finalDsl,
        WorkflowStage workflowStage,
        String currentNode,
        int retryCount,
        List<String> errorMessages,
        List<String> traceLogs
) {

    /**
     * 将运行时图状态回写为任务会话。
     */
    public TaskSession toTaskSession() {
        TaskSession session = TaskSession.newSession(taskId, inputType, sourceHeaders, sampleRows);
        if (templateRecognitionResult != null) {
            session = session.withTemplateRecognitionResult(templateRecognitionResult);
        }
        if (processingRule != null) {
            session = session.withProcessingRule(processingRule);
        }
        if (vagueBindingRecoResult != null) {
            session = session.withVagueBindingRecoResult(vagueBindingRecoResult);
        }
        if (userConfirmationItems != null) {
            session = session.withUserConfirmationItems(userConfirmationItems);
        }
        if (userConfirmationResult != null) {
            session = session.withUserConfirmationResult(userConfirmationResult);
        }
        if (finalDsl != null) {
            session = session.withFinalDsl(finalDsl);
        }
        return session;
    }
}
