package com.example.dataprocess.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 数据加工任务会话对象。
 */
public record TaskSession(
        String taskId,
        List<String> sourceHeaders,
        List<Map<String, String>> sampleRows,
        TemplateRecognitionResult templateRecognitionResult,
        UserConfirmationItems userConfirmationItems,
        FinalDsl finalDsl
) {

    /**
     * 创建新的任务会话。
     */
    public static TaskSession newSession(
            String taskId,
            List<String> sourceHeaders,
            List<Map<String, String>> sampleRows
    ) {
        return new TaskSession(
                taskId,
                List.copyOf(sourceHeaders),
                sampleRows.stream().map(Map::copyOf).toList(),
                null,
                null,
                null
        );
    }

    /**
     * 回写模板识别结果。
     */
    public TaskSession withTemplateRecognitionResult(TemplateRecognitionResult result) {
        return new TaskSession(taskId, sourceHeaders, sampleRows, result, userConfirmationItems, finalDsl);
    }

    /**
     * 回写用户确认项。
     */
    public TaskSession withUserConfirmationItems(UserConfirmationItems items) {
        return new TaskSession(taskId, sourceHeaders, sampleRows, templateRecognitionResult, items, finalDsl);
    }

    /**
     * 回写最终 DSL。
     */
    public TaskSession withFinalDsl(FinalDsl newFinalDsl) {
        return new TaskSession(taskId, sourceHeaders, sampleRows, templateRecognitionResult, userConfirmationItems, newFinalDsl);
    }
}
