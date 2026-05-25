package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 用户提交并通过后端校验的一轮确认结果。
 *
 * @param taskId 当前任务 ID
 * @param templateCode 当前模板编码
 * @param mappingConfirmations 已确认的字段映射项
 * @param optionConfirmations 已确认的枚举字段项
 * @param inputConfirmations 已确认的输入字段项
 */
public record UserConfirmationResult(
        String taskId,
        String templateCode,
        List<MappingConfirmation> mappingConfirmations,
        List<OptionConfirmation> optionConfirmations,
        List<InputConfirmation> inputConfirmations
) {
}
