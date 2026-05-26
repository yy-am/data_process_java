package com.example.dataprocess.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 数据加工任务会话。
 *
 * @param taskId 任务唯一标识
 * @param inputType 输入来源类型，例如 excel-import
 * @param sourceHeaders 原始或解析后的源表头
 * @param sampleRows 样例数据行
 * @param templateRecognitionResult 模板识别结果
 * @param processingRule 本次任务确定的完整加工规则
 * @param vagueBindingRecoResult 完整字段绑定识别结果
 * @param userConfirmationItems 需要前端展示的确认项
 * @param userConfirmationResult 用户提交并通过校验的确认结果
 * @param finalDsl 最终生成的 DSL
 */
public record TaskSession(
        String taskId,
        String inputType,
        List<String> sourceHeaders,
        List<Map<String, String>> sampleRows,
        TemplateRecognitionResult templateRecognitionResult,
        ProcessingRule processingRule,
        VagueBindingRecoResult vagueBindingRecoResult,
        UserConfirmationItems userConfirmationItems,
        UserConfirmationResult userConfirmationResult,
        FinalDsl finalDsl
) {

    /**
     * 创建新的任务会话，并冻结样例数据，避免后续被外部修改。
     */
    public static TaskSession newSession(
            String taskId,
            String inputType,
            List<String> sourceHeaders,
            List<Map<String, String>> sampleRows
    ) {
        return new TaskSession(
                taskId,
                inputType,
                List.copyOf(sourceHeaders),
                sampleRows.stream().map(Map::copyOf).toList(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 回写模板识别结果。
     */
    public TaskSession withTemplateRecognitionResult(TemplateRecognitionResult result) {
        return new TaskSession(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                result,
                processingRule,
                vagueBindingRecoResult,
                userConfirmationItems,
                userConfirmationResult,
                finalDsl
        );
    }

    /**
     * 回写本次任务确定的加工规则。
     */
    public TaskSession withProcessingRule(ProcessingRule rule) {
        return new TaskSession(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                templateRecognitionResult,
                rule,
                vagueBindingRecoResult,
                userConfirmationItems,
                userConfirmationResult,
                finalDsl
        );
    }

    /**
     * 回写完整字段绑定识别结果。
     */
    public TaskSession withVagueBindingRecoResult(VagueBindingRecoResult result) {
        return new TaskSession(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                templateRecognitionResult,
                processingRule,
                result,
                userConfirmationItems,
                userConfirmationResult,
                finalDsl
        );
    }

    /**
     * 回写用户确认项。
     */
    public TaskSession withUserConfirmationItems(UserConfirmationItems items) {
        return new TaskSession(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                templateRecognitionResult,
                processingRule,
                vagueBindingRecoResult,
                items,
                userConfirmationResult,
                finalDsl
        );
    }

    /**
     * 回写用户确认结果。
     */
    public TaskSession withUserConfirmationResult(UserConfirmationResult result) {
        return new TaskSession(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                templateRecognitionResult,
                processingRule,
                vagueBindingRecoResult,
                userConfirmationItems,
                result,
                finalDsl
        );
    }

    /**
     * 回写最终 DSL。
     */
    public TaskSession withFinalDsl(FinalDsl newFinalDsl) {
        return new TaskSession(
                taskId,
                inputType,
                sourceHeaders,
                sampleRows,
                templateRecognitionResult,
                processingRule,
                vagueBindingRecoResult,
                userConfirmationItems,
                userConfirmationResult,
                newFinalDsl
        );
    }
}
