package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 用户提交并通过后端校验的一轮确认结果。
 *
 * @param taskId 当前任务 ID
 * @param templateCode 当前模板编码
 * @param mappingDecisions 字段映射确认结果
 * @param optionFieldDecisions 枚举字段确认结果
 * @param inputFieldDecisions 手工输入字段确认结果
 */
public record UserConfirmationResult(
        String taskId,
        String templateCode,
        List<FieldMappingDecision> mappingDecisions,
        List<OptionFieldDecision> optionFieldDecisions,
        List<InputFieldDecision> inputFieldDecisions
) {
}
