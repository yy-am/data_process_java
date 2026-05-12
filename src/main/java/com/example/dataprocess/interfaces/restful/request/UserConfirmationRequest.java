package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 用户确认结果提交请求。
 *
 * @param taskId 当前任务 ID
 * @param templateCode 当前模板编码
 * @param mappingDecisions 字段映射确认结果
 * @param optionFieldDecisions 枚举字段确认结果
 * @param inputFieldDecisions 输入字段确认结果
 */
public record UserConfirmationRequest(
        @NotBlank String taskId,
        @NotBlank String templateCode,
        @NotEmpty List<FieldMappingDecisionDto> mappingDecisions,
        @NotEmpty List<OptionFieldDecisionDto> optionFieldDecisions,
        @NotEmpty List<InputFieldDecisionDto> inputFieldDecisions
) {
}
