package com.example.dataprocess.application.state;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.WorkflowStage;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * StateGraph 运行时统一状态对象。
 *
 * @param taskId 任务唯一标识
 * @param inputType 输入来源类型
 * @param sourceHeaders 原始源表头
 * @param sampleRows 样例数据行
 * @param inputSnapshot 标准化输入快照
 * @param templateRecognitionResult 模板识别结果
 * @param userConfirmationItems 用户确认题包
 * @param userConfirmationRequest 用户提交的确认结果
 * @param finalDsl 最终 DSL 结果
 * @param transformedPreviewRows 转换预览结果
 * @param workflowStage 当前工作流阶段
 * @param currentNode 当前执行节点名
 * @param retryCount 重试次数
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
        UserConfirmationItems userConfirmationItems,
        UserConfirmationRequest userConfirmationRequest,
        FinalDsl finalDsl,
        List<Map<String, String>> transformedPreviewRows,
        WorkflowStage workflowStage,
        String currentNode,
        int retryCount,
        List<String> errorMessages,
        List<String> traceLogs
) {

    /**
     * 基于任务会话构造初始图状态。
     */
    public static DataProcessingGraphState from(TaskSession session) {
        return new DataProcessingGraphState(
                session.taskId(),
                session.inputType(),
                session.sourceHeaders(),
                session.sampleRows(),
                null,
                session.templateRecognitionResult(),
                session.userConfirmationItems(),
                null,
                session.finalDsl(),
                List.of(),
                WorkflowStage.RECEIVED,
                "START",
                0,
                List.of(),
                List.of()
        );
    }

    public DataProcessingGraphState withInputSnapshot(InputSnapshot newInputSnapshot) {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                newInputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                userConfirmationRequest,
                finalDsl,
                transformedPreviewRows,
                WorkflowStage.INPUT_SNAPSHOT_BUILT,
                "build_input_snapshot",
                retryCount,
                errorMessages,
                appendTrace("Built input snapshot.")
        );
    }

    public DataProcessingGraphState withTemplateRecognitionResult(TemplateRecognitionResult result) {
        WorkflowStage nextStage = Boolean.TRUE.equals(result.needUserConfirm())
                ? WorkflowStage.USER_CONFIRMATION_REQUIRED
                : WorkflowStage.TEMPLATE_RECOGNIZED;
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                result,
                userConfirmationItems,
                userConfirmationRequest,
                finalDsl,
                transformedPreviewRows,
                nextStage,
                "template_recognition",
                retryCount,
                errorMessages,
                appendTrace("Template recognition finished.")
        );
    }

    public DataProcessingGraphState withUserConfirmationItems(UserConfirmationItems items) {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                items,
                userConfirmationRequest,
                finalDsl,
                transformedPreviewRows,
                WorkflowStage.USER_CONFIRMATION_REQUIRED,
                "confirmation_question",
                retryCount,
                errorMessages,
                appendTrace("Generated user confirmation package.")
        );
    }

    public DataProcessingGraphState awaitingUserConfirmation() {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                userConfirmationRequest,
                finalDsl,
                transformedPreviewRows,
                WorkflowStage.USER_CONFIRMATION_REQUIRED,
                "wait_user_confirmation",
                retryCount,
                errorMessages,
                appendTrace("Waiting for user confirmation.")
        );
    }

    public DataProcessingGraphState withUserConfirmationRequest(UserConfirmationRequest request) {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                request,
                finalDsl,
                transformedPreviewRows,
                WorkflowStage.USER_CONFIRMED,
                "wait_user_confirmation",
                retryCount,
                errorMessages,
                appendTrace("Accepted user confirmation input.")
        );
    }

    public DataProcessingGraphState withFinalDsl(FinalDsl newFinalDsl) {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                userConfirmationRequest,
                newFinalDsl,
                transformedPreviewRows,
                WorkflowStage.DSL_DRAFTED,
                "rule_drafting",
                retryCount,
                errorMessages,
                appendTrace("Drafted final DSL.")
        );
    }

    public DataProcessingGraphState withDslValidated() {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                userConfirmationRequest,
                finalDsl,
                transformedPreviewRows,
                WorkflowStage.DSL_VALIDATED,
                "dsl_validation",
                retryCount,
                errorMessages,
                appendTrace("Validated DSL.")
        );
    }

    public DataProcessingGraphState withTransformedPreviewRows(List<Map<String, String>> previewRows) {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                userConfirmationRequest,
                finalDsl,
                previewRows.stream().map(Map::copyOf).toList(),
                WorkflowStage.TRANSFORMED,
                "dsl_transformation",
                retryCount,
                errorMessages,
                appendTrace("Built transformed preview rows.")
        );
    }

    public DataProcessingGraphState completed() {
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                userConfirmationRequest,
                finalDsl,
                transformedPreviewRows,
                WorkflowStage.COMPLETED,
                "complete",
                retryCount,
                errorMessages,
                appendTrace("Workflow completed.")
        );
    }

    public DataProcessingGraphState withError(String errorMessage) {
        List<String> updatedErrors = new ArrayList<>(errorMessages);
        updatedErrors.add(errorMessage);
        return new DataProcessingGraphState(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                inputSnapshot,
                templateRecognitionResult,
                userConfirmationItems,
                userConfirmationRequest,
                finalDsl,
                transformedPreviewRows,
                workflowStage,
                currentNode,
                retryCount + 1,
                List.copyOf(updatedErrors),
                appendTrace("Error: " + errorMessage)
        );
    }

    public TaskSession toTaskSession() {
        TaskSession session = TaskSession.newSession(taskId, inputType, sourceHeaders, sampleRows);
        if (templateRecognitionResult != null) {
            session = session.withTemplateRecognitionResult(templateRecognitionResult);
        }
        if (userConfirmationItems != null) {
            session = session.withUserConfirmationItems(userConfirmationItems);
        }
        if (finalDsl != null) {
            session = session.withFinalDsl(finalDsl);
        }
        return session;
    }

    private List<String> appendTrace(String message) {
        List<String> updated = new ArrayList<>(traceLogs);
        updated.add(message);
        return List.copyOf(updated);
    }
}
