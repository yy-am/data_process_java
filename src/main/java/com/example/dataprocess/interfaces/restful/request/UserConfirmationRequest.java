package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 用户确认结果提交请求。
 *
 * @param taskId 当前任务 ID
 * @param templateCode 当前模板编码
 * @param mappingConfirmations 字段映射确认结果
 * @param optionConfirmations 枚举字段确认结果
 * @param inputConfirmations 输入字段确认结果
 */
public record UserConfirmationRequest(
        @NotBlank String taskId,
        @NotBlank String templateCode,
        @NotNull List<MappingConfirmationDto> mappingConfirmations,
        @NotNull List<OptionConfirmationDto> optionConfirmations,
        @NotNull List<InputConfirmationDto> inputConfirmations
) {
}
