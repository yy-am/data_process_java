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
 */
public record DataProcessingGraphState(
        String taskId,
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

    /**
     * 写入输入快照并推进阶段。
     */
    public DataProcessingGraphState withInputSnapshot(InputSnapshot newInputSnapshot) {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 写入模板识别结果并推进阶段。
     */
    public DataProcessingGraphState withTemplateRecognitionResult(TemplateRecognitionResult result) {
        WorkflowStage nextStage = Boolean.TRUE.equals(result.needUserConfirm())
                ? WorkflowStage.USER_CONFIRMATION_REQUIRED
                : WorkflowStage.TEMPLATE_RECOGNIZED;
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 写入用户确认项。
     */
    public DataProcessingGraphState withUserConfirmationItems(UserConfirmationItems items) {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 标记流程等待用户确认。
     */
    public DataProcessingGraphState awaitingUserConfirmation() {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 写入用户确认请求内容。
     */
    public DataProcessingGraphState withUserConfirmationRequest(UserConfirmationRequest request) {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 写入最终 DSL 草案。
     */
    public DataProcessingGraphState withFinalDsl(FinalDsl newFinalDsl) {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 标记 DSL 校验通过。
     */
    public DataProcessingGraphState withDslValidated() {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 写入转换后的预览结果。
     */
    public DataProcessingGraphState withTransformedPreviewRows(List<Map<String, String>> previewRows) {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 标记整个流程完成。
     */
    public DataProcessingGraphState completed() {
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 记录错误并递增重试计数。
     */
    public DataProcessingGraphState withError(String errorMessage) {
        List<String> updatedErrors = new ArrayList<>(errorMessages);
        updatedErrors.add(errorMessage);
        return new DataProcessingGraphState(
                taskId,
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

    /**
     * 将图状态回写为领域层任务会话对象。
     */
    public TaskSession toTaskSession() {
        TaskSession session = TaskSession.newSession(taskId, sourceHeaders, sampleRows);
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

    /**
     * 追加一条执行轨迹日志。
     */
    private List<String> appendTrace(String message) {
        List<String> updated = new ArrayList<>(traceLogs);
        updated.add(message);
        return List.copyOf(updated);
    }
}
