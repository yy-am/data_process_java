package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 一轮用户确认所需的完整题包。
 *
 * @param taskId 当前任务 ID
 * @param templateCode 当前模板编码
 * @param unclearMappings 待确认的字段映射问题
 * @param requiredOptionFields 待选择的枚举型字段问题
 * @param requiredInputFields 待填写的自由输入字段问题
 */
public record UserConfirmationItems(
        String taskId,
        String templateCode,
        List<UnclearMappingQuestion> unclearMappings,
        List<RequiredOptionQuestion> requiredOptionFields,
        List<RequiredInputQuestion> requiredInputFields
) {
}
