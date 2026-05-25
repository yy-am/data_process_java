package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 一轮用户确认所需的完整题包。
 *
 * @param taskId 当前任务 ID
 * @param presetTemplateCode 当前预置用户模板编码
 * @param standardTemplateCode 对应标准模板编码
 * @param mappingConfirmations 待确认的字段映射项
 * @param optionConfirmations 待选择的枚举字段项
 * @param inputConfirmations 待填写的输入字段项
 */
public record UserConfirmationItems(
        String taskId,
        String presetTemplateCode,
        String standardTemplateCode,
        List<MappingConfirmation> mappingConfirmations,
        List<OptionConfirmation> optionConfirmations,
        List<InputConfirmation> inputConfirmations
) {
}
